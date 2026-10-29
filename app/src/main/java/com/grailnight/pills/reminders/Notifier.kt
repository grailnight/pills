package com.grailnight.pills.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grailnight.pills.MainActivity
import com.grailnight.pills.R
import com.grailnight.pills.data.IntakeWindow
import com.grailnight.pills.data.Medication
import com.grailnight.pills.domain.Schedule
import com.grailnight.pills.ui.theme.medicationColor
import com.grailnight.pills.util.amount
import com.grailnight.pills.util.doseLine
import com.grailnight.pills.util.formatTime
import com.grailnight.pills.util.windowRange
import java.time.LocalDate

class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private val res = context.resources

    fun createChannels() {
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            res.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = res.getString(R.string.channel_reminders_desc)
            enableVibration(true)
        }
        val stock = NotificationChannel(
            CHANNEL_STOCK,
            res.getString(R.string.channel_stock),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = res.getString(R.string.channel_stock_desc) }
        manager.createNotificationChannels(listOf(reminders, stock))
    }

    fun canNotify(): Boolean = manager.areNotificationsEnabled()

    fun showReminder(
        medication: Medication,
        window: IntakeWindow,
        day: LocalDate,
        windowOver: Boolean,
        snoozeMinutes: Int,
    ) {
        val dayId = day.toEpochDay()
        val end = Schedule.windowEnd(day, window).toLocalTime()
        val title = res.getString(
            if (windowOver) R.string.notif_title_missed else R.string.notif_title,
            medication.name,
        )
        val text = if (windowOver) {
            res.getString(R.string.notif_missed_text, res.windowRange(window.startMinute, window.endMinute))
        } else {
            res.getString(R.string.notif_text, res.doseLine(medication, window.amount), formatTime(end))
        }
        val bigText = listOf(text, medication.note.trim()).filter { it.isNotEmpty() }.joinToString("\n")

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(medicationColor(medication.colorIndex).toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(windowOver)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(
                R.drawable.ic_check,
                res.getString(R.string.action_take),
                actionIntent(ReminderReceiver.ACTION_TAKE, window.id, dayId),
            )
            .addAction(
                R.drawable.ic_snooze,
                res.getString(R.string.action_snooze, snoozeMinutes),
                actionIntent(ReminderReceiver.ACTION_SNOOZE, window.id, dayId),
            )
            .addAction(
                R.drawable.ic_skip,
                res.getString(R.string.action_skip),
                actionIntent(ReminderReceiver.ACTION_SKIP, window.id, dayId),
            )
            .build()
        notify(reminderId(window.id), notification)
    }

    fun showLowStock(medication: Medication) {
        val left = res.amount(medication.stock ?: 0.0, medication.unit)
        val notification = NotificationCompat.Builder(context, CHANNEL_STOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(medicationColor(medication.colorIndex).toArgb())
            .setContentTitle(res.getString(R.string.notif_low_stock_title, medication.name))
            .setContentText(res.getString(R.string.notif_low_stock_text, left))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notify(lowStockId(medication.id), notification)
    }

    fun showTest() {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.notif_test_title))
            .setContentText(res.getString(R.string.notif_test_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notify(TEST_ID, notification)
    }

    fun cancelReminder(windowId: Long) = manager.cancel(reminderId(windowId))

    fun cancelLowStock(medicationId: Long) = manager.cancel(lowStockId(medicationId))

    private fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(id, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(action: String, windowId: Long, day: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        windowId.toInt(),
        ReminderReceiver.actionIntent(context, action, windowId, day),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun reminderId(windowId: Long) = windowId.toInt()

    private fun lowStockId(medicationId: Long) = LOW_STOCK_BASE + medicationId.toInt()

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_STOCK = "stock"
        private const val LOW_STOCK_BASE = 1_000_000
        private const val TEST_ID = 2_000_000
    }
}
