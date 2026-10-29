package com.align.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import com.align.app.MainActivity
import com.align.app.R

/** The one Align widget. It can be resized to any size; pages are redrawn for the exact size. */
class FocusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        refresh(context)

    private fun refresh(context: Context) {
        val pending = goAsync()
        Thread {
            try {
                WidgetUpdater.updateAll(context)
            } catch (e: Exception) {
                // keep the previous widget content
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object WidgetUpdater {

    /** Fire-and-forget refresh from the UI thread. */
    fun updateAllAsync(context: Context, delayMs: Long = 0L) {
        val app = context.applicationContext
        Thread {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                if (delayMs > 0) Thread.sleep(delayMs)
                updateAll(app)
            } catch (e: Exception) { /* ignore */ }
        }.start()
    }

    /** Redraws every placed widget. Call off the main thread. Cheap when no widget is placed. */
    @Synchronized
    fun updateAll(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, FocusWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val data = WidgetData.load(app)
        for (id in ids) push(app, mgr, id, data)
    }

    /** Real size of the widget in pixels (portrait: min width x max height, landscape: max width x min height). */
    private fun sizePx(app: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = mgr.getAppWidgetOptions(id)
        val density = app.resources.displayMetrics.density
        val land = app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val wDp = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 } ?: 250
        val hDp = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            .takeIf { it > 0 } ?: 110
        return Pair((wDp * density).toInt().coerceAtLeast(64), (hDp * density).toInt().coerceAtLeast(64))
    }

    private fun push(app: Context, mgr: AppWidgetManager, id: Int, data: WidgetData) {
        val (wPx, hPx) = sizePx(app, mgr, id)
        val pages = WidgetRenderer.render(app, data, wPx, hPx)
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val views = RemoteViews(app.packageName, R.layout.widget_flipper)
        views.setImageViewBitmap(R.id.page1, pages[0])
        views.setImageViewBitmap(R.id.page2, pages[1])
        views.setImageViewBitmap(R.id.page3, pages[2])
        views.setOnClickPendingIntent(R.id.widget_root, open)
        views.setOnClickPendingIntent(R.id.flipper, open)
        mgr.updateAppWidget(id, views)
    }
}
