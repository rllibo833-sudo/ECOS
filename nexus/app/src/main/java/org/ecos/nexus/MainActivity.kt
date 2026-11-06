package org.ecos.nexus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var library: LibraryStore
    private lateinit var ui: NexusScreen
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); library = LibraryStore(this); ui = NexusScreen(this, library); setContentView(ui) }
    fun importFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
        startActivityForResult(intent, IMPORT_REQUEST)
    }
    @Deprecated("Kept for broad Android compatibility in the foundation.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IMPORT_REQUEST || resultCode != RESULT_OK || data == null) return
        val uris = buildList { data.data?.let { add(it) }; data.clipData?.let { clip -> for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri) } }.distinct()
        var count = 0
        uris.forEach { if (runCatching { library.importUri(it) }.getOrDefault(false)) count++ }
        ui.showLibrary()
        Toast.makeText(this, "$count file(s) imported", Toast.LENGTH_SHORT).show()
    }
    companion object { private const val IMPORT_REQUEST = 4101 }
}
