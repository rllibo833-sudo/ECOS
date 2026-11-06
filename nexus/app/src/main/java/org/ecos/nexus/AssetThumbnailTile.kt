package org.ecos.nexus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A library row with a small, asynchronously decoded preview. Originals are never modified. */
class AssetThumbnailTile(
    context: Context,
    asset: LibraryAsset,
    subtitle: String,
    action: () -> Unit
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val thumbnail = ImageView(context)

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        val size = (64 * density).toInt()
        thumbnail.layoutParams = LayoutParams((84 * density).toInt(), size).apply {
            marginEnd = (10 * density).toInt()
        }
        thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        thumbnail.setBackgroundColor(0xFF18334D.toInt())
        thumbnail.contentDescription = "Preview of ${asset.name}"
        addView(thumbnail)
        addView(
            NexusCanvasTile(context, asset.name, subtitle, action = action),
            LayoutParams(0, (70 * density).toInt(), 1f)
        )
        isClickable = true
        setOnClickListener { action() }
        thumbnail.tag = asset.id
        if (asset.category == "image" || asset.category == "video") {
            val cached = previews.get(asset.id)
            if (cached != null) {
                thumbnail.setImageBitmap(cached)
            } else {
                val file = File(File(context.filesDir, "library"), asset.relativePath)
                decoder.execute {
                    val bitmap = decode(file, asset.category)
                    if (bitmap != null) {
                        previews.put(asset.id, bitmap)
                        post {
                            if (thumbnail.tag == asset.id && isAttachedToWindow) {
                                thumbnail.setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            }
        } else {
            thumbnail.setImageDrawable(null)
        }
    }

    private fun decode(file: File, category: String): Bitmap? = runCatching {
        if (!file.isFile) return null
        if (category == "image") {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 168 || bounds.outHeight / sample > 128) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                    val scale = minOf(168f / frame.width, 128f / frame.height, 1f)
                    if (scale >= 1f) frame else Bitmap.createScaledBitmap(
                        frame, (frame.width * scale).toInt().coerceAtLeast(1),
                        (frame.height * scale).toInt().coerceAtLeast(1), true
                    ).also { if (it !== frame) frame.recycle() }
                }
            } finally {
                retriever.release()
            }
        }
    }.getOrNull()

    companion object {
        private val decoder = ThreadPoolExecutor(
            1, 2, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(24),
            ThreadPoolExecutor.DiscardOldestPolicy()
        )
        private val previews = object : LruCache<String, Bitmap>(2 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }
}
