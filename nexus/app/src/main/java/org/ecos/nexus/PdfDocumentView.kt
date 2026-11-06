package org.ecos.nexus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.io.File
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.min

/** Offline PDF surface backed by Android's native PdfRenderer. Only visible pages are rasterized. */
class PdfDocumentView(context: Context, file: File) : View(context) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val density = resources.displayMetrics.density
    private val pageGap = (18f * density).toInt()
    private val sideInset = (12f * density).toInt()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 27, 42) }
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = mutableListOf<Rect>()
    private val cache = object : LinkedHashMap<Int, Bitmap>(3, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean {
            val remove = size > 2
            if (remove) eldest?.value?.recycle()
            return remove
        }
    }
    private var pageWidth = 0
    private var contentWidth = 0
    private var closed = false
    private var zoom = 1f
    private var zoomFocusX = 0f
    private var zoomFocusY = 0f
    private val zoomDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            zoomFocusX = detector.focusX
            zoomFocusY = detector.focusY
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 3f)
            zoomFocusX = detector.focusX
            zoomFocusY = detector.focusY
            invalidate()
            return true
        }
    })

    val pageCount: Int get() = renderer.pageCount
    fun pageTop(index: Int): Int = bounds.getOrNull(index.coerceIn(0, bounds.lastIndex))?.top ?: 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width != contentWidth) calculateLayout(width)
        val desiredHeight = if (bounds.isEmpty()) 0 else bounds.last().bottom + pageGap
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    private fun calculateLayout(width: Int) {
        contentWidth = width
        pageWidth = max(1, width - sideInset * 2)
        bounds.clear()
        var top = pageGap
        for (index in 0 until renderer.pageCount) {
            val page = renderer.openPage(index)
            val ratio = page.height.toFloat() / max(1, page.width)
            val height = max(1, (pageWidth * ratio).toInt())
            page.close()
            bounds += Rect(sideInset, top, sideInset + pageWidth, top + height)
            top += height + pageGap
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundPaint.color)
        if (closed || bounds.isEmpty()) return
        val clip = canvas.clipBounds
        val visibleTop = clip.top
        val visibleBottom = clip.bottom
        canvas.save()
        canvas.scale(zoom, zoom, zoomFocusX.takeIf { it > 0f } ?: width / 2f, zoomFocusY.takeIf { it > 0f } ?: visibleTop.toFloat())
        bounds.forEachIndexed { index, rect ->
            if (rect.bottom < visibleTop || rect.top > visibleBottom) return@forEachIndexed
            canvas.drawRect(rect, pagePaint)
            val bitmap = cache[index] ?: renderPage(index, (rect.width() * zoom).toInt(), (rect.height() * zoom).toInt())?.also { cache[index] = it }
            if (bitmap != null) canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        zoomDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return zoomDetector.isInProgress || event.pointerCount > 1 || super.onTouchEvent(event)
    }

    private fun renderPage(index: Int, width: Int, height: Int): Bitmap? {
        if (closed) return null
        return runCatching {
            val page = renderer.openPage(index)
            try {
                val scale = min(1f, 2400f / max(width, height))
                val bitmap = Bitmap.createBitmap(max(1, (width * scale).toInt()), max(1, (height * scale).toInt()), Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }.getOrNull()
    }

    fun close() {
        if (closed) return
        closed = true
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
        renderer.close()
        descriptor.close()
    }
}
