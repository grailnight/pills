package com.grailnight.pills.domain

import com.grailnight.pills.data.IntakeLog
import com.grailnight.pills.data.IntakeWindow
import com.grailnight.pills.data.LogStatus
import com.grailnight.pills.data.Medication
import com.grailnight.pills.data.MedicationWithWindows
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class DoseStatus { TAKEN, SKIPPED, UPCOMING, DUE, MISSED }

/** One expected intake: a medication's window on a particular day, plus what was logged for it. */
data class Dose(
    val medication: Medication,
    val window: IntakeWindow,
    val day: LocalDate,
    val log: IntakeLog?,
) {
    val start: LocalDateTime = Schedule.windowStart(day, window)
    val end: LocalDateTime = Schedule.windowEnd(day, window)
    val key: String = "${window.id}:${day.toEpochDay()}"

    fun status(now: LocalDateTime): DoseStatus = when {
        log?.status == LogStatus.TAKEN -> DoseStatus.TAKEN
        log?.status == LogStatus.SKIPPED -> DoseStatus.SKIPPED
        now < start -> DoseStatus.UPCOMING
        now < end -> DoseStatus.DUE
        else -> DoseStatus.MISSED
    }
}

data class Reminder(val day: LocalDate, val at: LocalDateTime)

object Schedule {

    fun windowStart(day: LocalDate, window: IntakeWindow): LocalDateTime =
        day.atStartOfDay().plusMinutes(window.startMinute.toLong())

    fun windowEnd(day: LocalDate, window: IntakeWindow): LocalDateTime {
        val endDay = if (window.endMinute > window.startMinute) day else day.plusDays(1)
        return endDay.atStartOfDay().plusMinutes(window.endMinute.toLong())
    }

    /**
     * Whether a dose is due for [window] on [day]. Windows that had already ended when the
     * medication or window was added are not expected, so they never show up as missed.
     */
    fun isExpected(medication: Medication, window: IntakeWindow, day: LocalDate): Boolean {
        if (!medication.isScheduledOn(day.dayOfWeek)) return false
        val since = Instant.ofEpochMilli(maxOf(medication.createdAt, window.createdAt))
        return windowEnd(day, window) > LocalDateTime.ofInstant(since, ZoneId.systemDefault())
    }

    fun dosesOn(
        day: LocalDate,
        medications: List<MedicationWithWindows>,
        logs: Map<Pair<Long, Long>, IntakeLog>,
    ): List<Dose> = medications
        .flatMap { (medication, windows) ->
            windows
                .filter { isExpected(medication, it, day) }
                .map { Dose(medication, it, day, logs[it.id to day.toEpochDay()]) }
        }
        .sortedWith(compareBy<Dose>({ it.start }, { it.medication.name.lowercase() }))

    fun indexLogs(logs: List<IntakeLog>): Map<Pair<Long, Long>, IntakeLog> =
        logs.associateBy { it.windowId to it.day }

    /**
     * The next moment to remind about [window]: the window start, then every [repeatMinutes]
     * while it stays open, and finally its end. Returns null if nothing is pending within a week.
     */
    fun nextReminder(
        medication: Medication,
        window: IntakeWindow,
        loggedDays: Set<Long>,
        now: LocalDateTime,
        repeatMinutes: Int,
    ): Reminder? {
        val today = now.toLocalDate()
        // Start from yesterday so a window that crosses midnight is still found after it.
        for (offset in -1L..7L) {
            val day = today.plusDays(offset)
            if (!isExpected(medication, window, day) || day.toEpochDay() in loggedDays) continue
            val start = windowStart(day, window)
            val end = windowEnd(day, window)
            when {
                now < start -> return Reminder(day, start)
                now < end -> {
                    if (repeatMinutes <= 0) return Reminder(day, end)
                    val elapsed = Duration.between(start, now).toMinutes()
                    val next = start.plusMinutes((elapsed / repeatMinutes + 1) * repeatMinutes)
                    return Reminder(day, minOf(next, end))
                }
            }
        }
        return null
    }
}
