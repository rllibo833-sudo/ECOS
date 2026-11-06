package org.ecos.nexus

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.zip.ZipFile

/** Lightweight, read-only preview for text assets and ZIP inventories. */
object AssetPreview {
    fun create(context: Context, asset: LibraryAsset): View {
        val root = ScrollView(context)
        val body = TextView(context).apply { setTextColor(0xFFE6EEF8.toInt()); textSize = 14f; setPadding(20, 20, 20, 28); setTextIsSelectable(true) }
        val file = File(File(context.filesDir, "library"), asset.relativePath)
        body.text = when (asset.category) {
            "markdown" -> renderMarkdown(file)
            "source" -> readText(file)
            "archive" -> zipInventory(file)
            "dataset" -> readText(file, 120_000)
            else -> "${asset.name}\n\n${asset.category.uppercase()}\n${asset.mime}\n${asset.size} bytes\n\nStored locally. A dedicated binary/media viewer remains planned."
        }
        if (asset.category == "source") body.typeface = Typeface.MONOSPACE
        root.addView(body); return root
    }
    private fun readText(file: File, limit: Int = 240_000): String = runCatching { file.inputStream().bufferedReader().use { it.readText().take(limit) } }.getOrElse { "Unable to read file: ${it.message}" }
    private fun renderMarkdown(file: File): String = readText(file).lineSequence().map { line ->
        when {
            line.startsWith("# ") -> line.removePrefix("# ").uppercase() + "\n" + "═".repeat(minOf(line.length - 1, 42))
            line.startsWith("## ") -> "\n" + line.removePrefix("## ").uppercase() + "\n" + "─".repeat(minOf(line.length - 2, 36))
            line.startsWith("### ") -> "\n" + line.removePrefix("### ") + "\n"
            line.startsWith("- ") || line.startsWith("* ") -> "  •  " + line.drop(2)
            else -> line
        }
    }.joinToString("\n")
    private fun zipInventory(file: File): String = runCatching {
        ZipFile(file).use { zip ->
            val entries = zip.entries(); val out = StringBuilder("ARCHIVE CONTENTS\n\n"); var count = 0
            while (entries.hasMoreElements() && count < 500) {
                val e = entries.nextElement()
                out.append(if (e.isDirectory) "▸ " else "  ").append(e.name).append(if (!e.isDirectory) "  (${e.size} bytes)" else "").append('\n'); count++
            }
            if (entries.hasMoreElements()) out.append("\n… inventory limited to first 500 entries.")
            out.toString()
        }
    }.getOrElse { "Could not inspect archive: ${it.message}" }
}
