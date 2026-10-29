package com.align.app.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * Circular theme-change reveal (the same idea as the splash -> home reveal):
 * 1. the screen is captured,
 * 2. the new theme is applied underneath,
 * 3. the old screenshot is drawn on top with a circular hole that grows out of the tapped control.
 *
 * [origin] is where the circle starts (root coordinates). It is set by whichever control starts the change.
 */
object ThemeReveal {
    var origin: Offset = Offset.Unspecified

    /** Copies the current window pixels. Calls [onDone] on the main thread with null if it is not possible. */
    fun capture(activity: Activity, onDone: (Bitmap?) -> Unit) {
        val decor = activity.window.decorView
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) {
            onDone(null)
            return
        }
        try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            PixelCopy.request(
                activity.window, bmp,
                { result -> onDone(if (result == PixelCopy.SUCCESS) bmp else null) },
                Handler(Looper.getMainLooper())
            )
        } catch (t: Throwable) {
            onDone(null)
        }
    }
}

/**
 * Returns a modifier that tracks this element's position, and a function that stores its centre as the
 * reveal origin. Call the function right before asking for a theme change.
 */
@Composable
fun rememberThemeOrigin(): Pair<Modifier, () -> Unit> {
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val tracker = remember {
        Modifier.onGloballyPositioned { holder[0] = it }
    }
    val mark = remember {
        {
            val c = holder[0]
            if (c != null && c.isAttached) {
                ThemeReveal.origin = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f)
            }
        }
    }
    return tracker to mark
}
