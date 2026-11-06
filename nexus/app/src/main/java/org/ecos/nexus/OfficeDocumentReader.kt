package org.ecos.nexus

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

/** Bounded, read-only text extraction for common OOXML office files. */
class OfficeDocumentReader(context: Context) {
    private val inspector = AssetInspector(context)

    fun read(asset: LibraryAsset, maxChars: Int = 128 * 1024): String {
        require(maxChars in 1..512 * 1024)
        val file = inspector.file(asset)
        require(file.isFile && file.length() <= 100L * 1024 * 1024) { "Office file is missing or exceeds the preview limit" }
        return ZipFile(file).use { zip ->
            when (file.extension.lowercase()) {
                "docx" -> paragraphs(zip, "word/document.xml", "p", maxChars)
                "pptx" -> slides(zip, maxChars)
                "xlsx" -> spreadsheet(zip, maxChars)
                else -> throw IllegalArgumentException("Preview is available for DOCX, XLSX, and PPTX. Open legacy formats externally.")
            }
        }.ifBlank { "No readable text was found in this document." }
    }

    private fun slides(zip: ZipFile, limit: Int): String {
        val out = StringBuilder()
        for (index in 1..40) {
            val entry = zip.getEntry("ppt/slides/slide$index.xml") ?: continue
            if (out.length >= limit) break
            out.append("\n— Slide $index —\n")
            out.append(paragraphs(zip, entry.name, "p", limit - out.length))
        }
        return out.toString()
    }

    private fun spreadsheet(zip: ZipFile, limit: Int): String {
        val shared = zip.getEntry("xl/sharedStrings.xml")?.let { parseTextElements(zip, it.name, "si", limit) }.orEmpty()
        val sheet = zip.getEntry("xl/worksheets/sheet1.xml") ?: return "No first worksheet was found."
        val parser = parser(zip, sheet.name)
        val out = StringBuilder()
        var cellType = ""
        var value = StringBuilder()
        var inValue = false
        var inInline = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT && out.length < limit) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "row" -> Unit
                    "c" -> cellType = parser.getAttributeValue(null, "t").orEmpty()
                    "v" -> { value = StringBuilder(); inValue = true }
                    "t" -> if (cellType == "inlineStr") { value = StringBuilder(); inInline = true }
                }
                XmlPullParser.TEXT -> if (inValue || inInline) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "v" -> {
                        val raw = value.toString()
                        out.append(if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw)
                        out.append("  ")
                        inValue = false
                    }
                    "t" -> if (inInline) {
                        out.append(value)
                        out.append("  ")
                        inInline = false
                    }
                    "c" -> cellType = ""
                    "row" -> out.append("\n")
                }
            }
            parser.next()
        }
        return out.toString().take(limit)
    }

    private fun paragraphs(zip: ZipFile, path: String, paragraphTag: String, limit: Int): String {
        val parser = parser(zip, path)
        val out = StringBuilder()
        var inText = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT && out.length < limit) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name.substringAfter(':') == "t") inText = true
                XmlPullParser.TEXT -> if (inText) out.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    if (tag == "t") inText = false
                    if (tag == paragraphTag) out.append("\n")
                }
            }
            parser.next()
        }
        return out.toString().take(limit)
    }

    private fun parseTextElements(zip: ZipFile, path: String, parentTag: String, limit: Int): List<String> {
        val parser = parser(zip, path)
        val values = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        var inText = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT && values.sumOf { it.length } < limit) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    if (tag == parentTag) { depth++; if (depth == 1) current = StringBuilder() }
                    if (depth > 0 && tag == "t") inText = true
                }
                XmlPullParser.TEXT -> if (inText) current.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfter(':')
                    if (tag == "t") inText = false
                    if (tag == parentTag && depth > 0) {
                        depth--
                        if (depth == 0) values += current.toString()
                    }
                }
            }
            parser.next()
        }
        return values
    }

    private fun parser(zip: ZipFile, path: String): XmlPullParser {
        val entry = zip.getEntry(path) ?: throw IllegalArgumentException("Office document part is missing")
        require(entry.size in 0..(2L * 1024 * 1024)) { "Office XML part exceeds the preview limit" }
        val xml = XmlPullParserFactory.newInstance().newPullParser()
        xml.setInput(zip.getInputStream(entry), "UTF-8")
        return xml
    }
}
