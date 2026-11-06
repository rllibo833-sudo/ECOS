package org.ecos.nexus

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.zip.ZipFile

data class ArchiveEntryInfo(val name: String, val size: Long, val compressedSize: Long, val directory: Boolean)

class AssetInspector(private val context: Context) {
    fun file(asset: LibraryAsset): File {
        val root = File(context.filesDir, "library").canonicalFile
        val candidate = File(root, asset.relativePath).canonicalFile
        require(candidate.path.startsWith(root.path + File.separator)) { "Invalid library path" }
        return candidate
    }
    fun zipEntries(asset: LibraryAsset, limit: Int = 500): List<ArchiveEntryInfo> {
        val source = file(asset)
        require(source.isFile && source.length() > 0L) { "Archive is missing or empty" }
        return ZipFile(source).use { zip ->
            val result = mutableListOf<ArchiveEntryInfo>()
            val entries = zip.entries()
            while (entries.hasMoreElements() && result.size < limit) {
                val entry = entries.nextElement()
                result += ArchiveEntryInfo(entry.name, entry.size, entry.compressedSize, entry.isDirectory)
            }
            result
        }
    }
    fun decodeImagePreview(asset: LibraryAsset, maxDimension: Int = 1600): Bitmap {
        val source = file(asset)
        require(source.isFile) { "File is missing" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image" }
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(source.absolutePath, options) ?: error("Could not decode image")
    }

    fun readTextPrefix(asset: LibraryAsset, maxBytes: Int = 64 * 1024): String {
        require(maxBytes > 0)
        val source = file(asset)
        require(source.isFile) { "File is missing" }
        val output = java.io.ByteArrayOutputStream(maxBytes)
        source.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var remaining = maxBytes
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    fun readText(asset: LibraryAsset, maxBytes: Int = 512 * 1024): String {
        val source = file(asset)
        require(source.isFile) { "File is missing" }
        require(source.length() <= maxBytes) { "File is larger than the preview limit" }
        return source.readText(Charsets.UTF_8)
    }
    fun share(asset: LibraryAsset) {
        val source = file(asset)
        require(source.isFile) { "File is missing" }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", source)
        val intent = Intent(Intent.ACTION_SEND).setType(asset.mime.ifBlank { "*/*" })
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share " + asset.name))
    }

    fun openExternal(asset: LibraryAsset) {
        val source = file(asset)
        require(source.isFile) { "File is missing" }
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", source)
        val mime = asset.mime.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Open " + asset.name))
    }
}
