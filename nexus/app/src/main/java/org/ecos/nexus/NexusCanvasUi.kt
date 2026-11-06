package org.ecos.nexus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Small, theme-owned controls rendered directly on Canvas rather than platform widgets. */
class NexusCanvasTile(
    context: Context,
    private val title: String,
    private val subtitle: String = "",
    private val compact: Boolean = false,
    private val action: () -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (compact) 0xFF18334D.toInt() else 0xFF102237.toInt() }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF294965.toInt(); style = Paint.Style.STROKE; strokeWidth = density }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F5FA.toInt(); textSize = (if (compact) 10f else 14f) * density; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9BAFC4.toInt(); textSize = 11f * density }
    private val rect = RectF()
    private var pressed = false

    init {
        isClickable = true
        setOnClickListener { action() }
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { pressed = true; invalidate() }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate() }
            }
            false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = ((if (compact) 42 else 70) * density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 1f * density
        rect.set(inset, inset, width - inset, height - inset)
        if (pressed) fill.color = 0xFF214563.toInt() else fill.color = if (compact) 0xFF18334D.toInt() else 0xFF102237.toInt()
        canvas.drawRoundRect(rect, 13f * density, 13f * density, fill)
        canvas.drawRoundRect(rect, 13f * density, 13f * density, outline)
        val x = 14f * density
        val baseline = if (compact || subtitle.isBlank()) height / 2f - (titlePaint.ascent() + titlePaint.descent()) / 2f else height * .43f
        canvas.drawText(title.take(58), x, baseline, titlePaint)
        if (!compact && subtitle.isNotBlank()) {
            canvas.drawText(subtitle.take(86), x, height * .73f, subtitlePaint)
            val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF83C9FF.toInt(); strokeWidth = 2f * density }
            canvas.drawLine(width - 18f * density, height / 2f - 5f * density, width - 12f * density, height / 2f, marker)
            canvas.drawLine(width - 12f * density, height / 2f, width - 18f * density, height / 2f + 5f * density, marker)
        }
    }
}

class NexusBrandHeader(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF83C9FF.toInt(); textSize = 12f * density; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
    private val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F5FA.toInt(); textSize = 25f * density; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL) }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9BAFC4.toInt(); textSize = 13f * density }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((92 * density).toInt(), heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawText("N E X U S", 0f, 14f * density, brand)
        canvas.drawText("Your knowledge universe", 0f, 48f * density, headline)
        canvas.drawText("A private library for everything you create.", 0f, 72f * density, caption)
    }
}
