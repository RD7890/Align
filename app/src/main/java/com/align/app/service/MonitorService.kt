package com.align.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.align.app.MainActivity
import com.align.app.R
import com.align.app.data.AppHelper
import com.align.app.data.LimitStore
import com.align.app.usage.UsageHelper
import com.align.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that stays alive in the background. Every couple of seconds (while the
 * screen is on) it asks UsageStatsManager which app is in front. If that app has a limit and
 * today's usage has reached it, it draws the full-screen [BlockOverlay] on top.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var lastLaunch = 0L
    private var widgetTicks = 0

    @Volatile
    private var lastClose = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlay by lazy {
        BlockOverlay(applicationContext) { lastClose = SystemClock.elapsedRealtime() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        // Safety net: if this process gets killed, the alarm brings the monitor back (see RestartReceiver).
        ServiceController.scheduleWatchdog(this)
        if (loop?.isActive != true) {
            loop = scope.launch {
                while (isActive) {
                    try {
                        tick()
                    } catch (e: Exception) {
                        Log.w(TAG, "tick failed", e)
                    }
                    widgetTicks++
                    if (widgetTicks % WIDGET_EVERY_TICKS == 0 &&
                        (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                    ) {
                        try {
                            WidgetUpdater.updateAll(applicationContext)
                        } catch (e: Exception) {
                            Log.w(TAG, "widget update failed", e)
                        }
                    }
                    delay(POLL_MS)
                }
            }
        }
        return START_STICKY
    }

    /** App swiped out of Recents: some phones kill the process right after; ask for a quick restart. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        ServiceController.scheduleWatchdog(this, ServiceController.QUICK_RESTART_MS)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        mainHandler.post { overlay.hide() }
        super.onDestroy()
    }

    private fun tick() {
        val store = LimitStore(this)

        // Pomodoro phase changes (focus <-> break) that happened while the app was closed / the screen was off.
        if (PomodoroEngine.advance(this, store, System.currentTimeMillis(), notify = true) &&
            store.pomoPhase == 0 && !store.focusActive()
        ) {
            ServiceController.sync(this) // cycle finished: stop the monitor if nothing else needs it
        }

        // A focus session that ended while the app was closed / the screen was off.
        val focusEnd = store.focusEndAt
        if (focusEnd != 0L && System.currentTimeMillis() >= focusEnd) finishFocus(store)

        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!power.isInteractive) return

        val focusOn = store.focusActive()
        val limits = if (store.monitoring) store.all() else emptyMap()
        if (!focusOn && limits.isEmpty()) return
        if (!UsageHelper.hasUsageAccess(this)) return

        val snap = UsageHelper.snapshot(this)
        val fg = snap.foreground ?: return

        // 1) Focus session: blocked apps are not allowed until the timer is over.
        if (focusOn && fg in store.focusBlocked()) {
            if (overlay.isShowing) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastLaunch < RELAUNCH_GUARD_MS || now - lastClose < AFTER_CLOSE_GUARD_MS) return
            lastLaunch = now
            val left = store.focusEndAt - System.currentTimeMillis()
            val content = OverlayContent(
                title = "Stay focused",
                message = "${AppHelper.label(this, fg)} is blocked while your focus timer runs. You've got this.",
                caption1 = "Time left", value1 = AppHelper.formatMs(left.coerceAtLeast(0L)),
                caption2 = "Session", value2 = AppHelper.formatMinutes(if (store.pomoPhase != 0) store.pomo().focusMin else store.focusDurationMin)
            )
            mainHandler.post { overlay.show(fg, content) }
            return
        }

        // 2) Daily limits.
        val limitMin = limits[fg] ?: return
        val usedMs = snap.totalMs[fg] ?: 0L
        Log.d(TAG, "fg=$fg used=${usedMs / 1000}s limit=${limitMin}m")
        if (usedMs < limitMin * 60_000L) return

        // Already showing, or user just pressed Close and our app is still coming to the front.
        if (overlay.isShowing) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < RELAUNCH_GUARD_MS || now - lastClose < AFTER_CLOSE_GUARD_MS) return
        lastLaunch = now

        // WindowManager must be used from the main thread.
        mainHandler.post { overlay.show(fg, usedMs, limitMin) }
    }

    private fun finishFocus(store: LimitStore) {
        val minutes = store.focusDurationMin
        store.focusEndAt = 0L
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL_ID, "Focus sessions", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            DONE_NOTIFICATION_ID,
            NotificationCompat.Builder(this, DONE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFFF2541.toInt())
                .setContentTitle("Focus session complete")
                .setContentText("Great work - you stayed focused for $minutes min. Your apps are unlocked.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
        ServiceController.sync(this) // stops the monitor if nothing else needs it
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Limit monitoring", NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID) // channel from versions with the old icon

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF2541.toInt())
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_notification_large))
            .setContentTitle("Align is watching your limits")
            .setContentText("Tap to manage app limits")
            .setOngoing(true)
            .setContentIntent(open)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val TAG = "MonitorService"
        const val DONE_CHANNEL_ID = "focus_done"
        const val DONE_NOTIFICATION_ID = 2
        const val OLD_CHANNEL_ID = "monitor"
        const val CHANNEL_ID = "monitor_v2"
        const val NOTIFICATION_ID = 1
        const val POLL_MS = 2000L
        const val WIDGET_EVERY_TICKS = 30 // ~ every minute
        const val RELAUNCH_GUARD_MS = 3000L
        const val AFTER_CLOSE_GUARD_MS = 3000L
    }
}

/** Starts or stops [MonitorService] depending on settings and granted permissions. */
object ServiceController {
    fun sync(context: Context) {
        val app = context.applicationContext
        val store = LimitStore(app)
        val shouldRun = ((store.monitoring && store.all().isNotEmpty()) || store.focusActive() || store.pomoPhase != 0) &&
            UsageHelper.hasUsageAccess(app) &&
            Settings.canDrawOverlays(app)
        val intent = Intent(app, MonitorService::class.java)
        if (shouldRun) {
            ContextCompat.startForegroundService(app, intent)
        } else {
            app.stopService(intent)
            cancelWatchdog(app)
        }
    }

    const val WATCHDOG_MS = 5 * 60_000L
    const val QUICK_RESTART_MS = 2_000L

    private fun watchdogIntent(app: Context): PendingIntent = PendingIntent.getBroadcast(
        app, 7, Intent(app, RestartReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /**
     * Schedules a wake-up that restarts the monitor if it is not running. Exact alarms are used when
     * allowed (they may start a foreground service from the background), otherwise an inexact one.
     */
    fun scheduleWatchdog(context: Context, delayMs: Long = WATCHDOG_MS) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = watchdogIntent(app)
        val at = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelWatchdog(context: Context) {
        val app = context.applicationContext
        (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(watchdogIntent(app))
    }
}
