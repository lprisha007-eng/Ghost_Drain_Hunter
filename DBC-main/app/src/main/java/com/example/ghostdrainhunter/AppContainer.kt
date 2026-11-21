package com.example.ghostdrainhunter

import android.content.Context
import com.example.ghostdrainhunter.data.capture.BatteryReader
import com.example.ghostdrainhunter.data.db.AppDatabase
import com.example.ghostdrainhunter.data.db.ScreenOffSessionDao
import com.example.ghostdrainhunter.data.debug.DebugSeeder
import com.example.ghostdrainhunter.data.settings.SettingsStore
import com.example.ghostdrainhunter.domain.DrainAlertNotifier

interface AppContainer {
    val database: AppDatabase
    val sessionDao: ScreenOffSessionDao
    val batteryReader: BatteryReader
    val settingsStore: SettingsStore
    val drainAlertNotifier: DrainAlertNotifier
    val debugSeeder: DebugSeeder
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val sessionDao: ScreenOffSessionDao by lazy {
        database.sessionDao()
    }

    override val batteryReader: BatteryReader by lazy {
        BatteryReader(context)
    }

    override val settingsStore: SettingsStore by lazy {
        SettingsStore(context)
    }

    override val drainAlertNotifier: DrainAlertNotifier by lazy {
        DrainAlertNotifier(context)
    }

    override val debugSeeder: DebugSeeder by lazy {
        DebugSeeder(sessionDao)
    }
}
