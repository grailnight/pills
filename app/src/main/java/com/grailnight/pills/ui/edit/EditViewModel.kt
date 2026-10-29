package com.grailnight.pills.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grailnight.pills.AppContainer
import com.grailnight.pills.data.IntakeWindow
import com.grailnight.pills.data.Medication
import com.grailnight.pills.data.PillUnit
import com.grailnight.pills.ui.theme.MedicationColors
import com.grailnight.pills.util.formatNumber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WindowDraft(
    val id: Long = 0,
    val startMinute: Int,
    val endMinute: Int,
    val amount: Double = 1.0,
    val createdAt: Long? = null,
)

class EditViewModel(savedState: SavedStateHandle, private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val medicationId: Long = savedState.get<Long>(ARG_ID) ?: 0L
    val isNew: Boolean get() = medicationId == 0L

    var loaded by mutableStateOf(isNew)
        private set
    var showErrors by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
    var dosage by mutableStateOf("")
    var note by mutableStateOf("")
    var unit by mutableStateOf(PillUnit.TABLET)
    var colorIndex by mutableIntStateOf(0)
    var daysMask by mutableIntStateOf(Medication.ALL_DAYS)
    var remindersEnabled by mutableStateOf(true)
    var trackStock by mutableStateOf(false)
    var stockText by mutableStateOf("")
    var thresholdText by mutableStateOf("5")
    val windows = mutableStateListOf<WindowDraft>()

    private var original: Medication? = null

    init {
        viewModelScope.launch {
            if (isNew) {
                colorIndex = repository.medications.first().size % MedicationColors.size
                windows += WindowDraft(startMinute = 8 * 60, endMinute = 10 * 60)
            } else {
                load()
            }
        }
    }

    private suspend fun load() {
        val entry = repository.medication(medicationId) ?: return
        val medication = entry.medication
        original = medication
        name = medication.name
        dosage = medication.dosage
        note = medication.note
        unit = medication.unit
        colorIndex = medication.colorIndex
        daysMask = medication.daysMask
        remindersEnabled = medication.remindersEnabled
        trackStock = medication.stock != null
        stockText = medication.stock?.let(::formatNumber).orEmpty()
        thresholdText = formatNumber(medication.lowStockThreshold)
        windows.clear()
        windows += entry.sortedWindows.map {
            WindowDraft(it.id, it.startMinute, it.endMinute, it.amount, it.createdAt)
        }
        loaded = true
    }

    val nameError: Boolean get() = showErrors && name.isBlank()
    val windowsError: Boolean get() = showErrors && windows.isEmpty()
    val daysError: Boolean get() = showErrors && daysMask == 0
    val stockError: Boolean get() = showErrors && trackStock && parse(stockText) == null
    val thresholdError: Boolean get() = showErrors && trackStock && parse(thresholdText) == null

    fun addWindow() {
        val last = windows.maxByOrNull { it.startMinute }
        val start = if (last == null) 8 * 60 else (last.startMinute + 6 * 60) % (24 * 60)
        val length = last?.let { (it.endMinute - it.startMinute).mod(24 * 60) }?.takeIf { it > 0 } ?: 120
        windows += WindowDraft(
            startMinute = start,
            endMinute = (start + length) % (24 * 60),
            amount = last?.amount ?: 1.0,
        )
    }

    fun updateWindow(index: Int, transform: (WindowDraft) -> WindowDraft) {
        windows[index] = transform(windows[index])
    }

    fun removeWindow(index: Int) {
        windows.removeAt(index)
    }

    fun toggleDay(dayIndex: Int) {
        daysMask = daysMask xor (1 shl dayIndex)
    }

    /** Highlights the problems and keeps the screen open when the form is not valid. */
    fun save(onSaved: () -> Unit) {
        showErrors = true
        if (name.isBlank() || windows.isEmpty() || daysMask == 0) return
        val stock = if (trackStock) parse(stockText) ?: return else null
        val threshold = if (trackStock) parse(thresholdText) ?: return else original?.lowStockThreshold ?: 5.0

        val now = System.currentTimeMillis()
        val medication = Medication(
            id = medicationId,
            name = name.trim(),
            dosage = dosage.trim(),
            note = note.trim(),
            unit = unit,
            colorIndex = colorIndex,
            daysMask = daysMask,
            remindersEnabled = remindersEnabled,
            stock = stock,
            lowStockThreshold = threshold,
            createdAt = original?.createdAt ?: now,
        )
        val intakeWindows = windows.map {
            IntakeWindow(
                id = it.id,
                medicationId = medicationId,
                startMinute = it.startMinute,
                endMinute = it.endMinute,
                amount = it.amount,
                createdAt = it.createdAt ?: now,
            )
        }
        container.appScope.launch { repository.saveMedication(medication, intakeWindows) }
        onSaved()
    }

    fun delete(onDeleted: () -> Unit) {
        if (isNew) return onDeleted()
        container.appScope.launch { repository.deleteMedication(medicationId) }
        onDeleted()
    }

    private fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

    companion object {
        const val ARG_ID = "id"
    }
}
