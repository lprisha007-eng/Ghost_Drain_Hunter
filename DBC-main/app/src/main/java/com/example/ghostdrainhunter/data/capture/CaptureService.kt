package com.example.ghostdrainhunter.data.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.ghostdrainhunter.GhostDrainApplication
import com.example.ghostdrainhunter.MainActivity
import com.example.ghostdrainhunter.R
import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.AnalysisStatus
import com.example.ghostdrainhunter.domain.SessionAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CaptureService : Service() {

    companion object {
        const val ACTION_START = "com.example.ghostdrainhunter.action.START"
        const val ACTION_STOP = "com.example.ghostdrainhunter.action.STOP"

        const val CHANNEL_ID = "ghost_drain_capture_channel"
        const val NOTIFICATION_ID = 1001

        // 10 seconds noise filter threshold
        const val MIN_DISCARD_DURATION_MILLIS = 10_000L

        // 48 hours threshold for an implausible open session left after crash/reboot
        private const val MAX_PLAUSIBLE_SESSION_MILLIS = 48 * 3600 * 1000L

        private val _isMonitoring = MutableStateFlow(false)
        val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isReceiverRegistered = false

    private val screenAndPowerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_POWER_CONNECTED -> handlePowerChanged(true)
                Intent.ACTION_POWER_DISCONNECTED -> handlePowerChanged(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        startMonitoringForeground()
        return START_STICKY
    }

    private fun startMonitoringForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        registerDynamicReceivers()
        _isMonitoring.value = true
    }

    private fun stopMonitoring() {
        unregisterDynamicReceivers()
        _isMonitoring.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerDynamicReceivers() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(screenAndPowerReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterDynamicReceivers() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenAndPowerReceiver)
            } catch (_: IllegalArgumentException) {
            }
            isReceiverRegistered = false
        }
    }

    private fun handleScreenOff() {
        serviceScope.launch {
            val container = (application as GhostDrainApplication).container
            val sessionDao = container.sessionDao
            val batteryReader = container.batteryReader

            val now = System.currentTimeMillis()
            val batteryInfo = batteryReader.getBatteryInfo()

            // Check if there was an open session (missed screen-on event or crash)
            val openSession = sessionDao.getOpenSession()
            if (openSession != null) {
                val duration = now - openSession.startTime
                if (duration < MIN_DISCARD_DURATION_MILLIS) {
                    sessionDao.deleteSessionById(openSession.id)
                } else if (duration > MAX_PLAUSIBLE_SESSION_MILLIS || duration < 0) {
                    sessionDao.update(openSession.copy(status = ScreenOffSession.STATUS_DISCARDED))
                } else {
                    sessionDao.closeSession(
                        id = openSession.id,
                        endTime = now,
                        endPercent = batteryInfo.percentage,
                        wasCharging = openSession.wasCharging || batteryInfo.isCharging,
                        status = ScreenOffSession.STATUS_CLOSED
                    )
                }
            }

            // Create new OPEN session
            val newSession = ScreenOffSession(
                startTime = now,
                startPercent = batteryInfo.percentage,
                endTime = null,
                endPercent = null,
                wasCharging = batteryInfo.isCharging,
                status = ScreenOffSession.STATUS_OPEN
            )
            sessionDao.insert(newSession)
        }
    }

    private fun handleScreenOn() {
        serviceScope.launch {
            val container = (application as GhostDrainApplication).container
            val sessionDao = container.sessionDao
            val batteryReader = container.batteryReader
            val settingsStore = container.settingsStore
            val alertNotifier = container.drainAlertNotifier

            val now = System.currentTimeMillis()
            val batteryInfo = batteryReader.getBatteryInfo()

            val openSession = sessionDao.getOpenSession() ?: return@launch
            val duration = now - openSession.startTime

            // Noise filter: Discard any session shorter than 10 seconds (does not create a row)
            if (duration < MIN_DISCARD_DURATION_MILLIS) {
                sessionDao.deleteSessionById(openSession.id)
                return@launch
            }

            if (duration > MAX_PLAUSIBLE_SESSION_MILLIS || duration < 0) {
                sessionDao.update(openSession.copy(status = ScreenOffSession.STATUS_DISCARDED))
                return@launch
            }

            val wasChargingTotal = openSession.wasCharging || batteryInfo.isCharging
            sessionDao.closeSession(
                id = openSession.id,
                endTime = now,
                endPercent = batteryInfo.percentage,
                wasCharging = wasChargingTotal,
                status = ScreenOffSession.STATUS_CLOSED
            )

            // Prune sessions older than 90 days when a session closes
            val pruneCutoff = now - (90L * 24 * 3600 * 1000L)
            sessionDao.pruneSessionsOlderThan(pruneCutoff)

            // Analyze and notify if appropriate
            val closedSession = openSession.copy(
                endTime = now,
                endPercent = batteryInfo.percentage,
                wasCharging = wasChargingTotal,
                status = ScreenOffSession.STATUS_CLOSED
            )
            val settings = settingsStore.settingsFlow.first()

            // Only alert if within active tracking start time
            if (closedSession.startTime >= settings.trackingStartTime) {
                val analysis = SessionAnalyzer.analyze(
                    session = closedSession,
                    thresholdPerHour = settings.thresholdPerHour,
                    minSessionMinutes = settings.minSessionMinutes
                )

                if (analysis.status == AnalysisStatus.VALID &&
                    analysis.isFlagged &&
                    settings.alertsEnabled
                ) {
                    alertNotifier.postDrainAlert(analysis)
                }
            }
        }
    }

    private fun handlePowerChanged(isConnected: Boolean) {
        if (isConnected) {
            serviceScope.launch {
                val container = (application as GhostDrainApplication).container
                val sessionDao = container.sessionDao
                val openSession = sessionDao.getOpenSession()
                if (openSession != null && !openSession.wasCharging) {
                    sessionDao.update(openSession.copy(wasCharging = true))
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitoring_notification_title))
            .setContentText(getString(R.string.monitoring_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        unregisterDynamicReceivers()
        _isMonitoring.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
