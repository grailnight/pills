package com.grailnight.pills.domain

import com.grailnight.pills.data.IntakeLog
import com.grailnight.pills.data.IntakeWindow
import com.grailnight.pills.data.LogStatus
import com.grailnight.pills.data.Medication
import com.grailnight.pills.data.MedicationWithWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {

    // A Wednesday.
    private val today = LocalDate.of(2026, 10, 28)
    private val medication = Medication(id = 1, name = "Витамин D", createdAt = millis(today.minusDays(30).atTime(12, 0)))
    private val morning = IntakeWindow(id = 10, medicationId = 1, startMinute = 8 * 60, endMinute = 10 * 60, createdAt = medication.createdAt)

    private fun millis(time: LocalDateTime) = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun at(hour: Int, minute: Int = 0, day: LocalDate = today) = day.atTime(hour, minute)

    private fun next(
        now: LocalDateTime,
        window: IntakeWindow = morning,
        med: Medication = medication,
        logged: Set<LocalDate> = emptySet(),
        repeat: Int = 30,
    ) = Schedule.nextReminder(med, window, logged.map { it.toEpochDay() }.toSet(), now, repeat)

    @Test
    fun `before the window reminds at its start`() {
        assertEquals(Reminder(today, at(8)), next(at(6, 15)))
    }

    @Test
    fun `inside the window repeats on the interval grid`() {
        assertEquals(Reminder(today, at(8, 30)), next(at(8)))
        assertEquals(Reminder(today, at(9)), next(at(8, 30)))
        assertEquals(Reminder(today, at(9)), next(at(8, 41)))
    }

    @Test
    fun `the last reminder lands on the window end`() {
        assertEquals(Reminder(today, at(10)), next(at(9, 45), repeat = 60))
        assertEquals(Reminder(today, at(10)), next(at(8, 5), repeat = 0))
    }

    @Test
    fun `after the window moves to the next day`() {
        assertEquals(Reminder(today.plusDays(1), at(8, day = today.plusDays(1))), next(at(10)))
    }

    @Test
    fun `a logged dose is not reminded again`() {
        assertEquals(Reminder(today.plusDays(1), at(8, day = today.plusDays(1))), next(at(8, 10), logged = setOf(today)))
    }

    @Test
    fun `days that are not selected are skipped`() {
        // Mondays and Fridays only; today is Wednesday.
        val med = medication.copy(daysMask = 0b0010001)
        val friday = today.plusDays(2)
        assertEquals(Reminder(friday, at(8, day = friday)), next(at(12), med = med))
    }

    @Test
    fun `no reminders when no day is selected`() {
        assertNull(next(at(12), med = medication.copy(daysMask = 0)))
    }

    @Test
    fun `a window crossing midnight belongs to the day it starts`() {
        val night = morning.copy(startMinute = 22 * 60, endMinute = 2 * 60)
        val tomorrow = today.plusDays(1)
        assertEquals(tomorrow.atTime(2, 0), Schedule.windowEnd(today, night))
        // At 01:00 the window opened yesterday is still pending.
        assertEquals(Reminder(today, tomorrow.atTime(1, 30)), next(tomorrow.atTime(1, 0), window = night))
        assertEquals(Reminder(tomorrow, tomorrow.atTime(22, 0)), next(tomorrow.atTime(1, 0), window = night, logged = setOf(today)))
    }

    @Test
    fun `windows that ended before the medication was added are not expected`() {
        val tomorrow = today.plusDays(1)
        val addedInTheEvening = medication.copy(createdAt = millis(at(19)))
        assertEquals(Reminder(tomorrow, at(8, day = tomorrow)), next(at(19, 5), med = addedInTheEvening))
        assertEquals(emptyList<Dose>(), Schedule.dosesOn(today, listOf(MedicationWithWindows(addedInTheEvening, listOf(morning))), emptyMap()))

        // Added while the window is open: today's dose is still expected.
        val addedInTheMorning = medication.copy(createdAt = millis(at(9)))
        assertEquals(1, Schedule.dosesOn(today, listOf(MedicationWithWindows(addedInTheMorning, listOf(morning))), emptyMap()).size)
        assertEquals(Reminder(today, at(9, 30)), next(at(9, 1), med = addedInTheMorning))
    }

    @Test
    fun `dose status follows the clock and the log`() {
        val dose = Dose(medication, morning, today, log = null)
        assertEquals(DoseStatus.UPCOMING, dose.status(at(7, 59)))
        assertEquals(DoseStatus.DUE, dose.status(at(8)))
        assertEquals(DoseStatus.DUE, dose.status(at(9, 59)))
        assertEquals(DoseStatus.MISSED, dose.status(at(10)))

        val log = IntakeLog(medicationId = 1, windowId = 10, day = today.toEpochDay(), status = LogStatus.TAKEN, amount = 1.0, loggedAt = 0)
        assertEquals(DoseStatus.TAKEN, dose.copy(log = log).status(at(12)))
        assertEquals(DoseStatus.SKIPPED, dose.copy(log = log.copy(status = LogStatus.SKIPPED)).status(at(7)))
    }
}
