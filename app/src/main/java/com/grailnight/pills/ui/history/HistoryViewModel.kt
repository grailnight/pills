package com.grailnight.pills.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grailnight.pills.AppContainer
import com.grailnight.pills.domain.Dose
import com.grailnight.pills.domain.DoseStatus
import com.grailnight.pills.domain.Schedule
import com.grailnight.pills.ui.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

data class DaySummary(
    val date: LocalDate,
    val doses: List<Dose>,
    val taken: Int,
    /** Doses that are already decided: taken, skipped or missed. */
    val resolved: Int,
    val isFuture: Boolean,
) {
    val total: Int get() = doses.size
}

data class HistoryState(
    val loading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val weeks: List<List<DaySummary>> = emptyList(),
    val selected: DaySummary? = null,
    /** Share of taken doses over the last 30 days, or null without data. */
    val ratePercent: Int? = null,
    val streak: Int = 0,
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val now = minuteTicker()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logs = now
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { today -> repository.logs(today.minusDays(HISTORY_DAYS), today) }

    val state: StateFlow<HistoryState> =
        combine(repository.medications, logs, now, selectedDate) { medications, logs, now, selected ->
            val today = now.toLocalDate()
            val index = Schedule.indexLogs(logs)
            fun summary(date: LocalDate): DaySummary {
                val doses = if (date > today) {
                    Schedule.dosesOn(date, medications, emptyMap())
                } else {
                    Schedule.dosesOn(date, medications, index)
                }
                val statuses = doses.map { it.status(now) }
                return DaySummary(
                    date = date,
                    doses = doses,
                    taken = statuses.count { it == DoseStatus.TAKEN },
                    resolved = statuses.count { it == DoseStatus.TAKEN || it == DoseStatus.SKIPPED || it == DoseStatus.MISSED },
                    isFuture = date > today,
                )
            }

            val gridStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(GRID_WEEKS - 1L)
            val weeks = (0 until GRID_WEEKS).map { week ->
                (0..6).map { summary(gridStart.plusDays(week * 7L + it)) }
            }

            val lastMonth = (0L until 30L).map { summary(today.minusDays(it)) }
            val resolved = lastMonth.sumOf { it.resolved }
            val rate = if (resolved == 0) null else lastMonth.sumOf { it.taken } * 100 / resolved

            HistoryState(
                loading = false,
                now = now,
                weeks = weeks,
                selected = summary(selected ?: today),
                ratePercent = rate,
                streak = streak(today, ::summary),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    /** Days in a row, ending today or yesterday, on which every dose was taken. */
    private fun streak(today: LocalDate, summary: (LocalDate) -> DaySummary): Int {
        var streak = 0
        for (offset in 0L until HISTORY_DAYS) {
            val day = summary(today.minusDays(offset))
            when {
                day.total == 0 -> continue
                day.taken == day.total -> streak++
                offset == 0L -> continue // today is still in progress
                else -> break
            }
        }
        return streak
    }

    fun select(date: LocalDate) {
        selectedDate.value = date
    }

    fun toggle(dose: Dose) {
        container.appScope.launch {
            if (dose.status(LocalDateTime.now()) == DoseStatus.TAKEN) {
                repository.clearLog(dose.window.id, dose.day.toEpochDay())
            } else {
                repository.markTaken(dose.window.id, dose.day.toEpochDay())
            }
        }
    }

    private companion object {
        const val GRID_WEEKS = 5
        const val HISTORY_DAYS = 90L
    }
}
