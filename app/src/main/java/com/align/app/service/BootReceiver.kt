package com.align.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the monitor after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                ServiceController.sync(context)
            } catch (e: Exception) {
                // Some OEMs block background starts; the service will start next time the app opens.
            }
        }
    }
}
