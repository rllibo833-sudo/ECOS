package org.ecos.nexus

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class LibraryAsset(val id: String, val name: String, val category: String, val mime: String, val relativePath: String, val size: Long, val importedAt: Long)

class LibraryStore(private val context: Context) {
    private val root = File(context.filesDir, "library").apply { mkdirs() }
    private val catalog = File(context.filesDir, "nexus-catalog.json")

    @Synchronized fun all(): List<LibraryAsset> = runCatching {
        val array = JSONArray(if (catalog.exists()) catalog.readText() else "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            LibraryAsset(o.getString("id"), o.getString("name"), o.getString("category"), o.optString("mime"), o.getString("relativePath"), o.optLong("size"), o.optLong("importedAt"))
        }.sortedByDescending { it.importedAt }
    }.getOrDefault(emptyList())

    @Synchronized fun importUri(uri: Uri): Boolean {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "import-" + java.util.Date().time
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val id = UUID.randomUUID().toString()
        val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val target = File(root, id + "-" + safeName)
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } } ?: return false
        val item = LibraryAsset(id, name, classify(name, mime), mime, target.name, target.length(), java.util.Date().time)
        val json = JSONArray()
        (all() + item).forEach { a -> json.put(JSONObject().apply {
            put("id", a.id); put("name", a.name); put("category", a.category); put("mime", a.mime); put("relativePath", a.relativePath); put("size", a.size); put("importedAt", a.importedAt)
        }) }
        val temp = File(context.filesDir, "nexus-catalog.tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(catalog)) { catalog.writeText(temp.readText()); temp.delete() }
        return true
    }

    private fun classify(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            ext in setOf("zip", "7z", "rar") -> "archive"
            ext in setOf("md", "markdown", "mdx") -> "markdown"
            ext in setOf("kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "rs", "go", "html", "css", "json", "xml", "yaml", "yml", "toml", "sql", "sh") -> "source"
            ext in setOf("csv", "tsv", "parquet", "jsonl") -> "dataset"
            ext == "apk" -> "package"
            mime == "application/pdf" || ext in setOf("pdf", "doc", "docx", "txt", "rtf", "odt") -> "document"
            else -> "other"
        }
    }
}
