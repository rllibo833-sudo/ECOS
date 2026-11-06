package org.ecos.nexus

import android.app.Activity
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.widget.ProgressBar
import android.widget.TextView
import android.app.AlertDialog
import android.view.Gravity

class MainActivity : Activity() {
    private lateinit var library: LibraryStore
    private lateinit var graph: KnowledgeGraphStore
    private lateinit var ui: NexusScreen
    @Volatile private var cancelImport = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = LibraryStore(this)
        graph = KnowledgeGraphStore(this)
        library.all().forEach(graph::ensureAssetNode)
        graph.refreshSuggestions(library.all())
        ui = NexusScreen(this, library, graph)
        setContentView(ui)
    }


    fun requestMediaPermissions() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            Toast.makeText(this, "Media access is already granted", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissions(missing.toTypedArray(), MEDIA_PERMISSION_REQUEST)
        }
    }

    @Deprecated("Kept for broad Android compatibility.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MEDIA_PERMISSION_REQUEST) {
            val granted = grantResults.count { it == PackageManager.PERMISSION_GRANTED }
            Toast.makeText(this, "$granted of ${grantResults.size} media permissions granted", Toast.LENGTH_LONG).show()
        }
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
        cancelImport = false
        val progressText = TextView(this).apply { text = "Preparing import…"; setPadding(0, 0, 0, 12) }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = uris.size.coerceAtLeast(1); progress = 0 }
        val progressLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
            addView(progressText)
            addView(progressBar, android.widget.LinearLayout.LayoutParams(-1, 8))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Importing files").setView(progressLayout)
            .setNegativeButton("Cancel") { _, _ -> cancelImport = true }.create()
        dialog.setOnCancelListener { cancelImport = true }
        dialog.show()
        Thread {
            var count = 0
            var failed = 0
            uris.forEachIndexed { index, uri ->
                if (cancelImport) return@forEachIndexed
                val result = runCatching { library.importUri(uri) }
                if (result.getOrDefault(false)) count++ else if (result.isFailure) failed++
                runOnUiThread {
                    progressBar.progress = index + 1
                    progressText.text = "Processed ${index + 1} of ${uris.size}"
                }
            }
            val assets = library.all()
            assets.forEach(graph::ensureAssetNode)
            graph.refreshSuggestions(assets)
            runOnUiThread {
                if (dialog.isShowing) dialog.dismiss()
                ui.showLibrary()
                val status = if (cancelImport) "Import cancelled" else "Import complete"
                Toast.makeText(this, "$status · $count added · $failed failed", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    companion object { private const val IMPORT_REQUEST = 4101; private const val MEDIA_PERMISSION_REQUEST = 4102 }
}
