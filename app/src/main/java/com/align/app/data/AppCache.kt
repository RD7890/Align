package com.align.app.data

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for app labels and icons. Looking these up through PackageManager (and drawing
 * adaptive icons into bitmaps) is slow, so the dashboard warms the cache on a background thread
 * and list rows only read from memory while scrolling.
 */
object AppCache {
    private const val ICON_PX = 128

    private val labels = ConcurrentHashMap<String, String>()
    private val icons = object : LruCache<String, ImageBitmap>(160) {}

    fun label(context: Context, pkg: String): String =
        labels[pkg] ?: AppHelper.label(context, pkg).also { labels[pkg] = it }

    fun cachedLabel(pkg: String): String? = labels[pkg]

    fun putLabel(pkg: String, label: String) {
        labels[pkg] = label
    }

    /** Icon if it is already cached (cheap, safe on the main thread). */
    fun peekIcon(pkg: String): ImageBitmap? = synchronized(icons) { icons.get(pkg) }

    /** Loads (and caches) the icon. Call off the main thread. */
    fun icon(context: Context, pkg: String): ImageBitmap? {
        peekIcon(pkg)?.let { return it }
        val loaded = AppHelper.icon(context, pkg, ICON_PX) ?: return null
        synchronized(icons) { icons.put(pkg, loaded) }
        return loaded
    }

    /** Loads labels and icons for [pkgs] ahead of time. Call off the main thread. */
    fun warm(context: Context, pkgs: Collection<String>, iconLimit: Int = 8) {
        // Labels for all, icons only for the first few: the rest load on demand when a row is shown.
        pkgs.forEachIndexed { i, pkg ->
            label(context, pkg)
            if (i < iconLimit) icon(context, pkg)
        }
    }
}
