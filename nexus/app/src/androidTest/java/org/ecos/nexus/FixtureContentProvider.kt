package org.ecos.nexus

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.IOException

class FixtureContentProvider : ContentProvider() {
    data class Payload(val name: String, val bytes: ByteArray, val failAfter: Int?)
    companion object { val files = mutableMapOf<String, Payload>() }

    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val payload = files[uri.path] ?: throw IllegalArgumentException("Unknown fixture")
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME)
        return MatrixCursor(columns).apply {
            newRow().apply { columns.forEach { add(if (it == OpenableColumns.DISPLAY_NAME) payload.name else null) } }
        }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val payload = files[uri.path] ?: throw IllegalArgumentException("Unknown fixture")
        val pipe = ParcelFileDescriptor.createReliablePipe()
        Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                val limit = payload.failAfter?.coerceIn(0, payload.bytes.size) ?: payload.bytes.size
                if (limit > 0) output.write(payload.bytes, 0, limit)
                if (payload.failAfter != null) throw IOException("Simulated interrupted provider stream")
            }
        }.apply { uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> } }.start()
        return pipe[0]
    }
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
