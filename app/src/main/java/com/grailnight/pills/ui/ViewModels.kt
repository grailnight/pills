package com.grailnight.pills.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grailnight.pills.AppContainer
import com.grailnight.pills.PillsApp
import com.grailnight.pills.ui.edit.EditViewModel
import com.grailnight.pills.ui.history.HistoryViewModel
import com.grailnight.pills.ui.meds.MedsViewModel
import com.grailnight.pills.ui.settings.SettingsViewModel
import com.grailnight.pills.ui.today.TodayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object AppViewModels {
    val Factory = viewModelFactory {
        initializer { TodayViewModel(container()) }
        initializer { MedsViewModel(container()) }
        initializer { EditViewModel(createSavedStateHandle(), container()) }
        initializer { HistoryViewModel(container()) }
        initializer { SettingsViewModel(container()) }
    }

    private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as PillsApp).container
}

/** Emits the current time now and then at the start of every minute. */
fun minuteTicker(): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now)
        val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        delay(ChronoUnit.MILLIS.between(now, nextMinute) + 50)
    }
}
