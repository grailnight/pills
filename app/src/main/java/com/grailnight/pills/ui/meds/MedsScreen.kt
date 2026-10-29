package com.grailnight.pills.ui.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grailnight.pills.AppContainer
import com.grailnight.pills.R
import com.grailnight.pills.data.MedicationWithWindows
import com.grailnight.pills.ui.AppViewModels
import com.grailnight.pills.ui.components.EmptyState
import com.grailnight.pills.ui.components.PillBadge
import com.grailnight.pills.util.amount
import com.grailnight.pills.util.daysSummary
import com.grailnight.pills.util.windowRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MedsViewModel(container: AppContainer) : ViewModel() {
    val medications: StateFlow<List<MedicationWithWindows>?> = container.repository.medications
        .map<List<MedicationWithWindows>, List<MedicationWithWindows>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun MedsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: MedsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    MedsContent(medications, onAdd, onEdit)
}

@Composable
fun MedsContent(
    medications: List<MedicationWithWindows>?,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        when {
            medications == null -> Unit
            medications.isEmpty() -> EmptyState(
                title = stringResource(R.string.meds_empty_title),
                text = stringResource(R.string.meds_empty_hint),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBar + 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Text(
                        stringResource(R.string.meds_title),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
                items(medications, key = { it.medication.id }) { entry ->
                    MedicationCard(entry, onClick = { onEdit(entry.medication.id) }, modifier = Modifier.animateItem())
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.meds_add)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@Composable
private fun MedicationCard(entry: MedicationWithWindows, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    val medication = entry.medication
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillBadge(medication.colorIndex, medication.unit, size = 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOf(medication.dosage, res.daysSummary(medication.daysMask))
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!medication.remindersEnabled) {
                    Icon(
                        Icons.Rounded.NotificationsOff,
                        contentDescription = stringResource(R.string.meds_reminders_off),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entry.windows.isEmpty()) {
                Text(
                    stringResource(R.string.meds_no_windows),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    entry.sortedWindows.forEach { window ->
                        Chip(res.windowRange(window.startMinute, window.endMinute) + " · " + res.amount(window.amount, medication.unit))
                    }
                }
            }
            medication.stock?.let { stock ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    val color = if (medication.isLowOnStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.meds_stock_left, res.amount(stock, medication.unit)),
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
