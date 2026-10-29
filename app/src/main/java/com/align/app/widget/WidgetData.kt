package com.align.app.widget

import android.content.Context
import com.align.app.data.AppCache
import com.align.app.data.LimitStore
import com.align.app.usage.UsageHelper

class AppUsage(val pkg: String, val label: String, val ms: Long)
class LimitUsage(val pkg: String, val label: String, val usedMs: Long, val limitMin: Int) {
    val fraction: Float get() = (usedMs.toFloat() / (limitMin * 60_000f)).coerceIn(0f, 1f)
    val reached: Boolean get() = usedMs >= limitMin * 60_000L
}

/** Everything the widgets draw, loaded once per refresh. Call off the main thread. */
class WidgetData(
    val hasUsage: Boolean,
    val total: Long,
    val week: List<Long>,
    val top: List<AppUsage>,
    val limits: List<LimitUsage>
) {
    val reachedCount: Int get() = limits.count { it.reached }

    companion object {
        fun load(context: Context): WidgetData {
            val app = context.applicationContext
            val limitMap = LimitStore(app).all()
            if (!UsageHelper.hasUsageAccess(app)) {
                return WidgetData(false, 0L, List(7) { 0L }, emptyList(), emptyList())
            }
            val stats = UsageHelper.loadStats(app, 7)
            val today = stats.todayByApp
            val top = today.entries
                .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                .take(5)
            val limits = limitMap.entries
                .map { LimitUsage(it.key, "", today[it.key] ?: 0L, it.value) }
                .sortedWith(compareByDescending<LimitUsage> { it.fraction }.thenBy { it.pkg })
            AppCache.warm(app, top.map { it.key } + limits.take(3).map { it.pkg })
            return WidgetData(
                hasUsage = true,
                total = stats.todayTotal,
                week = stats.dayTotals,
                top = top.map { AppUsage(it.key, AppCache.label(app, it.key), it.value) },
                limits = limits.map { LimitUsage(it.pkg, AppCache.label(app, it.pkg), it.usedMs, it.limitMin) }
            )
        }
    }
}
