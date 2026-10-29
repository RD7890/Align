package com.align.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.util.LruCache
import com.caverock.androidsvg.SVG

/**
 * Renders the downloaded unDraw SVG illustrations (the .svg files in app/src/main/assets/illustrations,
 * MIT licence, accent colour already switched to #FF2541) to bitmaps. Used by both the Compose
 * UI and the plain-View block overlay.
 */
object Illustrations {

    private const val TAG = "Illustrations"

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Already-rendered bitmap, or null. Lets composables start with the image (no fade-in flicker). */
    fun cached(name: String, widthPx: Int): Bitmap? = synchronized(cache) { cache.get("$name@$widthPx") }

    /** Renders the given illustrations ahead of time (call off the main thread, e.g. during the splash). */
    fun preload(context: Context, names: List<String>, widthPx: Int) {
        for (n in names) render(context, n, widthPx)
    }

    /**
     * Returns a transparent bitmap [widthPx] wide (height follows the SVG aspect ratio).
     * [name] is an illustration ("time-management") or a brand asset ("brand/shield").
     */
    fun render(context: Context, name: String, widthPx: Int): Bitmap? {
        val key = "$name@$widthPx"
        synchronized(cache) { cache.get(key) }?.let { return it }
        return try {
            val path = if (name.contains('/')) "$name.svg" else "illustrations/$name.svg"
            val svg = SVG.getFromAsset(context.applicationContext.assets, path)
            val box = svg.documentViewBox
            val ratio = if (box != null && box.width() > 0f) box.height() / box.width() else 1f
            val heightPx = (widthPx * ratio).toInt().coerceAtLeast(1)
            svg.documentWidth = widthPx.toFloat()
            svg.documentHeight = heightPx.toFloat()
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bitmap))
            synchronized(cache) { cache.put(key, bitmap) }
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Could not render $name", e)
            null
        }
    }
}
