package org.ecos.nexus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

class NexusScreen(context: Context, private val store: LibraryStore) : LinearLayout(context) {
    private val content = LinearLayout(context)
    private val ink = 0xFFF2F5FA.toInt()
    private val muted = 0xFF9BAFC4.toInt()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF071321.toInt())
        setPadding(dp(20), dp(24), dp(20), dp(12))
        addView(TextView(context).apply { text = "N E X U S"; textSize = 12f; letterSpacing = 0.28f; setTextColor(0xFF83C9FF.toInt()) })
        addView(TextView(context).apply { text = "Your knowledge universe"; textSize = 27f; setTextColor(ink); setPadding(0, dp(12), 0, dp(4)) })
        addView(TextView(context).apply { text = "One library for everything you have created."; textSize = 13f; setTextColor(muted) })
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL; setPadding(0, dp(18), 0, dp(12)) }
        actions.addView(button("＋  IMPORT FILES") { (context as? MainActivity)?.importFiles() }, LayoutParams(0, dp(48), 1f))
        actions.addView(button("GRAPH") { showGraph() }, LayoutParams(dp(104), dp(48)).apply { marginStart = dp(8) })
        addView(actions)
        content.orientation = VERTICAL
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        showLibrary()
    }

    private fun button(label: String, action: () -> Unit) = TextView(context).apply {
        text = label; textSize = 11f; letterSpacing = 0.08f; gravity = Gravity.CENTER; setTextColor(ink)
        background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF14283D.toInt()); cornerRadius = dp(12).toFloat(); setStroke(dp(1), 0xFF294965.toInt()) }
        setOnClickListener { action() }
    }

    fun showLibrary() {
        content.removeAllViews()
        heading("LIBRARY  ·  " + store.all().size + " ITEMS")
        val items = store.all()
        if (items.isEmpty()) {
            content.addView(TextView(context).apply {
                text = "Your archive starts here. Import photos, videos, ZIP files, Markdown, source code, PDFs and other documents. Imported files stay on this device."
                textSize = 15f; setTextColor(muted); setPadding(0, dp(18), 0, dp(18))
            })
        } else items.forEach { asset ->
            content.addView(TextView(context).apply {
                text = asset.category.uppercase() + "    " + asset.name + "\n" + readableSize(asset.size) + "  ·  Stored locally"
                textSize = 14f; setTextColor(ink); setPadding(dp(14), dp(14), dp(14), dp(14))
                background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF102237.toInt()); cornerRadius = dp(12).toFloat() }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
    }

    private fun showGraph() {
        content.removeAllViews()
        heading("KNOWLEDGE GRAPH")
        content.addView(TextView(context).apply {
            text = "Canonical links and semantic suggestions are separate layers. This initial graph is a visual foundation; relationship editing and semantic analysis are planned milestones."
            textSize = 12f; setTextColor(muted); setPadding(0, 0, 0, dp(10))
        })
        content.addView(GraphCanvas(context, store.all().take(36).map { it.name }), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun heading(value: String) { content.addView(TextView(context).apply { text = value; textSize = 11f; letterSpacing = 0.12f; setTextColor(0xFF83C9FF.toInt()); setPadding(0, dp(14), 0, dp(10)) }) }
    private fun readableSize(n: Long) = when { n < 1024 -> "$n B"; n < 1024 * 1024 -> "%.1f KB".format(n / 1024f); else -> "%.1f MB".format(n / (1024f * 1024f)) }
}

private class GraphCanvas(context: Context, private val labels: List<String>) : View(context) {
    private val node = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF63B9F4.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF31536E.toInt(); strokeWidth = 2f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEAF4FF.toInt(); textSize = 25f }
    private var dx = 0f; private var dy = 0f; private var lastX = 0f; private var lastY = 0f
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); canvas.drawColor(0xFF091827.toInt())
        if (labels.isEmpty()) { canvas.drawText("Import files to begin", 28f, height / 2f, text); return }
        val count = labels.size; val cx = width / 2f; val cy = height / 2f
        val pts = (0 until count).map { i -> val a = Math.PI * 2.0 * i / count; Pair(cx + cos(a).toFloat() * 220f + dx, cy + sin(a).toFloat() * 220f + dy) }
        for (i in pts.indices) { val p = pts[i]; val q = pts[(i + 1) % pts.size]; canvas.drawLine(p.first, p.second, q.first, q.second, edge) }
        pts.forEachIndexed { i, p -> canvas.drawCircle(p.first, p.second, 11f, node); canvas.drawText(labels[i].take(16), p.first + 15f, p.second + 7f, text) }
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
