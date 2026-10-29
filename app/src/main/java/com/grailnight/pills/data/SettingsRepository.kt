package com.grailnight.pills.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    /** How often to repeat a reminder while the window is open; 0 disables repeats. */
    val repeatMinutes: Int = 30,
    val snoozeMinutes: Int = 15,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        prefs.edit {
            putInt(KEY_REPEAT, updated.repeatMinutes)
            putInt(KEY_SNOOZE, updated.snoozeMinutes)
        }
        _settings.value = updated
    }

    private fun read() = Settings(
        repeatMinutes = prefs.getInt(KEY_REPEAT, Settings().repeatMinutes),
        snoozeMinutes = prefs.getInt(KEY_SNOOZE, Settings().snoozeMinutes),
    )

    private companion object {
        const val KEY_REPEAT = "repeat_minutes"
        const val KEY_SNOOZE = "snooze_minutes"
    }
}
