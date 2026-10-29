package com.grailnight.pills.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grailnight.pills.R
import com.grailnight.pills.data.PillUnit
import com.grailnight.pills.ui.AppViewModels
import com.grailnight.pills.ui.components.AmountStepper
import com.grailnight.pills.ui.components.PillBadge
import com.grailnight.pills.ui.components.SectionTitle
import com.grailnight.pills.ui.components.TimePickerDialog
import com.grailnight.pills.ui.theme.MedicationColors
import com.grailnight.pills.util.amount
import com.grailnight.pills.util.daysSummary
import com.grailnight.pills.util.formatMinuteOfDay
import com.grailnight.pills.util.label
import com.grailnight.pills.util.shortLabel

private data class PickerTarget(val index: Int, val isStart: Boolean)

/** Half a tablet or half a millilitre makes sense, half a capsule or drop does not. */
private val PillUnit.step: Double
    get() = when (this) {
        PillUnit.TABLET, PillUnit.ML -> 0.5
        PillUnit.CAPSULE, PillUnit.DROP, PillUnit.PIECE -> 1.0
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    onClose: () -> Unit,
    viewModel: EditViewModel = viewModel(factory = AppViewModels.Factory),
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) R.string.edit_new_title else R.string.edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    Button(onClick = { viewModel.save(onClose) }, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.edit_save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (!viewModel.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicsSection(viewModel)
            LookSection(viewModel)
            ScheduleSection(viewModel, onPickTime = { picker = it })
            DaysSection(viewModel)
            RemindersSection(viewModel)
            StockSection(viewModel)
            if (!viewModel.isNew) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_delete))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picker?.let { target ->
        val window = viewModel.windows.getOrNull(target.index) ?: return@let
        TimePickerDialog(
            title = stringResource(if (target.isStart) R.string.time_picker_start else R.string.time_picker_end),
            initialMinute = if (target.isStart) window.startMinute else window.endMinute,
            onDismiss = { picker = null },
            onConfirm = { minute ->
                viewModel.updateWindow(target.index) {
                    if (target.isStart) {
                        // Keep the window length when the start moves.
                        val length = (it.endMinute - it.startMinute).mod(24 * 60)
                        it.copy(startMinute = minute, endMinute = (minute + length) % (24 * 60))
                    } else {
                        it.copy(endMinute = minute)
                    }
                }
                picker = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.edit_delete_confirm_title, viewModel.name)) },
            text = { Text(stringResource(R.string.edit_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(onClose) }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionTitle(title)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun BasicsSection(vm: EditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            PillBadge(vm.colorIndex, vm.unit, size = 88.dp)
        }
        OutlinedTextField(
            value = vm.name,
            onValueChange = { vm.name = it },
            label = { Text(stringResource(R.string.edit_name)) },
            isError = vm.nameError,
            supportingText = if (vm.nameError) {
                { Text(stringResource(R.string.edit_name_error)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.dosage,
            onValueChange = { vm.dosage = it },
            label = { Text(stringResource(R.string.edit_dosage)) },
            placeholder = { Text(stringResource(R.string.edit_dosage_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.note,
            onValueChange = { vm.note = it },
            label = { Text(stringResource(R.string.edit_note)) },
            placeholder = { Text(stringResource(R.string.edit_note_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LookSection(vm: EditViewModel) {
    SectionCard(stringResource(R.string.edit_section_look)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            MedicationColors.forEachIndexed { index, color ->
                val selected = index == vm.colorIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable { vm.colorIndex = index },
                ) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillUnit.entries.forEach { unit ->
                FilterChip(
                    selected = vm.unit == unit,
                    onClick = { vm.unit = unit },
                    label = { Text(stringResource(unit.label)) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleSection(vm: EditViewModel, onPickTime: (PickerTarget) -> Unit) {
    val res = LocalContext.current.resources
    SectionCard(stringResource(R.string.edit_section_schedule)) {
        vm.windows.forEachIndexed { index, window ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.edit_window_from), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        TimeChip(formatMinuteOfDay(window.startMinute)) { onPickTime(PickerTarget(index, true)) }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_window_to), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        TimeChip(formatMinuteOfDay(window.endMinute)) { onPickTime(PickerTarget(index, false)) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.removeWindow(index) }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_remove_window),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (window.endMinute <= window.startMinute) {
                        Text(
                            stringResource(R.string.edit_window_next_day),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, end = 12.dp)) {
                        Text(
                            stringResource(R.string.edit_window_amount),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        AmountStepper(
                            value = window.amount,
                            label = res.amount(window.amount, vm.unit),
                            onChange = { amount -> vm.updateWindow(index) { it.copy(amount = amount) } },
                            step = vm.unit.step,
                            min = vm.unit.step,
                        )
                    }
                }
            }
        }
        if (vm.windowsError) {
            Text(stringResource(R.string.edit_windows_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = vm::addWindow) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.edit_add_window))
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun DaysSection(vm: EditViewModel) {
    val res = LocalContext.current.resources
    val names = stringArrayResource(R.array.weekdays_short)
    SectionCard(stringResource(R.string.edit_section_days)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            names.forEachIndexed { index, name ->
                val selected = vm.daysMask and (1 shl index) != 0
                val background by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "day",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(background)
                        .clickable { vm.toggleDay(index) },
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            res.daysSummary(vm.daysMask),
            style = MaterialTheme.typography.bodyMedium,
            color = if (vm.daysError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemindersSection(vm: EditViewModel) {
    SectionCard(stringResource(R.string.edit_section_reminders)) {
        SwitchRow(
            title = stringResource(R.string.edit_reminders_switch),
            subtitle = stringResource(R.string.edit_reminders_hint),
            checked = vm.remindersEnabled,
            onChange = { vm.remindersEnabled = it },
        )
    }
}

@Composable
private fun StockSection(vm: EditViewModel) {
    SectionCard(stringResource(R.string.edit_section_stock)) {
        SwitchRow(
            title = stringResource(R.string.edit_stock_switch),
            subtitle = stringResource(R.string.edit_stock_hint),
            checked = vm.trackStock,
            onChange = { vm.trackStock = it },
        )
        AnimatedVisibility(vm.trackStock) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = vm.stockText,
                    onChange = { vm.stockText = it },
                    label = stringResource(R.string.edit_stock_current),
                    suffix = stringResource(vm.unit.shortLabel),
                    isError = vm.stockError,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = vm.thresholdText,
                    onChange = { vm.thresholdText = it },
                    label = stringResource(R.string.edit_stock_threshold),
                    suffix = stringResource(vm.unit.shortLabel),
                    isError = vm.thresholdError,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) },
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
