package com.grailnight.pills.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Medication::class, IntakeWindow::class, IntakeLog::class],
    version = 1,
    exportSchema = true,
)
abstract class PillsDatabase : RoomDatabase() {
    abstract fun dao(): PillsDao

    companion object {
        fun create(context: Context): PillsDatabase =
            Room.databaseBuilder(context, PillsDatabase::class.java, "pills.db").build()
    }
}
