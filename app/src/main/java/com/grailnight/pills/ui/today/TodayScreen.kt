package com.grailnight.pills.ui.today

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grailnight.pills.R
import com.grailnight.pills.domain.Dose
import com.grailnight.pills.domain.DoseStatus
import com.grailnight.pills.ui.AppViewModels
import com.grailnight.pills.ui.components.EmptyState
import com.grailnight.pills.ui.components.PillBadge
import com.grailnight.pills.util.RU
import com.grailnight.pills.util.amount
import com.grailnight.pills.util.doseLine
import com.grailnight.pills.util.duration
import com.grailnight.pills.util.formatTime
import com.grailnight.pills.util.ruPlural
import com.grailnight.pills.util.windowRange
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    onAddMedication: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Dose?>(null) }

    TodayContent(
        state = state,
        onAddMedication = onAddMedication,
        onToggle = viewModel::toggleTaken,
        onOpen = { selected = it },
    )

    selected?.let { dose ->
        DoseSheet(
            dose = dose,
            now = state.now,
            onDismiss = { selected = null },
            onTake = { viewModel.markTaken(dose); selected = null },
            onSkip = { viewModel.skip(dose); selected = null },
            onReset = { viewModel.reset(dose); selected = null },
        )
    }
}

@Composable
fun TodayContent(
    state: TodayState,
    onAddMedication: () -> Unit,
    onToggle: (Dose) -> Unit,
    onOpen: (Dose) -> Unit,
) {
    if (state.loading) return
    if (!state.hasMedications) {
        EmptyState(
            title = stringResource(R.string.today_empty_title),
            text = stringResource(R.string.today_empty_hint),
        ) {
            Button(onClick = onAddMedication) { Text(stringResource(R.string.today_add_medication)) }
        }
        return
    }

    val groups = state.doses.groupBy { it.start to it.end }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = rememberLazyListState()

    // Bring the first window that still needs attention into view when the screen opens.
    LaunchedEffect(Unit) {
        var target = if (state.lowStock.isNotEmpty()) 3 else 2
        for (doses in groups.values) {
            if (doses.any { it.status(state.now) in NEEDS_ACTION }) {
                val visible = snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
                if (target > visible.last().index - 1) listState.scrollToItem(target)
                break
            }
            target += 1 + doses.size
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusBar + 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { TodayHeader(state.now.toLocalDate()) }
        item(key = "summary") { SummaryCard(state) }
        if (state.lowStock.isNotEmpty()) {
            item(key = "stock") { LowStockCard(state) }
        }
        groups.forEach { (range, doses) ->
            val (start, end) = range
            item(key = "group-$start-$end") {
                GroupHeader(start, end, active = state.now >= start && state.now < end)
            }
            items(doses, key = { it.key }) { dose ->
                DoseCard(
                    dose = dose,
                    status = dose.status(state.now),
                    now = state.now,
                    onToggle = { onToggle(dose) },
                    onClick = { onOpen(dose) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private val NEEDS_ACTION = setOf(DoseStatus.DUE, DoseStatus.MISSED, DoseStatus.UPCOMING)

@Composable
private fun TodayHeader(date: LocalDate) {
    val formatted = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)).replaceFirstChar { it.titlecase(RU) }
    }
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.displaySmall)
        Text(
            formatted,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryCard(state: TodayState) {
    val total = state.doses.size
    val taken = state.taken
    val remaining = state.remaining
    val due = remaining.filter { it.status(state.now) == DoseStatus.DUE }
    val missed = state.doses.count { it.status(state.now) == DoseStatus.MISSED }

    val (title, subtitle) = when {
        total == 0 -> stringResource(R.string.today_nothing_today) to stringResource(R.string.today_nothing_today_hint)
        due.isNotEmpty() -> stringResource(R.string.today_due_title) to
            due.joinToString(", ") { it.medication.name }
        remaining.isNotEmpty() -> stringResource(R.string.today_next, formatTime(remaining.first().start.toLocalTime())) to
            LocalContext.current.resources.ruPlural(R.array.today_left, remaining.size)
        missed > 0 -> stringResource(R.string.today_missed_title) to stringResource(R.string.today_missed_hint)
        else -> stringResource(R.string.today_all_done) to
            stringResource(R.string.today_all_done_hint, taken, total)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = if (total == 0) 1f else taken.toFloat() / total,
                    size = 88.dp,
                    color = MaterialTheme.colorScheme.primary,
                    track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                )
                if (total == 0) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(36.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.progress_of, taken, total), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, size: Dp, color: Color, track: Color) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "progress",
    )
    Canvas(Modifier.size(size)) {
        val stroke = 10.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (animated > 0f) {
            drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun LowStockCard(state: TodayState) {
    val res = LocalContext.current.resources
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.Inventory2, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.today_low_stock_title), style = MaterialTheme.typography.titleSmall)
                state.lowStock.forEach {
                    Text(
                        stringResource(R.string.today_low_stock_item, it.name, res.amount(it.stock ?: 0.0, it.unit)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(start: LocalDateTime, end: LocalDateTime, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    ) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.window_range, formatTime(start.toLocalTime()), formatTime(end.toLocalTime())),
            style = MaterialTheme.typography.titleSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (active) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.status_now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DoseCard(
    dose: Dose,
    status: DoseStatus,
    now: LocalDateTime,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val done = status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED
    val container by animateColorAsState(
        when (status) {
            DoseStatus.DUE -> MaterialTheme.colorScheme.secondaryContainer
            DoseStatus.TAKEN, DoseStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        label = "container",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = if (done) 10.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillBadge(dose.medication.colorIndex, dose.medication.unit, dimmed = done, size = if (done) 40.dp else 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dose.medication.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    res.doseLine(dose.medication, dose.window.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dose.medication.note.isNotBlank() && !done) {
                    Text(
                        dose.medication.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusLine(dose, status, now)
            }
            Spacer(Modifier.width(12.dp))
            CheckButton(checked = status == DoseStatus.TAKEN, highlighted = status == DoseStatus.DUE, onClick = onToggle)
        }
    }
}

@Composable
private fun StatusLine(dose: Dose, status: DoseStatus, now: LocalDateTime) {
    val res = LocalContext.current.resources
    val colors = MaterialTheme.colorScheme
    val (text, color) = when (status) {
        DoseStatus.TAKEN -> {
            val takenAt = dose.log?.loggedAt?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).toLocalTime()
            }
            stringResource(R.string.status_taken_at, takenAt?.let(::formatTime).orEmpty()) to colors.primary
        }
        DoseStatus.SKIPPED -> stringResource(R.string.status_skipped) to colors.outline
        DoseStatus.DUE -> stringResource(R.string.status_due_until, formatTime(dose.end.toLocalTime())) to colors.primary
        DoseStatus.UPCOMING -> stringResource(R.string.status_upcoming_in, res.duration(Duration.between(now, dose.start))) to
            colors.onSurfaceVariant
        DoseStatus.MISSED -> stringResource(R.string.status_missed) to colors.error
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun CheckButton(checked: Boolean, highlighted: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) colors.primary else Color.Transparent, label = "fill")
    val border by animateColorAsState(
        when {
            checked -> colors.primary
            highlighted -> colors.primary
            else -> colors.outline
        },
        label = "border",
    )
    val scale by animateFloatAsState(
        if (checked) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )
    Surface(
        onClick = {
            val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(feedback)
            onClick()
        },
        shape = CircleShape,
        color = fill,
        border = BorderStroke(2.dp, border),
        modifier = Modifier
            .size(48.dp)
            .scale(scale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.action_take),
                tint = if (checked) colors.onPrimary else border.copy(alpha = 0.6f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseSheet(
    dose: Dose,
    now: LocalDateTime,
    onDismiss: () -> Unit,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
) {
    val res = LocalContext.current.resources
    val status = dose.status(now)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillBadge(dose.medication.colorIndex, dose.medication.unit, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(dose.medication.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        res.doseLine(dose.medication, dose.window.amount) + " · " +
                            res.windowRange(dose.window.startMinute, dose.window.endMinute),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (dose.medication.note.isNotBlank()) {
                Text(
                    dose.medication.note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            if (status != DoseStatus.TAKEN) {
                SheetAction(Icons.Rounded.Check, stringResource(R.string.action_take), onTake)
            }
            if (status != DoseStatus.SKIPPED) {
                SheetAction(Icons.Rounded.DoNotDisturbOn, stringResource(R.string.action_skip), onSkip)
            }
            if (dose.log != null) {
                SheetAction(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.action_reset), onReset)
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.titleMedium) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
}
