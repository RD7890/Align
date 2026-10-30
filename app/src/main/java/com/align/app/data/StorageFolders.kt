package com.align.app.data

import android.content.Context
import android.util.Log

/**
 * Creates Align's own folders on shared storage, named after the package (com.align.app):
 *
 *   Android/data/com.align.app/    (files/ lives inside it)
 *   Android/obb/com.align.app/
 *   Android/media/com.align.app/
 *
 * These are the app-specific directories Android reserves for every app, so no storage permission is needed.
 * Asking the system for them is what makes it create them. Safe to call on every start (it only creates what is
 * missing) and it should run off the main thread.
 *
 * Note: on Android 11+ other apps and most file managers cannot browse Android/data or Android/obb (only Align
 * itself, adb and the system can). Android/media stays readable by other apps.
 */
object StorageFolders {
    private const val TAG = "AlignStorage"

    fun ensure(context: Context) {
        val app = context.applicationContext
        // Each call is isolated: one failing (e.g. storage not mounted) must not stop the others.
        run("data") { app.getExternalFilesDir(null) }       // Android/data/<package>/files  (creates .../<package>)
        run("obb") { app.obbDir }                           // Android/obb/<package>
        run("media") { app.externalMediaDirs.firstOrNull() } // Android/media/<package>
    }

    private fun run(label: String, dir: () -> java.io.File?) {
        try {
            val f = dir()
            if (f != null && !f.exists()) f.mkdirs()
            Log.d(TAG, "$label -> ${f?.absolutePath} exists=${f?.exists()}")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not create $label folder", t)
        }
    }
}
