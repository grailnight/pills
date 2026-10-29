package com.grailnight.pills.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PillsDao {

    @Transaction
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    fun observeMedications(): Flow<List<MedicationWithWindows>>

    @Transaction
    @Query("SELECT * FROM medications")
    suspend fun getMedications(): List<MedicationWithWindows>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedication(id: Long): MedicationWithWindows?

    @Query("SELECT * FROM intake_windows WHERE id = :id")
    suspend fun getWindow(id: Long): IntakeWindow?

    @Query("SELECT * FROM intake_logs WHERE day BETWEEN :fromDay AND :toDay")
    fun observeLogs(fromDay: Long, toDay: Long): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_logs WHERE windowId = :windowId AND day BETWEEN :fromDay AND :toDay")
    suspend fun getLogs(windowId: Long, fromDay: Long, toDay: Long): List<IntakeLog>

    @Query("SELECT * FROM intake_logs WHERE windowId = :windowId AND day = :day")
    suspend fun getLog(windowId: Long, day: Long): IntakeLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: IntakeLog)

    @Delete
    suspend fun deleteLog(log: IntakeLog)

    @Insert
    suspend fun insertMedication(medication: Medication): Long

    @Update
    suspend fun updateMedication(medication: Medication)

    @Query("UPDATE medications SET stock = :stock WHERE id = :id")
    suspend fun updateStock(id: Long, stock: Double?)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedication(id: Long)

    @Insert
    suspend fun insertWindow(window: IntakeWindow): Long

    @Update
    suspend fun updateWindow(window: IntakeWindow)

    @Query("DELETE FROM intake_windows WHERE id IN (:ids)")
    suspend fun deleteWindows(ids: List<Long>)

    @Query("SELECT id FROM intake_windows WHERE medicationId = :medicationId")
    suspend fun windowIds(medicationId: Long): List<Long>

    /**
     * Saves a medication together with its windows. Existing windows keep their ids so the
     * intake log stays attached to them; windows missing from [windows] are removed.
     * Returns the medication id and the ids of the removed windows.
     */
    @Transaction
    suspend fun saveMedication(medication: Medication, windows: List<IntakeWindow>): Pair<Long, List<Long>> {
        val medicationId = if (medication.id == 0L) {
            insertMedication(medication)
        } else {
            updateMedication(medication)
            medication.id
        }
        val keptIds = windows.filter { it.id != 0L }.map { it.id }.toSet()
        val removedIds = windowIds(medicationId).filterNot { it in keptIds }
        if (removedIds.isNotEmpty()) deleteWindows(removedIds)
        for (window in windows) {
            val owned = window.copy(medicationId = medicationId)
            if (owned.id == 0L) insertWindow(owned) else updateWindow(owned)
        }
        return medicationId to removedIds
    }
}
