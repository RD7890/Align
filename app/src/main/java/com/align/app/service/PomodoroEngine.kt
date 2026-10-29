package com.align.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.align.app.MainActivity
import com.align.app.R
import com.align.app.data.LimitStore
import com.align.app.data.PomoState

/**
 * Pomodoro state machine shared by the UI and [MonitorService], so the cycle keeps moving even when the
 * app is closed. Blocking reuses the normal focus logic: `focusEndAt` is set only during a FOCUS phase, so
 * blocked apps are locked while focusing and free during breaks.
 *
 * Cycle: FOCUS -> short break -> FOCUS -> ... -> last FOCUS -> long break -> done.
 */
object PomodoroEngine {
    const val IDLE = 0
    const val FOCUS = 1
    const val SHORT_BREAK = 2
    const val LONG_BREAK = 3

    private const val CHANNEL_ID = "pomodoro"
    private const val NOTIFICATION_ID = 3

    fun lengthMs(s: PomoState, phase: Int): Long = when (phase) {
        FOCUS -> s.focusMin
        SHORT_BREAK -> s.breakMin
        else -> s.longMin
    } * 60_000L

    private fun enter(store: LimitStore, phase: Int, round: Int, end: Long) {
        store.setPomoRun(phase, round, end)
        store.focusEndAt = if (phase == FOCUS && end > 0L) end else 0L
    }

    /** Starts a fresh cycle with focus round 1. */
    fun start(store: LimitStore, now: Long) {
        enter(store, FOCUS, 1, now + lengthMs(store.pomo(), FOCUS))
    }

    /** Ends the whole cycle (used by "end session" and when the cycle completes). */
    fun stop(store: LimitStore) {
        store.setPomoRun(IDLE, 0, 0L)
        store.focusEndAt = 0L
    }

    /**
     * Starts the waiting phase (manual continue) or skips the running break. A running FOCUS phase is
     * never skipped: it stays locked until it ends.
     */
    fun startNext(store: LimitStore, now: Long) {
        val s = store.pomo()
        when {
            s.phase == IDLE -> Unit
            s.end == 0L -> enter(store, s.phase, s.round, now + lengthMs(s, s.phase))
            s.phase == SHORT_BREAK -> enter(store, FOCUS, s.round + 1, now + lengthMs(s, FOCUS))
            s.phase == LONG_BREAK -> stop(store)
        }
    }

    /**
     * Moves the cycle forward if the current phase is over (chains several phases when a lot of time has
     * passed). Returns true if anything changed. [notify] posts one notification for the resulting state.
     */
    fun advance(ctx: Context, store: LimitStore, now: Long, notify: Boolean): Boolean {
        var s = store.pomo()
        var changed = false
        while (s.phase != IDLE && s.end != 0L && now >= s.end) {
            changed = true
            val endedAt = s.end
            val nextPhase: Int
            val nextRound: Int
            when (s.phase) {
                FOCUS -> {
                    nextPhase = if (s.round >= s.rounds) LONG_BREAK else SHORT_BREAK
                    nextRound = s.round
                }
                SHORT_BREAK -> {
                    nextPhase = FOCUS
                    nextRound = s.round + 1
                }
                else -> {
                    nextPhase = IDLE
                    nextRound = 0
                }
            }
            if (nextPhase == IDLE) {
                stop(store)
            } else {
                // Auto-continue chains from the moment the last phase ended, not from "now".
                enter(store, nextPhase, nextRound, if (s.auto) endedAt + lengthMs(s, nextPhase) else 0L)
            }
            s = store.pomo()
        }
        if (changed && notify) postNotification(ctx, s)
        return changed
    }

    private fun postNotification(ctx: Context, s: PomoState) {
        val (title, text) = when {
            s.phase == IDLE -> "Pomodoro complete" to "All ${s.rounds} focus rounds done. Your apps are unlocked."
            s.phase == FOCUS && s.end != 0L ->
                "Back to focus" to "Round ${s.round} of ${s.rounds} - ${s.focusMin} min. Blocked apps are locked again."
            s.phase == FOCUS -> "Focus round ${s.round} is ready" to "Open Align to start it."
            s.phase == SHORT_BREAK && s.end != 0L -> "Break time" to "${s.breakMin} min to rest. Your apps are unlocked."
            s.phase == SHORT_BREAK -> "Break is ready" to "Open Align to start your ${s.breakMin} min break."
            s.end != 0L -> "Long break" to "${s.longMin} min to recharge. Your apps are unlocked."
            else -> "Long break is ready" to "Open Align to start your ${s.longMin} min break."
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pomodoro", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFFF2541.toInt())
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }
}
