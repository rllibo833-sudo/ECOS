package org.ecos.nexus

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
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
import android.widget.ImageView
import android.widget.VideoView
import android.widget.MediaController
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class NexusScreen(context: Context, private val store: LibraryStore, private val graph: KnowledgeGraphStore) : LinearLayout(context) {
    private val inspector = AssetInspector(context)
    private var activePdf: PdfDocumentView? = null
    private var activeVideo: VideoView? = null
    private var activeImage: android.graphics.Bitmap? = null
    private val content = LinearLayout(context)
    private lateinit var mainScroll: ScrollView
    private val ink = 0xFFF2F5FA.toInt()
    private val muted = 0xFF9BAFC4.toInt()
    private val accent = 0xFF83C9FF.toInt()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF071321.toInt())
        setPadding(dp(18), dp(24), dp(18), dp(12))
        addView(NexusBrandHeader(context), LayoutParams(LayoutParams.MATCH_PARENT, dp(92)))
        val nav = LinearLayout(context).apply { orientation = HORIZONTAL; setPadding(0, dp(16), 0, dp(12)) }
        nav.addView(button("LIBRARY") { showLibrary() }, LayoutParams(0, dp(42), 1f))
        nav.addView(button("GRAPH") { showGraph() }, LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
        nav.addView(button("ARTICLES") { showArticles() }, LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
        addView(nav)
        content.orientation = VERTICAL
        mainScroll = ScrollView(context).apply { isFillViewport = true; addView(content) }
        addView(mainScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        showLibrary()
    }

    private fun button(label: String, action: () -> Unit): View = NexusCanvasTile(context, label, compact = true, action = action)
    private fun heading(value: String) { content.addView(TextView(context).apply { text = value; textSize = 11f; letterSpacing = .12f; setTextColor(accent); setPadding(0, dp(14), 0, dp(10)) }) }
    private fun card(title: String, subtitle: String, action: () -> Unit) {
        content.addView(NexusCanvasTile(context, title, subtitle, action = action),
            LayoutParams(LayoutParams.MATCH_PARENT, dp(70)).apply { bottomMargin = dp(8) })
    }

    fun showLibrary() {
        releaseMediaViews()
        content.removeAllViews()
        val items = store.all()
        heading("LIBRARY  ·  ${items.size} ITEMS")
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        actions.addView(button("+ IMPORT FILES") { (context as? MainActivity)?.importFiles() }, LayoutParams(0, dp(44), 1f))
        actions.addView(button("NEW ARTICLE") { createArticle() }, LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) })
        content.addView(actions)
        card("GRANT MEDIA ACCESS", "Optional: allow Nexus to browse device photos, videos, and audio. Document import uses the system picker.") {
            (context as? MainActivity)?.requestMediaPermissions()
        }
        card("CREATE OFFLINE BACKUP", "Export originals, catalog, and knowledge graph to a ZIP file") {
            (context as? MainActivity)?.backupArchive()
        }
        card("RESTORE BACKUP", "Replace the current library from a Nexus backup ZIP") {
            (context as? MainActivity)?.restoreArchive()
        }
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
                list.addView(AssetThumbnailTile(context, asset, "${asset.category.uppercase()}  ·  ${readableSize(asset.size)}") { showAsset(asset) },
                    LayoutParams(LayoutParams.MATCH_PARENT, dp(70)).apply { bottomMargin = dp(8) })
            }
            if (filtered.isEmpty()) list.addView(TextView(context).apply { text = "No matching items."; setTextColor(muted); setPadding(0, dp(16), 0, 0) })
        }
        search.addTextChangedListener(SimpleTextWatcher { render(it) })
        render("")
    }

    private fun showAsset(asset: LibraryAsset) {
        releaseMediaViews()
        content.removeAllViews()
        heading(asset.category.uppercase())
        card(asset.name, "${readableSize(asset.size)} · stored on this device") {}
        card("FILE DETAILS", "${asset.mime} · ${asset.relativePath}") {
            AlertDialog.Builder(context).setTitle(asset.name)
                .setMessage("Type: ${asset.mime}\nCategory: ${asset.category}\nSize: ${readableSize(asset.size)}\nImported: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(asset.importedAt))}\nStored privately inside Nexus.")
                .setPositiveButton("Close", null).show()
        }
        card("SHARE ORIGINAL", "Send a read-only copy to another app") {
            runCatching { inspector.share(asset) }
                .onFailure { Toast.makeText(context, it.message ?: "Share failed", Toast.LENGTH_LONG).show() }
        }
        card("DELETE FROM NEXUS", "Permanently remove this imported copy and its graph node") {
            AlertDialog.Builder(context).setTitle("Delete imported file?")
                .setMessage("This removes the private copy from Nexus. The source in its original location is not changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    runCatching {
                        if (!store.delete(asset)) error("Could not delete file")
                        graph.removeAssetNode(asset.id)
                    }.onSuccess { showLibrary() }
                        .onFailure { Toast.makeText(context, it.message ?: "Delete failed", Toast.LENGTH_LONG).show() }
                }.show()
        }
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
            "pdf" -> {
                runCatching {
                    val reader = PdfDocumentView(context, inspector.file(asset))
                    activePdf = reader
                    heading("PDF READER · ${reader.pageCount} PAGES")
                    val scroll = ScrollView(context).apply {
                        isFillViewport = false
                        setBackgroundColor(0xFF0D1B2A.toInt())
                        addView(reader, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                    }
                    var pageIndex = 0
                    val pageLabel = TextView(context).apply {
                        text = "PAGE 1 / ${reader.pageCount}"
                        setTextColor(ink)
                        gravity = Gravity.CENTER
                        textSize = 12f
                    }
                    val controls = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(button("PREVIOUS") {
                            pageIndex = (pageIndex - 1).coerceAtLeast(0)
                            pageLabel.text = "PAGE ${pageIndex + 1} / ${reader.pageCount}"
                            scroll.smoothScrollTo(0, reader.pageTop(pageIndex))
                        }, LayoutParams(0, dp(42), 1f))
                        addView(pageLabel, LayoutParams(0, dp(42), 1f))
                        addView(button("NEXT") {
                            pageIndex = (pageIndex + 1).coerceAtMost(reader.pageCount - 1)
                            pageLabel.text = "PAGE ${pageIndex + 1} / ${reader.pageCount}"
                            scroll.smoothScrollTo(0, reader.pageTop(pageIndex))
                        }, LayoutParams(0, dp(42), 1f))
                    }
                    content.addView(controls)
                    content.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(560)))
                }.onFailure {
                    card("PDF could not be opened", it.message ?: "The document may be damaged or encrypted.") {}
                }
            }
            "image" -> {
                runCatching {
                    val bitmap = inspector.decodeImagePreview(asset)
                    activeImage = bitmap
                    content.addView(ImageView(context).apply {
                        setImageBitmap(bitmap)
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = asset.name
                    }, LayoutParams(LayoutParams.MATCH_PARENT, dp(420)))
                }.onFailure { card("Image preview unavailable", it.message ?: "Unsupported image") {} }
            }
            "video" -> {
                runCatching {
                    val player = VideoView(context)
                    activeVideo = player
                    player.setMediaController(MediaController(context))
                    player.setVideoPath(inspector.file(asset).absolutePath)
                    content.addView(player, LayoutParams(LayoutParams.MATCH_PARENT, dp(280)))
                    player.setOnPreparedListener { media -> media.isLooping = false; player.start() }
                }.onFailure { card("Video preview unavailable", it.message ?: "Unsupported video") {} }
            }
            "audio", "package", "office", "other" -> {
                card("Open with Android", "Uses an installed viewer or player. Nexus keeps the original file locally.") {
                    runCatching { inspector.openExternal(asset) }.onFailure { Toast.makeText(context, it.message ?: "No compatible viewer", Toast.LENGTH_LONG).show() }
                }
            }
            "unsupported-archive" -> card("Unsupported archive format", "ZIP is supported for read-only inspection; 7z and RAR are not yet supported.") {}
            else -> card("Open with Android", "External viewer") { runCatching { inspector.openExternal(asset) } }
        }
        card("BACK TO LIBRARY", "Return to your archive") { showLibrary() }
    }

    private fun releaseMediaViews() {
        activePdf?.close()
        activePdf = null
        activeVideo?.stopPlayback()
        activeVideo = null
        activeImage?.recycle()
        activeImage = null
    }

    private fun renderMarkdown(source: String): CharSequence {
        val output = SpannableStringBuilder()
        var inCode = false
        source.lines().forEach { raw ->
            val line = raw.trimEnd()
            if (line.trim().startsWith("```")) {
                inCode = !inCode
                if (!inCode) output.append("\n")
                return@forEach
            }
            if (inCode) {
                val begin = output.length
                output.append(line).append("\n")
                output.setSpan(android.text.style.TypefaceSpan("monospace"), begin, output.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                output.setSpan(android.text.style.BackgroundColorSpan(0xFF102237.toInt()), begin, output.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                continue
            }
            val clean = line.trim()
            if (clean.startsWith("|") && clean.endsWith("|")) {
                if (clean.contains("---")) return@forEach
                val cells = clean.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                val begin = output.length
                output.append(cells.joinToString("   │   ")).append("\n")
                output.setSpan(android.text.style.TypefaceSpan("monospace"), begin, output.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                output.setSpan(android.text.style.BackgroundColorSpan(0xFF102237.toInt()), begin, output.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return@forEach
            }
            val begin = output.length
            when {
                clean.startsWith("# ") -> output.append(clean.drop(2).uppercase()).append("\n\n")
                clean.startsWith("## ") -> output.append(clean.drop(3)).append("\n\n")
                clean.startsWith("### ") -> output.append(clean.drop(4)).append("\n")
                clean.startsWith("- ") || clean.startsWith("* ") -> output.append("• ").append(clean.drop(2)).append("\n")
                clean.startsWith("> ") -> output.append("▏ ").append(clean.drop(2)).append("\n")
                clean.isBlank() -> output.append("\n")
                else -> output.append(clean).append("\n")
            }
            if (output.length > begin) {
                if (clean.startsWith("# ")) output.setSpan(android.text.style.RelativeSizeSpan(1.35f), begin, output.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else if (clean.startsWith("## ")) output.setSpan(android.text.style.RelativeSizeSpan(1.2f), begin, output.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else if (clean.startsWith("### ")) output.setSpan(android.text.style.RelativeSizeSpan(1.08f), begin, output.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val inlinePatterns = listOf(
            Regex("\\*\\*(.+?)\\*\\*") to android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)") to android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
            Regex("~~(.+?)~~") to android.text.style.StrikethroughSpan(),
            Regex("`([^`]+)`") to android.text.style.TypefaceSpan("monospace")
        )
        inlinePatterns.forEach { (pattern, span) ->
            pattern.findAll(output).toList().asReversed().forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                output.delete(end - match.groupValues[1].length - match.value.length + match.groupValues[1].length, end)
                val contentStart = start
                val contentEnd = start + match.groupValues[1].length
                output.replace(start, end - (match.value.length - match.groupValues[1].length), match.groupValues[1])
                output.setSpan(span, contentStart, contentEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val links = Regex("\\[\\[([^\\]]+)\\]\\]")
        links.findAll(output).toList().asReversed().forEach { match ->
            val title = match.groupValues[1].trim()
            val start = match.range.first
            output.replace(start, match.range.last + 1, title)
            output.setSpan(object : ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    val target = graph.nodes().firstOrNull { it.title.equals(title, true) }
                    when {
                        target == null -> Toast.makeText(context, "Knowledge item not found: " + title, Toast.LENGTH_SHORT).show()
                        target.kind == "article" -> showArticle(target)
                        else -> store.all().firstOrNull { it.id == target.assetId }?.let(::showAsset)
                    }
                }
                override fun updateDrawState(ds: TextPaint) { ds.color = accent; ds.isUnderlineText = true }
            }, start, start + title.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        android.text.util.Linkify.addLinks(output, android.text.util.Linkify.WEB_URLS)
        return output
    }

    private fun showGraph() {
        releaseMediaViews()
        content.removeAllViews()
        val assets = store.all()
        graph.refreshSuggestions(assets)
        val nodes = graph.nodes()
        val canonical = graph.edges("canonical")
        val semantic = graph.edges("semantic").filter { it.status != "rejected" }
        heading("KNOWLEDGE GRAPH")
        card("${nodes.size} NODES · ${canonical.size} CANONICAL LINKS", "Only persisted relationships are drawn. No decorative edges.") {}
        content.addView(GraphCanvas(context, nodes, canonical + semantic) { node ->
            if (node.kind == "article") showArticle(node)
            else store.all().firstOrNull { it.id == node.assetId }?.let(::showAsset)
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(360)))
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
            card("$from  ≈  $to", "${edge.status} · ${edge.relation} · ${"%.0f".format(edge.score * 100)}% content-term overlap") {
                if (edge.status == "suggested") {
                    AlertDialog.Builder(context).setTitle("Semantic suggestion")
                        .setMessage("Accept this suggestion as a reviewed semantic relation?")
                        .setPositiveButton("Accept") { _, _ -> graph.reviewSemantic(edge.id, true); showGraph() }
                        .setNegativeButton("Reject") { _, _ -> graph.reviewSemantic(edge.id, false); showGraph() }
                        .setNeutralButton("Cancel", null).show()
                }
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
        releaseMediaViews()
        content.removeAllViews()
        val articles = graph.nodes().filter { it.kind == "article" }
        heading("ENCYCLOPEDIA · ${articles.size} ARTICLES")
        card("+ CREATE ARTICLE", "Write a local knowledge page") { createArticle() }
        articles.forEach { article -> card(article.title, article.summary.ifBlank { "Personal knowledge article" }) { showArticle(article) } }
    }

    private fun createArticle() = editArticle(null)

    private fun editArticle(article: KnowledgeNode?) {
        val title = EditText(context).apply { hint = "Article title"; setText(article?.title.orEmpty()) }
        val summary = EditText(context).apply { hint = "Short summary / infobox"; setText(article?.summary.orEmpty()) }
        val body = EditText(context).apply {
            hint = "Article content (Markdown supported)"
            minLines = 8
            gravity = Gravity.TOP
            setText(article?.body.orEmpty())
        }
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            addView(title)
            addView(summary)
            addView(body)
        }
        AlertDialog.Builder(context).setTitle(if (article == null) "New article" else "Edit article").setView(box)
            .setPositiveButton("Save") { _, _ ->
                runCatching {
                    if (article == null) graph.createArticle(title.text.toString(), body.text.toString(), summary.text.toString())
                    else graph.updateArticle(article.id, title.text.toString(), body.text.toString(), summary.text.toString())
                }
                    .onSuccess { showArticle(it) }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showArticle(article: KnowledgeNode) {
        releaseMediaViews()
        content.removeAllViews()
        heading("NEXUS ARTICLE")
        card("EDIT ARTICLE", "Update title, summary, and Markdown content") { editArticle(article) }
        content.addView(TextView(context).apply { text = article.title; textSize = 25f; setTextColor(ink); setPadding(0, dp(8), 0, dp(8)) })
        if (article.summary.isNotBlank()) {
            card("ARTICLE SUMMARY", article.summary) {}
        }
        val toc = article.body.lines().mapNotNull { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("## ") -> trimmed.removePrefix("## ").trim()
                trimmed.startsWith("### ") -> "— " + trimmed.removePrefix("### ").trim()
                else -> null
            }
        }
        val renderedBody = renderMarkdown(article.body)
        val bodyView = TextView(context).apply {
            text = renderedBody
            textSize = 15f
            setTextColor(ink)
            setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(0, dp(16), 0, dp(20))
        }
        if (toc.isNotEmpty()) {
            heading("TABLE OF CONTENTS")
            toc.forEach { label ->
                val sectionTitle = label.removePrefix("— ").trim()
                card(label, "Jump to section") {
                    val offset = bodyView.text.toString().indexOf(sectionTitle)
                    if (offset >= 0) bodyView.post {
                        val layout = bodyView.layout
                        if (layout != null) {
                            val line = layout.getLineForOffset(offset)
                            mainScroll.smoothScrollTo(0, bodyView.top + layout.getLineTop(line))
                        }
                    }
                }
            }
        }
        content.addView(bodyView)
        val links = graph.edges().filter { it.status != "rejected" && (it.fromId == article.id || it.toId == article.id) }
        heading("RELATED KNOWLEDGE · ${links.size}")
        links.forEach { edge ->
            val other = graph.nodes().firstOrNull { it.id == if (edge.fromId == article.id) edge.toId else edge.fromId }
            if (other != null) card(other.title, "${edge.layer} · ${edge.relation}") { if (other.kind == "article") showArticle(other) else store.all().firstOrNull { it.id == other.assetId }?.let(::showAsset) }
        }
        card("BACK TO ARTICLES", "Return to encyclopedia") { showArticles() }
    }

    override fun onDetachedFromWindow() {
        releaseMediaViews()
        super.onDetachedFromWindow()
    }

    private fun readableSize(n: Long) = when { n < 1024 -> "$n B"; n < 1024 * 1024 -> "%.1f KB".format(n / 1024f); else -> "%.1f MB".format(n / (1024f * 1024f)) }
}

private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
    override fun afterTextChanged(s: android.text.Editable?) {}
}

private class GraphCanvas(context: Context, private val nodes: List<KnowledgeNode>, private val edges: List<KnowledgeEdge>, private val onNodeTap: (KnowledgeNode) -> Unit) : View(context) {
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF63B9F4.toInt() }
    private val canonicalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF62D6B0.toInt(); strokeWidth = 3f }
    private val semanticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB49BFF.toInt(); strokeWidth = 2f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEAF4FF.toInt(); textSize = 23f }
    private var dx = 0f; private var dy = 0f; private var lastX = 0f; private var lastY = 0f; private var downX = 0f; private var downY = 0f; private var scale = 1f
    private val positions = mutableMapOf<String, Pair<Float, Float>>()
    private val scaleDetector = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
            scale = (scale * detector.scaleFactor).coerceIn(0.55f, 2.5f)
            invalidate()
            return true
        }
    })
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
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; downX = event.x; downY = event.y; scaleDetector.onTouchEvent(event); return true }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> { scaleDetector.onTouchEvent(event); return true }
            MotionEvent.ACTION_MOVE -> { scaleDetector.onTouchEvent(event); if (!scaleDetector.isInProgress && event.pointerCount == 1) { dx += event.x - lastX; dy += event.y - lastY }; lastX = event.x; lastY = event.y; invalidate(); return true }
            MotionEvent.ACTION_UP -> {
                scaleDetector.onTouchEvent(event)
                if (kotlin.math.hypot(event.x - downX, event.y - downY) < 14f * resources.displayMetrics.density) {
                    val hit = positions.entries.minByOrNull { (_, point) -> kotlin.math.hypot(event.x - (point.first * scale + (width / 2f + dx) * (1f - scale)), event.y - (point.second * scale + (height / 2f + dy) * (1f - scale))) }
                    val candidate = hit?.let { pair -> nodes.firstOrNull { it.id == pair.key } }
                    val point = hit?.value
                    if (candidate != null && point != null && kotlin.math.hypot(event.x - (point.first * scale + (width / 2f + dx) * (1f - scale)), event.y - (point.second * scale + (height / 2f + dy) * (1f - scale))) < 28f * resources.displayMetrics.density) onNodeTap(candidate)
                }
                return true
            }
        }
        return true
    }
}
