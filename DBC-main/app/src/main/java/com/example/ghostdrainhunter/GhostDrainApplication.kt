package com.example.ghostdrainhunter

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class GhostDrainApplication : Application() {
    lateinit var container: AppContainer
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        appScope.launch {
            val settings = container.settingsStore.settingsFlow.first()
            val now = System.currentTimeMillis()

            // One-time clean start: delete all existing sessions and set trackingStartTime to now
            if (!settings.isCleanStartDone) {
                container.sessionDao.clearAll()
                container.settingsStore.startFresh(now)
            } else if (settings.trackingStartTime == 0L) {
                container.settingsStore.setTrackingStartTime(now)
            }

            // Prune sessions older than 90 days on app start
            val cutoff = now - TimeUnit.DAYS.toMillis(90)
            container.sessionDao.pruneSessionsOlderThan(cutoff)
        }
    }
}
