package com.grailnight.pills.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.grailnight.pills.container
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Handles fired reminder alarms and the buttons on reminder notifications. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val windowId = intent.getLongExtra(EXTRA_WINDOW_ID, -1)
        val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE)
        if (windowId < 0 || day == Long.MIN_VALUE) return

        val container = context.container
        val repository = container.repository
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> {
                        val millis = intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
                        val scheduledAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                        repository.onReminderDue(windowId, day, scheduledAt)
                    }
                    ACTION_TAKE -> repository.markTaken(windowId, day)
                    ACTION_SKIP -> repository.markSkipped(windowId, day)
                    ACTION_SNOOZE -> repository.snooze(windowId, day)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.grailnight.pills.REMIND"
        const val ACTION_TAKE = "com.grailnight.pills.TAKE"
        const val ACTION_SKIP = "com.grailnight.pills.SKIP"
        const val ACTION_SNOOZE = "com.grailnight.pills.SNOOZE"

        private const val EXTRA_WINDOW_ID = "window_id"
        private const val EXTRA_DAY = "day"
        private const val EXTRA_TRIGGER_AT = "trigger_at"

        fun alarmIntent(context: Context, windowId: Long, day: Long, triggerAt: Long): Intent =
            actionIntent(context, ACTION_REMIND, windowId, day).putExtra(EXTRA_TRIGGER_AT, triggerAt)

        fun actionIntent(context: Context, action: String, windowId: Long, day: Long): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_WINDOW_ID, windowId)
                .putExtra(EXTRA_DAY, day)
    }
}
