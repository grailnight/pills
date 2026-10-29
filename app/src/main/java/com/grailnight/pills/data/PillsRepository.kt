package com.grailnight.pills.data

import com.grailnight.pills.domain.Schedule
import com.grailnight.pills.reminders.Notifier
import com.grailnight.pills.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

class PillsRepository(
    private val dao: PillsDao,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: Notifier,
) {
    val medications: Flow<List<MedicationWithWindows>> = dao.observeMedications()

    // Writes come from the UI, notification buttons and alarms at once; serialize them so
    // that, for example, a double tap cannot subtract the same dose from the stock twice.
    private val mutex = Mutex()

    fun logs(from: LocalDate, to: LocalDate): Flow<List<IntakeLog>> =
        dao.observeLogs(from.toEpochDay(), to.toEpochDay())

    suspend fun medication(id: Long): MedicationWithWindows? = dao.getMedication(id)

    suspend fun saveMedication(medication: Medication, windows: List<IntakeWindow>) = mutex.withLock {
        val (id, removedWindowIds) = dao.saveMedication(medication, windows)
        removedWindowIds.forEach {
            scheduler.cancel(it)
            notifier.cancelReminder(it)
        }
        rescheduleMedication(id)
    }

    suspend fun deleteMedication(id: Long) = mutex.withLock {
        val windowIds = dao.getMedication(id)?.windows?.map { it.id }.orEmpty()
        dao.deleteMedication(id)
        windowIds.forEach {
            scheduler.cancel(it)
            notifier.cancelReminder(it)
        }
        notifier.cancelLowStock(id)
    }

    suspend fun markTaken(windowId: Long, day: Long) = mutex.withLock { log(windowId, day, LogStatus.TAKEN) }

    suspend fun markSkipped(windowId: Long, day: Long) = mutex.withLock { log(windowId, day, LogStatus.SKIPPED) }

    suspend fun clearLog(windowId: Long, day: Long) = mutex.withLock {
        val existing = dao.getLog(windowId, day) ?: return@withLock
        dao.deleteLog(existing)
        if (existing.status == LogStatus.TAKEN) adjustStock(existing.medicationId, existing.amount)
        scheduler.clearSnooze(windowId)
        rescheduleWindow(windowId)
    }

    private suspend fun log(windowId: Long, day: Long, status: LogStatus) {
        val window = dao.getWindow(windowId) ?: return
        val previous = dao.getLog(windowId, day)
        dao.upsertLog(
            IntakeLog(
                id = previous?.id ?: 0,
                medicationId = window.medicationId,
                windowId = windowId,
                day = day,
                status = status,
                amount = window.amount,
                loggedAt = System.currentTimeMillis(),
            ),
        )
        val stockDelta = when {
            previous?.status == status -> 0.0
            status == LogStatus.TAKEN -> -window.amount
            previous?.status == LogStatus.TAKEN -> previous.amount
            else -> 0.0
        }
        if (stockDelta != 0.0) adjustStock(window.medicationId, stockDelta)
        notifier.cancelReminder(windowId)
        scheduler.clearSnooze(windowId)
        rescheduleWindow(windowId)
    }

    private suspend fun adjustStock(medicationId: Long, delta: Double) {
        val medication = dao.getMedication(medicationId)?.medication ?: return
        val stock = medication.stock ?: return
        val updated = (stock + delta).coerceAtLeast(0.0)
        dao.updateStock(medicationId, updated)
        val wasLow = medication.isLowOnStock
        val isLow = medication.copy(stock = updated).isLowOnStock
        when {
            isLow && !wasLow -> notifier.showLowStock(medication.copy(stock = updated))
            !isLow -> notifier.cancelLowStock(medicationId)
        }
    }

    suspend fun snooze(windowId: Long, day: Long) = mutex.withLock {
        notifier.cancelReminder(windowId)
        val at = LocalDateTime.now().plusMinutes(settings.current.snoozeMinutes.toLong())
        scheduler.snooze(windowId, LocalDate.ofEpochDay(day), at)
    }

    /** Called when a reminder alarm fires. */
    suspend fun onReminderDue(windowId: Long, day: Long, scheduledAt: LocalDateTime) = mutex.withLock {
        val snoozed = scheduler.snoozeFor(windowId) != null
        scheduler.clearSnooze(windowId)
        val window = dao.getWindow(windowId) ?: return@withLock scheduler.cancel(windowId)
        val medication = dao.getMedication(window.medicationId)?.medication ?: return@withLock
        val date = LocalDate.ofEpochDay(day)
        if (medication.remindersEnabled && dao.getLog(windowId, day) == null) {
            // The ping at the window end is a quiet "not marked" note, unless the user snoozed into it.
            val windowOver = !snoozed && !scheduledAt.isBefore(Schedule.windowEnd(date, window))
            notifier.showReminder(medication, window, date, windowOver, settings.current.snoozeMinutes)
        }
        rescheduleWindow(windowId)
    }

    suspend fun rescheduleAll() = mutex.withLock {
        dao.getMedications().forEach { schedule(it) }
    }

    private suspend fun rescheduleMedication(id: Long) {
        dao.getMedication(id)?.let { schedule(it) }
    }

    private suspend fun rescheduleWindow(windowId: Long) {
        val window = dao.getWindow(windowId) ?: return scheduler.cancel(windowId)
        val medication = dao.getMedication(window.medicationId)?.medication ?: return
        schedule(medication, window)
    }

    private suspend fun schedule(entry: MedicationWithWindows) {
        entry.windows.forEach { schedule(entry.medication, it) }
    }

    private suspend fun schedule(medication: Medication, window: IntakeWindow) {
        if (!medication.remindersEnabled) {
            scheduler.cancel(window.id)
            notifier.cancelReminder(window.id)
            return
        }
        val now = LocalDateTime.now()
        val today = now.toLocalDate().toEpochDay()
        val loggedDays = dao.getLogs(window.id, today - 1, today + 7).map { it.day }.toSet()
        val snoozed = scheduler.snoozeFor(window.id)
        if (snoozed != null && snoozed.at > now && snoozed.day.toEpochDay() !in loggedDays) {
            scheduler.schedule(window.id, snoozed.day, snoozed.at)
            return
        }
        val next = Schedule.nextReminder(medication, window, loggedDays, now, settings.current.repeatMinutes)
        if (next == null) scheduler.cancel(window.id) else scheduler.schedule(window.id, next.day, next.at)
    }
}
