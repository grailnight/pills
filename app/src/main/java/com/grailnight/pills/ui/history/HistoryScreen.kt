package com.grailnight.pills.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grailnight.pills.R
import com.grailnight.pills.domain.Dose
import com.grailnight.pills.domain.DoseStatus
import com.grailnight.pills.ui.AppViewModels
import com.grailnight.pills.ui.components.PillBadge
import com.grailnight.pills.util.RU
import com.grailnight.pills.util.doseLine
import com.grailnight.pills.util.ruPlural
import com.grailnight.pills.util.windowRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = AppViewModels.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(state, onSelect = viewModel::select, onToggle = viewModel::toggle)
}

@Composable
fun HistoryContent(state: HistoryState, onSelect: (LocalDate) -> Unit, onToggle: (Dose) -> Unit) {
    if (state.loading) return
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val selected = state.selected

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBar + 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    icon = Icons.Rounded.TaskAlt,
                    value = state.ratePercent?.let { "$it%" } ?: "—",
                    caption = stringResource(R.string.history_rate_caption),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = Icons.Rounded.LocalFireDepartment,
                    value = state.streak.toString(),
                    caption = LocalContext.current.resources.ruPlural(R.array.history_streak_caption, state.streak),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "calendar") {
            CalendarCard(state, onSelect)
        }
        if (selected != null) {
            item(key = "day-title") {
                val title = selected.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU))
                    .replaceFirstChar { it.titlecase(RU) }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            if (selected.doses.isEmpty()) {
                item(key = "day-empty") {
                    Text(
                        stringResource(R.string.history_day_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(selected.doses, key = { it.key }) { dose ->
                HistoryDoseRow(dose, dose.status(state.now), editable = !selected.isFuture, onToggle = { onToggle(dose) })
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    caption: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = container, contentColor = content, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null)
            Text(value, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun CalendarCard(state: HistoryState, onSelect: (LocalDate) -> Unit) {
    val names = stringArrayResource(R.array.weekdays_short)
    val today = state.now.toLocalDate()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                names.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            state.weeks.forEach { week ->
                Row {
                    week.forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            DayCell(
                                day = day,
                                isToday = day.date == today,
                                isSelected = day.date == state.selected?.date,
                                onClick = { onSelect(day.date) },
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
            ) {
                LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.history_legend_all))
                LegendDot(MaterialTheme.colorScheme.primaryContainer, stringResource(R.string.history_legend_partial))
                LegendDot(MaterialTheme.colorScheme.errorContainer, stringResource(R.string.history_legend_none))
            }
        }
    }
}

@Composable
private fun DayCell(day: DaySummary, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (fill, text) = when {
        day.isFuture || day.total == 0 -> Color.Transparent to colors.onSurfaceVariant
        day.taken == day.total -> colors.primary to colors.onPrimary
        day.taken > 0 -> colors.primaryContainer to colors.onPrimaryContainer
        day.resolved > 0 -> colors.errorContainer to colors.onErrorContainer
        else -> Color.Transparent to colors.onSurface
    }
    val background by animateColorAsState(fill, label = "dayFill")
    val ring = when {
        isSelected -> colors.onSurface
        isToday -> colors.primary
        else -> Color.Transparent
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .border(2.dp, ring, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (day.isFuture) text.copy(alpha = 0.45f) else text,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryDoseRow(dose: Dose, status: DoseStatus, editable: Boolean, onToggle: () -> Unit) {
    val res = LocalContext.current.resources
    val colors = MaterialTheme.colorScheme
    val (icon, tint, label) = when (status) {
        DoseStatus.TAKEN -> Triple(Icons.Rounded.Check, colors.primary, stringResource(R.string.history_status_taken))
        DoseStatus.SKIPPED -> Triple(Icons.Rounded.DoNotDisturbOn, colors.outline, stringResource(R.string.status_skipped))
        DoseStatus.MISSED -> Triple(Icons.Rounded.Close, colors.error, stringResource(R.string.status_missed))
        DoseStatus.DUE, DoseStatus.UPCOMING -> Triple(Icons.Rounded.Schedule, colors.onSurfaceVariant, stringResource(R.string.history_status_pending))
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PillBadge(dose.medication.colorIndex, dose.medication.unit, size = 40.dp, dimmed = status == DoseStatus.SKIPPED)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dose.medication.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    res.windowRange(dose.window.startMinute, dose.window.endMinute) + " · " +
                        res.doseLine(dose.medication, dose.window.amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
            }
            if (editable) {
                IconButton(onClick = onToggle) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (status == DoseStatus.TAKEN) colors.primary else Color.Transparent)
                            .border(2.dp, if (status == DoseStatus.TAKEN) colors.primary else colors.outline, CircleShape),
                    ) {
                        Icon(
                            icon,
                            contentDescription = stringResource(R.string.action_take),
                            tint = if (status == DoseStatus.TAKEN) colors.onPrimary else tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
