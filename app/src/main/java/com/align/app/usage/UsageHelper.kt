package com.align.app.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.align.app.data.AppHelper
import java.util.Calendar

/**
 * Reads UsageStatsManager events (needs the "Usage access" special permission) and works out:
 *  - which app is in the foreground right now
 *  - how long each app has been in the foreground since midnight
 */
object UsageHelper {

    // UsageEvents.Event.ACTIVITY_RESUMED / ACTIVITY_PAUSED share these values with the
    // older MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND constants, so this works on API 26+.
    private const val EVENT_RESUMED = 1
    private const val EVENT_PAUSED = 2
    private const val EVENT_SCREEN_OFF = 16 // SCREEN_NON_INTERACTIVE

    class Snapshot(val totalMs: Map<String, Long>, val foreground: String?)

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun snapshot(context: Context): Snapshot {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(startOfToday(), now)
        val event = UsageEvents.Event()

        val openActivities = HashMap<String, HashSet<String>>() // pkg -> resumed activity classes
        val startedAt = HashMap<String, Long>()                 // pkg -> when it came to front
        val lastResume = HashMap<String, Long>()
        val total = HashMap<String, Long>()

        fun closeApp(pkg: String, ts: Long) {
            val start = startedAt.remove(pkg) ?: return
            total[pkg] = (total[pkg] ?: 0L) + (ts - start).coerceAtLeast(0L)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_RESUMED -> {
                    val pkg = event.packageName ?: continue
                    val set = openActivities.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) startedAt[pkg] = event.timeStamp
                    set.add(event.className ?: "")
                    lastResume[pkg] = event.timeStamp
                }
                EVENT_PAUSED -> {
                    val pkg = event.packageName ?: continue
                    val set = openActivities[pkg] ?: continue
                    set.remove(event.className ?: "")
                    if (set.isEmpty()) closeApp(pkg, event.timeStamp)
                }
                EVENT_SCREEN_OFF -> {
                    // Screen turned off: anything still "open" stops counting here.
                    for ((pkg, set) in openActivities) {
                        if (set.isNotEmpty()) {
                            set.clear()
                            closeApp(pkg, event.timeStamp)
                        }
                    }
                }
            }
        }

        // Apps that are still open right now.
        var foreground: String? = null
        var foregroundTs = -1L
        for ((pkg, set) in openActivities) {
            if (set.isEmpty()) continue
            startedAt[pkg]?.let { total[pkg] = (total[pkg] ?: 0L) + (now - it).coerceAtLeast(0L) }
            val ts = lastResume[pkg] ?: 0L
            if (ts >= foregroundTs) {
                foregroundTs = ts
                foreground = pkg
            }
        }
        return Snapshot(total, foreground)
    }
    // ------------------------------------------------------------------------------------
    // Charts / statistics (used by the dashboard and the per-app detail screen only; the
    // background monitor keeps using snapshot() above).
    // ------------------------------------------------------------------------------------

    private const val SAME_BUCKET_MS = 30_000L

    private class Interval(val pkg: String, val start: Long, val end: Long)

    /**
     * Usage over the last [days] days (oldest first, today last).
     * perDay[i][pkg] = foreground ms of pkg on day i, hourlyToday[pkg][h] = ms in hour h of today.
     */
    class UsageStats(
        val dayStarts: List<Long>,
        val perDay: List<Map<String, Long>>,
        val hourlyToday: Map<String, LongArray>
    ) {
        val todayByApp: Map<String, Long> get() = perDay.last()
        val todayTotal: Long get() = perDay.last().values.sum()
        val dayTotals: List<Long> get() = perDay.map { it.values.sum() }
        /**
         * True when [other] shows the same numbers at 30-second precision. The UI only swaps in new
         * stats when this is false, so charts don't redraw / re-animate on every poll.
         */
        fun sameAs(other: UsageStats): Boolean {
            if (dayStarts != other.dayStarts) return false
            for (i in perDay.indices) {
                val a = perDay[i]
                val b = other.perDay[i]
                if (a.size != b.size) return false
                for ((pkg, ms) in a) {
                    val o = b[pkg] ?: return false
                    if (ms / SAME_BUCKET_MS != o / SAME_BUCKET_MS) return false
                }
            }
            return true
        }

        fun weekFor(pkg: String): List<Long> = perDay.map { it[pkg] ?: 0L }
        fun hoursFor(pkg: String): LongArray = hourlyToday[pkg] ?: LongArray(24)
    }

    private fun startOfDay(offsetDays: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return c.timeInMillis
    }

    private fun intervals(context: Context, from: Long, to: Long): List<Interval> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from, to)
        val event = UsageEvents.Event()
        val result = ArrayList<Interval>()

        val openActivities = HashMap<String, HashSet<String>>()
        val startedAt = HashMap<String, Long>()

        fun closeApp(pkg: String, ts: Long) {
            val start = startedAt.remove(pkg) ?: return
            if (ts > start) result.add(Interval(pkg, start, ts))
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_RESUMED -> {
                    val pkg = event.packageName ?: continue
                    val set = openActivities.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) startedAt[pkg] = event.timeStamp
                    set.add(event.className ?: "")
                }
                EVENT_PAUSED -> {
                    val pkg = event.packageName ?: continue
                    val set = openActivities[pkg] ?: continue
                    set.remove(event.className ?: "")
                    if (set.isEmpty()) closeApp(pkg, event.timeStamp)
                }
                EVENT_SCREEN_OFF -> {
                    for ((pkg, set) in openActivities) {
                        if (set.isNotEmpty()) {
                            set.clear()
                            closeApp(pkg, event.timeStamp)
                        }
                    }
                }
            }
        }
        // Apps still open right now keep counting until "to".
        for ((pkg, set) in openActivities) {
            if (set.isNotEmpty()) closeApp(pkg, to)
        }
        return result
    }

    fun loadStats(context: Context, days: Int = 7): UsageStats {
        val dayStarts = (days - 1 downTo 0).map { startOfDay(-it) }
        val now = System.currentTimeMillis()
        val visible = AppHelper.statsPackages(context)

        val perDay = List(days) { HashMap<String, Long>() }
        val hourly = HashMap<String, LongArray>()
        val todayStart = dayStarts.last()
        val hourMs = 3_600_000L

        for (iv in intervals(context, dayStarts.first(), now)) {
            if (iv.pkg !in visible) continue
            for (d in 0 until days) {
                val dayEnd = if (d == days - 1) Long.MAX_VALUE else dayStarts[d + 1]
                val s = maxOf(iv.start, dayStarts[d])
                val e = minOf(iv.end, dayEnd)
                if (e <= s) continue
                perDay[d][iv.pkg] = (perDay[d][iv.pkg] ?: 0L) + (e - s)
                if (d == days - 1) {
                    val arr = hourly.getOrPut(iv.pkg) { LongArray(24) }
                    for (h in 0 until 24) {
                        val hs = todayStart + h * hourMs
                        val hs2 = maxOf(s, hs)
                        val he2 = minOf(e, hs + hourMs)
                        if (he2 > hs2) arr[h] += he2 - hs2
                    }
                }
            }
        }
        return UsageStats(dayStarts, perDay, hourly)
    }
}
