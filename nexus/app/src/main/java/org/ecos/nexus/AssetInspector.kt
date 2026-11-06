package org.ecos.nexus

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
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
    fun readText(asset: LibraryAsset, maxBytes: Int = 512 * 1024): String {
        val source = file(asset)
        require(source.isFile) { "File is missing" }
        require(source.length() <= maxBytes) { "File is larger than the preview limit" }
        return source.readText(Charsets.UTF_8)
    }
    fun share(asset: LibraryAsset) {\n        val source = file(asset)\n        require(source.isFile) { "File is missing" }\n        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", source)\n        val intent = Intent(Intent.ACTION_SEND).setType(asset.mime.ifBlank { "*/*" })\n            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)\n        context.startActivity(Intent.createChooser(intent, "Share " + asset.name))\n    }\n\n    fun openExternal(asset: LibraryAsset) {
        val source = file(asset)
        require(source.isFile) { "File is missing" }
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", source)
        val mime = asset.mime.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Open " + asset.name))
    }
}
