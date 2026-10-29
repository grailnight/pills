package com.grailnight.pills.util

import android.content.res.Resources
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.grailnight.pills.R
import com.grailnight.pills.data.Medication
import com.grailnight.pills.data.PillUnit
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@get:StringRes
val PillUnit.shortLabel: Int
    get() = when (this) {
        PillUnit.TABLET -> R.string.unit_tablet_short
        PillUnit.CAPSULE -> R.string.unit_capsule_short
        PillUnit.ML -> R.string.unit_ml_short
        PillUnit.DROP -> R.string.unit_drop_short
        PillUnit.PIECE -> R.string.unit_piece_short
    }

@get:StringRes
val PillUnit.label: Int
    get() = when (this) {
        PillUnit.TABLET -> R.string.unit_tablet
        PillUnit.CAPSULE -> R.string.unit_capsule
        PillUnit.ML -> R.string.unit_ml
        PillUnit.DROP -> R.string.unit_drop
        PillUnit.PIECE -> R.string.unit_piece
    }

/**
 * Picks one of three forms (one, few, many) by Russian plural rules. The app is Russian-only,
 * so this must not depend on the device language the way `<plurals>` does.
 */
fun Resources.ruPlural(@ArrayRes forms: Int, count: Int): String =
    getStringArray(forms)[russianPluralForm(count)].format(count)

/** 0 for "one" (1, 21, 101), 1 for "few" (2–4, 22–24), 2 for "many" (0, 5–20, 25…). */
fun russianPluralForm(count: Int): Int = when {
    count % 10 == 1 && count % 100 != 11 -> 0
    count % 10 in 2..4 && count % 100 !in 12..14 -> 1
    else -> 2
}

/** 1 → "1", 0.5 → "0,5", 2.25 → "2,25". */
fun formatNumber(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').replace('.', ',')
    }
}

fun Resources.amount(value: Double, unit: PillUnit): String =
    "${formatNumber(value)} ${getString(unit.shortLabel)}"

/** "2 таб. · 500 мг" */
fun Resources.doseLine(medication: Medication, amount: Double): String =
    listOf(amount(amount, medication.unit), medication.dosage.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

fun formatMinuteOfDay(minute: Int): String =
    LocalTime.of(minute / 60 % 24, minute % 60).format(timeFormatter)

fun formatTime(time: LocalTime): String = time.format(timeFormatter)

fun Resources.windowRange(startMinute: Int, endMinute: Int): String =
    getString(R.string.window_range, formatMinuteOfDay(startMinute), formatMinuteOfDay(endMinute))

fun Resources.daysSummary(mask: Int): String {
    val names = getStringArray(R.array.weekdays_short)
    return when (mask and Medication.ALL_DAYS) {
        Medication.ALL_DAYS -> getString(R.string.days_every)
        0b0011111 -> getString(R.string.days_weekdays)
        0b1100000 -> getString(R.string.days_weekends)
        0 -> getString(R.string.days_none)
        else -> (0..6).filter { mask and (1 shl it) != 0 }.joinToString(", ") { names[it] }
    }
}

/** "2 ч 15 мин", "40 мин" — rounded up so it never reads "0 мин" before the start. */
fun Resources.duration(duration: Duration): String {
    val totalMinutes = ((duration.seconds + 59) / 60).coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> getString(R.string.duration_minutes, minutes)
        minutes == 0L -> getString(R.string.duration_hours, hours)
        else -> getString(R.string.duration_hours_minutes, hours, minutes)
    }
}
