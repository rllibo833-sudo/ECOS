package org.ecos.nexus

import android.content.Context
import android.content.ContentValues
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class NexusStorageTest {
    private lateinit var context: Context

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "nexus-knowledge.json").delete()
        File(context.filesDir, "nexus-knowledge.json.tmp").delete()
        File(context.filesDir, "nexus-catalog.json").delete()
        File(context.filesDir, "library").deleteRecursively()
        File(context.filesDir, "library.restore-old").deleteRecursively()
    }


    private fun fixtureContext(name: String, bytes: ByteArray, failAfter: Int? = null): Pair<Context, Uri> {
        FixtureContentProvider.files["/file"] = FixtureContentProvider.Payload(name, bytes, failAfter)
        val providerContext = InstrumentationRegistry.getInstrumentation().context
        val wrapped = object : ContextWrapper(context) {
            override fun getContentResolver() = providerContext.contentResolver
        }
        return wrapped to Uri.parse("content://org.ecos.nexus.test.fixture/file")
    }

    @Test fun contentResolverImportKeepsCatalogAndOriginalConsistent() {
        val (source, uri) = fixtureContext("notes.txt", "offline notes".toByteArray())
        val library = LibraryStore(source)
        assertTrue(library.importUri(uri))
        val imported = library.all().single()
        assertEquals("notes.txt", imported.name)
        assertEquals("offline notes".length.toLong(), imported.size)
        assertTrue(File(source.filesDir, "library/${imported.relativePath}").exists())
        assertEquals(imported.fingerprint, LibraryStore(source).all().single().fingerprint)
    }

    @Test fun duplicateContentIsNotAddedTwice() {
        val (source, uri) = fixtureContext("same.txt", "same bytes".toByteArray())
        val library = LibraryStore(source)
        assertTrue(library.importUri(uri))
        assertFalse(library.importUri(uri))
        assertEquals(1, library.all().size)
        assertEquals(1, File(source.filesDir, "library").listFiles()?.count { !it.name.endsWith(".partial") })
    }

    @Test fun zeroByteFileCanBeImportedAndCatalogued() {
        val (source, uri) = fixtureContext("empty.bin", byteArrayOf())
        val library = LibraryStore(source)
        assertTrue(library.importUri(uri))
        assertEquals(0L, library.all().single().size)
    }

    @Test fun interruptedImportLeavesNoCatalogEntryOrPartialFile() {
        val (source, uri) = fixtureContext("broken.bin", ByteArray(1024) { it.toByte() }, 128)
        val library = LibraryStore(source)
        assertTrue(runCatching { library.importUri(uri) }.isFailure)
        assertTrue(library.all().isEmpty())
        assertTrue(File(source.filesDir, "library").listFiles().orEmpty().isEmpty())
    }

    @Test fun canonicalRelationshipsPersistAsTypedDirectedEdges() {
        val store = KnowledgeGraphStore(context)
        val a = store.createArticle("Nation of ECOS", "A world archive")
        val b = store.createArticle("ECOS Book I", "The first volume")
        store.addCanonical(a.id, b.id, "contains", "user-confirmed")

        val reopened = KnowledgeGraphStore(context)
        assertEquals(2, reopened.nodes().size)
        assertEquals(1, reopened.edges("canonical").size)
        assertEquals("contains", reopened.edges("canonical").single().relation)
        assertTrue(reopened.edges("semantic").isEmpty())
    }

    @Test fun semanticCandidatesRemainSeparateFromCanonicalFacts() {
        val store = KnowledgeGraphStore(context)
        val assets = listOf(
            LibraryAsset("a", "ECOS Nation Lore.md", "markdown", "text/markdown", "a", 10, 1),
            LibraryAsset("b", "ECOS Nation Book.md", "markdown", "text/markdown", "b", 10, 2)
        )
        store.refreshSuggestions(assets)

        assertTrue(store.edges("canonical").isEmpty())
        assertEquals(1, store.edges("semantic").size)
        assertEquals("suggested", store.edges("semantic").single().status)
        store.reviewSemantic(store.edges("semantic").single().id, true)
        assertEquals("accepted", store.edges("semantic").single().status)
        assertTrue(store.edges("canonical").isEmpty())
    }

    @Test fun zipInventoryIsReadOnlyAndDoesNotExtractEntries() {
        val library = File(context.filesDir, "library").apply { mkdirs() }
        val zip = File(library, "sample.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("docs/readme.md"))
            out.write("# Hello".toByteArray())
            out.closeEntry()
        }
        val asset = LibraryAsset("zip", "sample.zip", "archive", "application/zip", zip.name, zip.length(), 1)
        val entries = AssetInspector(context).zipEntries(asset)
        assertEquals("docs/readme.md", entries.single().name)
        assertFalse(File(library, "docs").exists())
    }
    @Test fun offlineBackupRoundTripsKnowledgeGraph() {
        val library = LibraryStore(context)
        val graph = KnowledgeGraphStore(context)
        graph.createArticle("Backup article", "Stored offline")
        val output = ByteArrayOutputStream()
        NexusBackup(context, library).exportTo(output)

        File(context.filesDir, "nexus-knowledge.json").delete()
        NexusBackup(context, library).restoreFrom(ByteArrayInputStream(output.toByteArray()))

        assertEquals("Backup article", KnowledgeGraphStore(context).nodes().single().title)
    }

    @Test fun backupRestoreRejectsPathTraversal() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("../outside.txt"))
            zip.write("unsafe".toByteArray())
            zip.closeEntry()
        }
        val result = runCatching {
            NexusBackup(context, LibraryStore(context)).restoreFrom(ByteArrayInputStream(output.toByteArray()))
        }
        assertTrue(result.isFailure)
        assertFalse(File(context.filesDir.parentFile, "outside.txt").exists())
    }

    @Test fun docxTextPreviewReadsParagraphsOffline() {
        val libraryDir = File(context.filesDir, "library").apply { mkdirs() }
        val file = File(libraryDir, "sample.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("""<w:document xmlns:w="urn:w"><w:body><w:p><w:r><w:t>Offline Nexus</w:t></w:r></w:p><w:p><w:r><w:t>Second paragraph</w:t></w:r></w:p></w:body></w:document>""".toByteArray())
            zip.closeEntry()
        }
        val asset = LibraryAsset("docx", file.name, "office", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", file.name, file.length(), 1)
        val text = OfficeDocumentReader(context).read(asset)
        assertTrue(text.contains("Offline Nexus"))
        assertTrue(text.contains("Second paragraph"))
    }

    @Test fun xlsxTextPreviewResolvesSharedStrings() {
        val libraryDir = File(context.filesDir, "library").apply { mkdirs() }
        val file = File(libraryDir, "sample.xlsx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write("""<sst xmlns="urn:s"><si><t>Knowledge</t></si><si><t>Library</t></si></sst>""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write("""<worksheet xmlns="urn:s"><sheetData><row><c t="s"><v>0</v></c><c t="s"><v>1</v></c></row></sheetData></worksheet>""".toByteArray())
            zip.closeEntry()
        }
        val asset = LibraryAsset("xlsx", file.name, "office", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.name, file.length(), 1)
        val text = OfficeDocumentReader(context).read(asset)
        assertTrue(text.contains("Knowledge"))
        assertTrue(text.contains("Library"))
    }

    @Test fun importedPdfCanBeRenderedOfflineByNexusReader() {
        val library = File(context.filesDir, "library").apply { mkdirs() }
        val pdf = File(library, "reader-test.pdf")
        val document = android.graphics.pdf.PdfDocument()
        val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 400, 1).create())
        page.canvas.drawText("Nexus offline PDF", 24f, 48f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
        document.finishPage(page)
        pdf.outputStream().use { document.writeTo(it) }
        document.close()

        val reader = PdfDocumentView(context, pdf)
        assertEquals(1, reader.pageCount)
        reader.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.AT_MOST)
        )
        reader.layout(0, 0, reader.measuredWidth, reader.measuredHeight)
        val bitmap = android.graphics.Bitmap.createBitmap(reader.measuredWidth, reader.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        reader.draw(android.graphics.Canvas(bitmap))
        assertTrue(bitmap.width > 0)
        bitmap.recycle()
        reader.close()
    }

}
