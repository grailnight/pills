package com.grailnight.pills.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.DayOfWeek

/** What a single unit of the medication is; drives the label next to amounts and the icon. */
enum class PillUnit { TABLET, CAPSULE, ML, DROP, PIECE }

enum class LogStatus { TAKEN, SKIPPED }

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val note: String = "",
    val unit: PillUnit = PillUnit.TABLET,
    val colorIndex: Int = 0,
    /** Bit 0 is Monday, bit 6 is Sunday. */
    val daysMask: Int = ALL_DAYS,
    val remindersEnabled: Boolean = true,
    /** Units left in the package, or null when stock is not tracked. */
    val stock: Double? = null,
    val lowStockThreshold: Double = 5.0,
    /** When the medication was added (epoch millis); no doses are expected before it. */
    val createdAt: Long,
) {
    fun isScheduledOn(day: DayOfWeek): Boolean = daysMask and (1 shl (day.value - 1)) != 0

    val isLowOnStock: Boolean
        get() = stock != null && stock <= lowStockThreshold

    companion object {
        const val ALL_DAYS = 0b1111111
    }
}

/**
 * A daily window in which a dose should be taken. When [endMinute] is not after
 * [startMinute] the window crosses midnight and belongs to the day it starts on.
 */
@Entity(
    tableName = "intake_windows",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationId")],
)
data class IntakeWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val startMinute: Int,
    val endMinute: Int,
    val amount: Double = 1.0,
    /** When the window was added (epoch millis); windows that ended before it are not expected. */
    val createdAt: Long,
)

@Entity(
    tableName = "intake_logs",
    foreignKeys = [
        ForeignKey(
            entity = IntakeWindow::class,
            parentColumns = ["id"],
            childColumns = ["windowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["windowId", "day"], unique = true), Index("day")],
)
data class IntakeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val windowId: Long,
    /** Epoch day the window starts on. */
    val day: Long,
    val status: LogStatus,
    val amount: Double,
    val loggedAt: Long,
)

data class MedicationWithWindows(
    @Embedded val medication: Medication,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val windows: List<IntakeWindow>,
) {
    val sortedWindows: List<IntakeWindow>
        get() = windows.sortedBy { it.startMinute }
}
