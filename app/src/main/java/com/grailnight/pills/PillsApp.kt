package com.grailnight.pills

import android.app.Application
import android.content.Context
import com.grailnight.pills.data.PillsDatabase
import com.grailnight.pills.data.PillsRepository
import com.grailnight.pills.data.SettingsRepository
import com.grailnight.pills.reminders.Notifier
import com.grailnight.pills.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PillsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.appScope.launch { container.repository.rescheduleAll() }
    }
}

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context)
    val notifier = Notifier(context)
    private val database = PillsDatabase.create(context)
    val scheduler = ReminderScheduler(context)
    val repository = PillsRepository(database.dao(), settings, scheduler, notifier)
}

val Context.container: AppContainer
    get() = (applicationContext as PillsApp).container
