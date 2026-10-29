package com.grailnight.pills.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grailnight.pills.R
import com.grailnight.pills.data.PillUnit
import com.grailnight.pills.ui.theme.medicationColor

/** A round badge with a drawn pill, capsule or drop in the medication's color. */
@Composable
fun PillBadge(
    colorIndex: Int,
    unit: PillUnit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    dimmed: Boolean = false,
) {
    val target = medicationColor(colorIndex)
    val color by animateColorAsState(
        if (dimmed) lerp(target, MaterialTheme.colorScheme.outline, 0.6f) else target,
        label = "pillColor",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        PillGlyph(unit = unit, color = color, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun PillGlyph(unit: PillUnit, color: Color, modifier: Modifier = Modifier) {
    when (unit) {
        PillUnit.ML, PillUnit.DROP -> Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = color, modifier = modifier)
        // A round tablet seen slightly from above: darker rim below, score line on top.
        PillUnit.TABLET -> Canvas(modifier) {
            val width = size.minDimension * 0.92f
            val faceHeight = width * 0.62f
            val rim = width * 0.14f
            val left = center.x - width / 2
            val top = center.y - (faceHeight + rim) / 2
            drawOval(lerp(color, Color.Black, 0.22f), Offset(left, top + rim), Size(width, faceHeight))
            drawRect(lerp(color, Color.Black, 0.22f), Offset(left, top + faceHeight / 2), Size(width, rim))
            drawOval(color, Offset(left, top), Size(width, faceHeight))
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(left + width * 0.2f, top + faceHeight / 2),
                end = Offset(left + width * 0.8f, top + faceHeight / 2),
                strokeWidth = faceHeight * 0.1f,
                cap = StrokeCap.Round,
            )
        }
        PillUnit.CAPSULE, PillUnit.PIECE -> Canvas(modifier) {
            val length = size.minDimension
            val thickness = length * 0.44f
            val topLeft = Offset(center.x - length / 2, center.y - thickness / 2)
            val capsule = Size(length, thickness)
            val corner = CornerRadius(thickness / 2)
            rotate(-45f) {
                drawRoundRect(lerp(color, Color.White, 0.55f), topLeft, capsule, corner)
                clipRect(right = center.x) {
                    drawRoundRect(color, topLeft, capsule, corner)
                }
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(topLeft.x + thickness * 0.45f, center.y - thickness * 0.2f),
                    end = Offset(center.x - thickness * 0.3f, center.y - thickness * 0.2f),
                    strokeWidth = thickness * 0.14f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun AmountStepper(
    value: Double,
    label: String,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 0.5,
    min: Double = 0.5,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_less))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        FilledTonalIconButton(onClick = { onChange(value + step) }) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_more))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillBadge(colorIndex = 1, unit = PillUnit.TABLET, size = 64.dp)
            PillBadge(colorIndex = 3, unit = PillUnit.CAPSULE, size = 88.dp)
            PillBadge(colorIndex = 5, unit = PillUnit.DROP, size = 64.dp)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        action()
    }
}
