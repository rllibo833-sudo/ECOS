package org.ecos.nexus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var library: LibraryStore
    private lateinit var graph: KnowledgeGraphStore
    private lateinit var ui: NexusScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = LibraryStore(this)
        graph = KnowledgeGraphStore(this)
        library.all().forEach(graph::ensureAssetNode)
        graph.refreshSuggestions(library.all())
        ui = NexusScreen(this, library, graph)
        setContentView(ui)
    }

    fun importFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, IMPORT_REQUEST)
    }

    @Deprecated("Kept for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IMPORT_REQUEST || resultCode != RESULT_OK || data == null) return
        val uris = buildList {
            data.data?.let { add(it) }
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri) }
        }.distinct()
        Toast.makeText(this, "Importing ${uris.size} file(s)…", Toast.LENGTH_SHORT).show()
        Thread {
            var count = 0
            uris.forEach { uri ->
                if (runCatching { library.importUri(uri) }.getOrDefault(false)) count++
            }
            val assets = library.all()
            assets.forEach(graph::ensureAssetNode)
            graph.refreshSuggestions(assets)
            runOnUiThread {
                ui.showLibrary()
                Toast.makeText(this, "$count file(s) imported", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    companion object { private const val IMPORT_REQUEST = 4101 }
}
