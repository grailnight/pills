package com.grailnight.pills.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.grailnight.pills.container
import kotlinx.coroutines.launch

/** Alarms do not survive reboots, app updates or clock changes, so they are rebuilt here. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = context.container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.repository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // Only string constants are referenced, so the API 31 one is safe on older versions.
        @SuppressLint("InlinedApi")
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
