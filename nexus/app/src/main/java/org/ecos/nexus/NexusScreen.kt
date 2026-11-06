package org.ecos.nexus

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class NexusScreen(context: Context, private val store: LibraryStore, private val graph: KnowledgeGraphStore) : LinearLayout(context) {
    private val inspector = AssetInspector(context)
    private val content = LinearLayout(context)
    private val ink = 0xFFF2F5FA.toInt()
    private val muted = 0xFF9BAFC4.toInt()
    private val accent = 0xFF83C9FF.toInt()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF071321.toInt())
        setPadding(dp(18), dp(24), dp(18), dp(12))
        addView(TextView(context).apply { text = "N E X U S"; textSize = 12f; letterSpacing = .28f; setTextColor(accent) })
        addView(TextView(context).apply { text = "Your knowledge universe"; textSize = 25f; setTextColor(ink); setPadding(0, dp(9), 0, dp(3)) })
        addView(TextView(context).apply { text = "A private library for everything you create."; textSize = 13f; setTextColor(muted) })
        val nav = LinearLayout(context).apply { orientation = HORIZONTAL; setPadding(0, dp(16), 0, dp(12)) }
        nav.addView(button("LIBRARY") { showLibrary() }, LayoutParams(0, dp(42), 1f))
        nav.addView(button("GRAPH") { showGraph() }, LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
        nav.addView(button("ARTICLES") { showArticles() }, LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
        addView(nav)
        content.orientation = VERTICAL
        val scroll = ScrollView(context).apply { isFillViewport = true; addView(content) }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        showLibrary()
    }

    private fun button(label: String, action: () -> Unit) = TextView(context).apply {
        text = label; textSize = 10f; letterSpacing = .08f; gravity = Gravity.CENTER; setTextColor(ink)
        background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF14283D.toInt()); cornerRadius = dp(11).toFloat(); setStroke(dp(1), 0xFF294965.toInt()) }
        setOnClickListener { action() }
    }
    private fun heading(value: String) { content.addView(TextView(context).apply { text = value; textSize = 11f; letterSpacing = .12f; setTextColor(accent); setPadding(0, dp(14), 0, dp(10)) }) }
    private fun card(title: String, subtitle: String, action: () -> Unit) {
        content.addView(LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF102237.toInt()); cornerRadius = dp(12).toFloat() }
            addView(TextView(context).apply { text = title; textSize = 14f; setTextColor(ink) })
            addView(TextView(context).apply { text = subtitle; textSize = 11f; setTextColor(muted); setPadding(0, dp(5), 0, 0) })
            setOnClickListener { action() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }

    fun showLibrary() {
        content.removeAllViews()
        val items = store.all()
        heading("LIBRARY  ·  ${items.size} ITEMS")
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        actions.addView(button("+ IMPORT FILES") { (context as? MainActivity)?.importFiles() }, LayoutParams(0, dp(44), 1f))
        actions.addView(button("NEW ARTICLE") { createArticle() }, LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) })
        content.addView(actions)
        val search = EditText(context).apply {
            hint = "Search your archive"; setHintTextColor(muted); setTextColor(ink); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF102237.toInt()); cornerRadius = dp(10).toFloat() }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        content.addView(search, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12); bottomMargin = dp(8) })
        val list = LinearLayout(context).apply { orientation = VERTICAL }
        content.addView(list)
        fun render(query: String) {
            list.removeAllViews()
            val filtered = store.all().filter { it.name.contains(query, true) || it.category.contains(query, true) }
            filtered.forEach { asset ->
                list.addView(LinearLayout(context).apply {
                    orientation = VERTICAL
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF102237.toInt()); cornerRadius = dp(12).toFloat() }
                    addView(TextView(context).apply { text = asset.name; textSize = 14f; setTextColor(ink) })
                    addView(TextView(context).apply { text = "${asset.category.uppercase()}  ·  ${readableSize(asset.size)}"; textSize = 11f; setTextColor(muted); setPadding(0, dp(5), 0, 0) })
                    setOnClickListener { showAsset(asset) }
                }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
            }
            if (filtered.isEmpty()) list.addView(TextView(context).apply { text = "No matching items."; setTextColor(muted); setPadding(0, dp(16), 0, 0) })
        }
        search.addTextChangedListener(SimpleTextWatcher { render(it) })
        render("")
    }

    private fun showAsset(asset: LibraryAsset) {
        content.removeAllViews()
        heading(asset.category.uppercase())
        card(asset.name, "${readableSize(asset.size)} · stored on this device") {}
        when (asset.category) {
            "markdown", "source", "document", "dataset" -> {
                val body = runCatching { inspector.readText(asset) }.getOrElse { "Preview unavailable: ${it.message}" }
                val view = TextView(context).apply {
                    text = if (asset.category == "markdown") renderMarkdown(body) else body
                    textSize = 14f; setTextColor(ink); setTextIsSelectable(true); movementMethod = LinkMovementMethod.getInstance(); setPadding(dp(4), dp(12), dp(4), dp(24))
                    typeface = if (asset.category == "source" || asset.category == "dataset") android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
                }
                content.addView(view)
            }
            "archive" -> {
                val entries = runCatching { inspector.zipEntries(asset) }.getOrElse { emptyList() }
                heading("ARCHIVE CONTENTS · ${entries.size} SHOWN")
                if (entries.isEmpty()) card("Archive could not be read", "Unsupported, damaged, or empty ZIP file") {}
                entries.forEach { e -> card(e.name, if (e.directory) "Folder" else readableSize(e.size)) {} }
            }
            "image", "video", "package", "pdf", "office", "other" -> {
                card("Open with Android", "Uses an installed viewer. Nexus keeps the original file locally.") {
                    runCatching { inspector.openExternal(asset) }.onFailure { Toast.makeText(context, it.message ?: "No compatible viewer", Toast.LENGTH_LONG).show() }
                }
            }
            "unsupported-archive" -> card("Unsupported archive format", "ZIP is supported for read-only inspection; 7z and RAR are not yet supported.") {}
            else -> card("Open with Android", "External viewer") { runCatching { inspector.openExternal(asset) } }
        }
        card("BACK TO LIBRARY", "Return to your archive") { showLibrary() }
    }

    private fun renderMarkdown(source: String): CharSequence {
        val plain = android.text.SpannableStringBuilder()
        source.lines().forEach { line ->
            val clean = line.trim()
            when {
                clean.startsWith("# ") -> plain.append(clean.removePrefix("# ").uppercase()).append("\\n\\n")
                clean.startsWith("## ") -> plain.append(clean.removePrefix("## ")).append("\\n\\n")
                clean.startsWith("### ") -> plain.append(clean.removePrefix("### ")).append("\\n")
                clean.startsWith("- ") || clean.startsWith("* ") -> plain.append("• ").append(clean.drop(2)).append("\\n")
                clean.startsWith("> ") -> plain.append("  ").append(clean.drop(2)).append("\\n")
                clean.isBlank() -> plain.append("\\n")
                else -> plain.append(clean.replace(Regex("\\\\*\\\\*(.+?)\\\\*\\\\*"), "\\$1").replace(Regex("\\\\*(.+?)\\\\*"), "\\$1")).append("\\n")
            }
        }
        val links = SpannableStringBuilder(plain)
        val pattern = Regex("\\\\[\\\\[([^\\\\]]+)\\\\]\\\\]")
        pattern.findAll(links).toList().asReversed().forEach { match ->
            val title = match.groupValues[1].trim()
            links.replace(match.range.first, match.range.last + 1, title)
            val endIndex = match.range.first + title.length
            links.setSpan(object : ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    val article = graph.nodes().firstOrNull { it.kind == "article" && it.title.equals(title, true) }
                    if (article != null) showArticle(article)
                    else Toast.makeText(context, "Article not found: " + title, Toast.LENGTH_SHORT).show()
                }
                override fun updateDrawState(ds: TextPaint) { ds.color = accent; ds.isUnderlineText = true }
            }, match.range.first, endIndex, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return links
    }

    private fun showGraph() {
        content.removeAllViews()
        val assets = store.all()
        graph.refreshSuggestions(assets)
        val nodes = graph.nodes()
        val canonical = graph.edges("canonical")
        val semantic = graph.edges("semantic").filter { it.status == "suggested" }
        heading("KNOWLEDGE GRAPH")
        card("${nodes.size} NODES · ${canonical.size} CANONICAL LINKS", "Only persisted relationships are drawn. No decorative edges.") {}
        content.addView(GraphCanvas(context, nodes, canonical + semantic), LayoutParams(LayoutParams.MATCH_PARENT, dp(360)))
        heading("CANONICAL RELATIONSHIPS")
        canonical.forEach { edge ->
            val from = nodes.firstOrNull { it.id == edge.fromId }?.title ?: edge.fromId
            val to = nodes.firstOrNull { it.id == edge.toId }?.title ?: edge.toId
            card("$from  →  $to", edge.relation + " · " + edge.provenance) {}
        }
        heading("SEMANTIC SUGGESTIONS · NOT CANONICAL")
        semantic.forEach { edge ->
            val from = nodes.firstOrNull { it.id == edge.fromId }?.title ?: "?"
            val to = nodes.firstOrNull { it.id == edge.toId }?.title ?: "?"
            card("$from  ≈  $to", "${edge.relation} · ${"%.0f".format(edge.score * 100)}% title-term overlap") {
                AlertDialog.Builder(context).setTitle("Semantic suggestion")
                    .setMessage("Accept this suggestion as a reviewed semantic relation?")
                    .setPositiveButton("Accept") { _, _ -> graph.reviewSemantic(edge.id, true); showGraph() }
                    .setNegativeButton("Reject") { _, _ -> graph.reviewSemantic(edge.id, false); showGraph() }
                    .setNeutralButton("Cancel", null).show()
            }
        }
        card("CREATE CANONICAL LINK", "Choose two existing items and define a typed relationship") { createCanonicalLink(nodes) }
    }

    private fun createCanonicalLink(nodes: List<KnowledgeNode>) {
        if (nodes.size < 2) { Toast.makeText(context, "Import or create at least two items first.", Toast.LENGTH_SHORT).show(); return }
        var from = nodes.first()
        var to = nodes[1]
        val names = nodes.map { it.title }.toTypedArray()
        AlertDialog.Builder(context).setTitle("Choose source node").setItems(names) { _, which ->
            from = nodes[which]
            AlertDialog.Builder(context).setTitle("Choose target node").setItems(names) { _, target ->
                to = nodes[target]
                val relation = EditText(context).apply { hint = "Relation, e.g. references / belongs_to" }
                AlertDialog.Builder(context).setTitle("Relationship type").setView(relation)
                    .setPositiveButton("Save") { _, _ ->
                        runCatching { graph.addCanonical(from.id, to.id, relation.text.toString()) }
                            .onSuccess { showGraph() }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }.setNegativeButton("Cancel", null).show()
            }.show()
        }.show()
    }

    private fun showArticles() {
        content.removeAllViews()
        val articles = graph.nodes().filter { it.kind == "article" }
        heading("ENCYCLOPEDIA · ${articles.size} ARTICLES")
        card("+ CREATE ARTICLE", "Write a local knowledge page") { createArticle() }
        articles.forEach { article -> card(article.title, article.summary.ifBlank { "Personal knowledge article" }) { showArticle(article) } }
    }

    private fun createArticle() {
        val title = EditText(context).apply { hint = "Article title" }
        val body = EditText(context).apply { hint = "Article content (Markdown supported)"; minLines = 6; gravity = Gravity.TOP }
        val box = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(20), 0, dp(20), 0); addView(title); addView(body) }
        AlertDialog.Builder(context).setTitle("New article").setView(box)
            .setPositiveButton("Save") { _, _ ->
                runCatching { graph.createArticle(title.text.toString(), body.text.toString()) }
                    .onSuccess { showArticle(it) }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showArticle(article: KnowledgeNode) {
        content.removeAllViews()
        heading("NEXUS ARTICLE")
        content.addView(TextView(context).apply { text = article.title; textSize = 25f; setTextColor(ink); setPadding(0, dp(8), 0, dp(8)) })
        if (article.summary.isNotBlank()) content.addView(TextView(context).apply { text = article.summary; textSize = 14f; setTextColor(muted) })
        content.addView(TextView(context).apply { text = renderMarkdown(article.body); textSize = 15f; setTextColor(ink); setTextIsSelectable(true); movementMethod = LinkMovementMethod.getInstance(); setPadding(0, dp(16), 0, dp(20)) })
        val links = graph.edges().filter { it.fromId == article.id || it.toId == article.id }
        heading("RELATED KNOWLEDGE · ${links.size}")
        links.forEach { edge ->
            val other = graph.nodes().firstOrNull { it.id == if (edge.fromId == article.id) edge.toId else edge.fromId }
            if (other != null) card(other.title, "${edge.layer} · ${edge.relation}") { if (other.kind == "article") showArticle(other) else store.all().firstOrNull { it.id == other.assetId }?.let(::showAsset) }
        }
        card("BACK TO ARTICLES", "Return to encyclopedia") { showArticles() }
    }

    private fun readableSize(n: Long) = when { n < 1024 -> "$n B"; n < 1024 * 1024 -> "%.1f KB".format(n / 1024f); else -> "%.1f MB".format(n / (1024f * 1024f)) }
}

private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
    override fun afterTextChanged(s: android.text.Editable?) {}
}

private class GraphCanvas(context: Context, private val nodes: List<KnowledgeNode>, private val edges: List<KnowledgeEdge>) : View(context) {
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF63B9F4.toInt() }
    private val canonicalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF62D6B0.toInt(); strokeWidth = 3f }
    private val semanticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB49BFF.toInt(); strokeWidth = 2f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEAF4FF.toInt(); textSize = 23f }
    private var dx = 0f; private var dy = 0f; private var lastX = 0f; private var lastY = 0f; private var scale = 1f
    private val positions = mutableMapOf<String, Pair<Float, Float>>()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); canvas.drawColor(0xFF091827.toInt())
        if (nodes.isEmpty()) { canvas.drawText("Import files or create an article", 24f, height / 2f, labelPaint); return }
        val cx = width / 2f + dx; val cy = height / 2f + dy
        positions.clear()
        nodes.take(80).forEachIndexed { i, n ->
            val angle = 2.0 * Math.PI * i / min(nodes.size, 80)
            positions[n.id] = Pair(cx + cos(angle).toFloat() * min(width, height) * .32f, cy + sin(angle).toFloat() * min(width, height) * .32f)
        }
        canvas.save(); canvas.scale(scale, scale, cx, cy)
        edges.forEach { edge ->
            val a = positions[edge.fromId]; val b = positions[edge.toId]
            if (a != null && b != null) canvas.drawLine(a.first, a.second, b.first, b.second, if (edge.layer == "canonical") canonicalPaint else semanticPaint)
        }
        nodes.take(80).forEach { n ->
            val p = positions[n.id] ?: return@forEach
            canvas.drawCircle(p.first, p.second, 9f, nodePaint)
            canvas.drawText(n.title.take(18), p.first + 12f, p.second + 6f, labelPaint)
        }
        canvas.restore()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; return true }
            MotionEvent.ACTION_MOVE -> { dx += event.x - lastX; dy += event.y - lastY; lastX = event.x; lastY = event.y; invalidate(); return true }
            MotionEvent.ACTION_UP -> return true
        }
        return true
    }
}
