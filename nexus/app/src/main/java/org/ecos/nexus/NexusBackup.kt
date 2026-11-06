package org.ecos.nexus

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Full offline archive snapshot. Restore replaces Nexus data only after staging and validation succeed. */
class NexusBackup(private val context: Context, private val library: LibraryStore) {
    private val root = File(context.filesDir, "library")
    private val catalog = File(context.filesDir, "nexus-catalog.json")
    private val knowledge = File(context.filesDir, "nexus-knowledge.json")

    fun exportTo(output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("catalog.json"))
            zip.write(if (catalog.isFile) catalog.readBytes() else "[]".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("knowledge.json"))
            zip.write(if (knowledge.isFile) knowledge.readBytes() else """{"nodes":[],"edges":[],"schemaVersion":1}""".toByteArray())
            zip.closeEntry()
            library.all().forEach { asset ->
                val file = File(root, asset.relativePath).canonicalFile
                require(file.path.startsWith(root.canonicalPath + File.separator) && file.isFile) { "Invalid or missing asset: ${asset.name}" }
                zip.putNextEntry(ZipEntry("assets/${asset.relativePath}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.finish()
        }
    }

    fun restoreFrom(input: InputStream) {
        val stage = File(context.cacheDir, "nexus-restore-stage").apply {
            deleteRecursively()
            mkdirs()
            File(this, "assets").mkdirs()
        }
        var totalBytes = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val normalized = entry.name.replace('\\', '/')
                    require(normalized.isNotBlank() && !normalized.startsWith("/") &&
                        normalized.split('/').none { it == ".." || it.isBlank() }) { "Unsafe backup entry path" }
                    val target = File(stage, normalized).canonicalFile
                    require(target.path.startsWith(stage.canonicalPath + File.separator)) { "Backup entry escapes staging directory" }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                require(totalBytes <= 2L * 1024 * 1024 * 1024) { "Backup exceeds 2 GiB restore limit" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val stagedCatalog = File(stage, "catalog.json")
            val stagedKnowledge = File(stage, "knowledge.json")
            val stagedAssets = File(stage, "assets")
            require(stagedCatalog.isFile && stagedKnowledge.isFile && stagedAssets.isDirectory) { "Backup is incomplete" }
            val items = JSONArray(stagedCatalog.readText())
            for (i in 0 until items.length()) {
                val relative = items.getJSONObject(i).getString("relativePath")
                require(relative.isNotBlank() && !relative.contains('/') && !relative.contains('\\')) { "Invalid asset path in catalog" }
                require(File(stagedAssets, relative).isFile) { "Backup is missing asset: $relative" }
            }
            // Validate the graph JSON before replacing the existing archive.
            org.json.JSONObject(stagedKnowledge.readText())
            val oldRoot = File(context.filesDir, "library.restore-old")
            oldRoot.deleteRecursively()
            if (root.exists() && !root.renameTo(oldRoot)) error("Could not stage existing library")
            if (!stagedAssets.renameTo(root)) {
                oldRoot.renameTo(root)
                error("Could not install restored assets")
            }
            try {
                replaceFile(stagedCatalog, catalog)
                replaceFile(stagedKnowledge, knowledge)
                oldRoot.deleteRecursively()
            } catch (error: Exception) {
                root.deleteRecursively()
                oldRoot.renameTo(root)
                throw error
            }
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun replaceFile(source: File, destination: File) {
        val temp = File(destination.parentFile, destination.name + ".restore")
        source.copyTo(temp, overwrite = true)
        if (destination.exists() && !destination.delete()) error("Could not replace ${destination.name}")
        if (!temp.renameTo(destination)) error("Could not finalize ${destination.name}")
    }
}
