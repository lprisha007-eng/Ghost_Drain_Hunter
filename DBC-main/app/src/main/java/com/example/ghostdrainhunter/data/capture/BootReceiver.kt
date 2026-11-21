package com.example.ghostdrainhunter.data.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.ghostdrainhunter.GhostDrainApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val app = context.applicationContext as? GhostDrainApplication ?: return
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = app.container.settingsStore.settingsFlow.first()
                    if (settings.isMonitoringUserEnabled) {
                        val serviceIntent = Intent(context, CaptureService::class.java).apply {
                            action = CaptureService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
