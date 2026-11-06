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

    fun backupArchive() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "Nexus-Backup.zip")
        }
        startActivityForResult(intent, BACKUP_REQUEST)
    }

    fun restoreArchive() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, RESTORE_REQUEST)
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
        if (resultCode != RESULT_OK || data?.data == null) return
        if (requestCode == BACKUP_REQUEST) {
            val uri = data.data!!
            Thread {
                val result = runCatching {
                    contentResolver.openOutputStream(uri)?.use { NexusBackup(this, library).exportTo(it) }
                        ?: error("Could not open backup destination")
                }
                runOnUiThread {
                    Toast.makeText(this, if (result.isSuccess) "Nexus backup created" else "Backup failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }.start()
            return
        }
        if (requestCode == RESTORE_REQUEST) {
            val uri = data.data!!
            AlertDialog.Builder(this).setTitle("Restore Nexus backup?")
                .setMessage("This replaces the current Nexus library and knowledge graph with the selected backup.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore") { _, _ ->
                    Thread {
                        val result = runCatching {
                            contentResolver.openInputStream(uri)?.use { NexusBackup(this, library).restoreFrom(it) }
                                ?: error("Could not open backup")
                        }
                        runOnUiThread {
                            if (result.isSuccess) {
                                library.all().forEach(graph::ensureAssetNode)
                                ui.showLibrary()
                            }
                            Toast.makeText(this, if (result.isSuccess) "Backup restored" else "Restore failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }.start()
                }.show()
            return
        }
        if (requestCode != IMPORT_REQUEST) return
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

    companion object { private const val IMPORT_REQUEST = 4101; private const val MEDIA_PERMISSION_REQUEST = 4102; private const val BACKUP_REQUEST = 4103; private const val RESTORE_REQUEST = 4104 }
}
