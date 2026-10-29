package com.align.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by the watchdog alarm (every few minutes while monitoring, and right after the app is swiped
 * away). Starts the monitor if it isn't running and, because starting it re-arms the alarm, keeps the
 * chain going. If the phone refuses to start it from the background, try again in a minute.
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            ServiceController.sync(context)
        } catch (e: Exception) {
            ServiceController.scheduleWatchdog(context, 60_000L)
        }
    }
}
