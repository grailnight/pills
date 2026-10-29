package com.grailnight.pills.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.grailnight.pills.domain.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Keeps at most one pending alarm per intake window. */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val snoozes = context.getSharedPreferences("snoozes", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(windowId: Long, day: LocalDate, at: LocalDateTime) {
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = ReminderReceiver.alarmIntent(context, windowId, day.toEpochDay(), triggerAt)
        val pending = PendingIntent.getBroadcast(
            context,
            windowId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(windowId: Long) {
        val pending = PendingIntent.getBroadcast(
            context,
            windowId.toInt(),
            ReminderReceiver.alarmIntent(context, windowId, 0, 0),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
        clearSnooze(windowId)
    }

    /** Snoozes are persisted so a later reschedule does not pull the reminder forward again. */
    fun snooze(windowId: Long, day: LocalDate, at: LocalDateTime) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        snoozes.edit { putString(windowId.toString(), "${day.toEpochDay()}:$millis") }
        schedule(windowId, day, at)
    }

    fun snoozeFor(windowId: Long): Reminder? {
        val (day, millis) = snoozes.getString(windowId.toString(), null)
            ?.split(':')
            ?.mapNotNull { it.toLongOrNull() }
            ?.takeIf { it.size == 2 }
            ?: return null
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        return Reminder(LocalDate.ofEpochDay(day), at)
    }

    fun clearSnooze(windowId: Long) {
        snoozes.edit { remove(windowId.toString()) }
    }
}
