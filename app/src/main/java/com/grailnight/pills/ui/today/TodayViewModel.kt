package com.grailnight.pills.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grailnight.pills.AppContainer
import com.grailnight.pills.data.Medication
import com.grailnight.pills.domain.Dose
import com.grailnight.pills.domain.DoseStatus
import com.grailnight.pills.domain.Schedule
import com.grailnight.pills.ui.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class TodayState(
    val loading: Boolean = true,
    val now: LocalDateTime = LocalDateTime.now(),
    val hasMedications: Boolean = false,
    val doses: List<Dose> = emptyList(),
    val lowStock: List<Medication> = emptyList(),
) {
    val taken: Int get() = doses.count { it.status(now) == DoseStatus.TAKEN }
    val remaining: List<Dose> get() = doses.filter { it.status(now) in OPEN }

    private companion object {
        val OPEN = setOf(DoseStatus.DUE, DoseStatus.UPCOMING)
    }
}

class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    private val now = minuteTicker()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val logs = now
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { today -> repository.logs(today.minusDays(1), today) }

    val state: StateFlow<TodayState> = combine(repository.medications, logs, now) { medications, logs, now ->
        val today = now.toLocalDate()
        val index = Schedule.indexLogs(logs)
        // Windows that started yesterday and run past midnight are still shown while open.
        val carriedOver = Schedule.dosesOn(today.minusDays(1), medications, index)
            .filter { it.status(now) == DoseStatus.DUE }
        TodayState(
            loading = false,
            now = now,
            hasMedications = medications.isNotEmpty(),
            doses = carriedOver + Schedule.dosesOn(today, medications, index),
            lowStock = medications.map { it.medication }.filter { it.isLowOnStock },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    fun toggleTaken(dose: Dose) = write {
        if (dose.status(LocalDateTime.now()) == DoseStatus.TAKEN) {
            repository.clearLog(dose.window.id, dose.day.toEpochDay())
        } else {
            repository.markTaken(dose.window.id, dose.day.toEpochDay())
        }
    }

    fun markTaken(dose: Dose) = write { repository.markTaken(dose.window.id, dose.day.toEpochDay()) }

    fun skip(dose: Dose) = write { repository.markSkipped(dose.window.id, dose.day.toEpochDay()) }

    fun reset(dose: Dose) = write { repository.clearLog(dose.window.id, dose.day.toEpochDay()) }

    // Writes run in the app scope so leaving the screen does not cut them short.
    private fun write(block: suspend () -> Unit) {
        container.appScope.launch { block() }
    }
}
