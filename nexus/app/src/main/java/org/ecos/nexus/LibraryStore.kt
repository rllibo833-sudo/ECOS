package org.ecos.nexus

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class LibraryAsset(
    val id: String,
    val name: String,
    val category: String,
    val mime: String,
    val relativePath: String,
    val size: Long,
    val importedAt: Long,
    val fingerprint: String = ""
)

class LibraryStore(private val context: Context) {
    private val root = File(context.filesDir, "library").apply { mkdirs() }
    private val catalog = File(context.filesDir, "nexus-catalog.json")

    @Synchronized fun all(): List<LibraryAsset> = runCatching {
        val array = JSONArray(if (catalog.exists()) catalog.readText() else "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            LibraryAsset(
                o.getString("id"), o.getString("name"), o.getString("category"),
                o.optString("mime"), o.getString("relativePath"), o.optLong("size"),
                o.optLong("importedAt"), o.optString("fingerprint")
            )
        }.sortedByDescending { it.importedAt }
    }.getOrDefault(emptyList())

    /** Copies the original into app-private storage, verifies it, then atomically updates the catalog. */
    @Synchronized fun importUri(uri: Uri): Boolean {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "import-" + System.currentTimeMillis()
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val id = UUID.randomUUID().toString()
        val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120)
        val finalFile = File(root, "$id-$safeName")
        val partial = File(root, "$id.partial")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            } ?: return false
            val fingerprint = digest.digest().joinToString("") { "%02x".format(it) }
            if (all().any { it.fingerprint.isNotBlank() && it.fingerprint == fingerprint }) {
                partial.delete()
                return false
            }
            if (!partial.renameTo(finalFile)) throw IllegalStateException("Could not finalize imported file")
            val item = LibraryAsset(id, name, classify(name, mime), mime, finalFile.name, finalFile.length(), System.currentTimeMillis(), fingerprint)
            val updated = all() + item
            writeCatalog(updated)
            return true
        } catch (error: Exception) {
            partial.delete()
            finalFile.delete()
            throw error
        }
    }

    @Synchronized fun delete(asset: LibraryAsset): Boolean {
        val updated = all().filterNot { it.id == asset.id }
        writeCatalog(updated)
        return File(root, asset.relativePath).delete() || !File(root, asset.relativePath).exists()
    }

    private fun writeCatalog(items: List<LibraryAsset>) {
        val json = JSONArray()
        items.forEach { a -> json.put(JSONObject().apply {
            put("id", a.id); put("name", a.name); put("category", a.category); put("mime", a.mime)
            put("relativePath", a.relativePath); put("size", a.size); put("importedAt", a.importedAt)
            put("fingerprint", a.fingerprint)
        }) }
        val temp = File(context.filesDir, "nexus-catalog.tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(catalog)) {
            catalog.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun classify(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            ext == "zip" -> "archive"
            ext in setOf("7z", "rar") -> "unsupported-archive"
            ext in setOf("md", "markdown", "mdx") -> "markdown"
            ext in setOf("kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "rs", "go", "html", "css", "json", "xml", "yaml", "yml", "toml", "sql", "sh") -> "source"
            ext in setOf("csv", "tsv", "parquet", "jsonl") -> "dataset"
            ext == "apk" -> "package"
            mime == "application/pdf" || ext == "pdf" -> "pdf"
            ext in setOf("doc", "docx", "rtf", "odt", "ods", "odp", "xls", "xlsx", "ppt", "pptx") -> "office"
            ext in setOf("txt", "log") || mime.startsWith("text/") -> "document"
            else -> "other"
        }
    }
}
