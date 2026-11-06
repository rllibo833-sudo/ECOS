package org.ecos.nexus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class NexusStorageTest {
    private lateinit var context: Context

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "nexus-knowledge.json").delete()
        File(context.filesDir, "nexus-knowledge.json.tmp").delete()
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
}
