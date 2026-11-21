package com.example.ghostdrainhunter.domain

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.ghostdrainhunter.MainActivity
import com.example.ghostdrainhunter.R
import java.util.Locale

class DrainAlertNotifier(private val context: Context) {

    companion object {
        const val ALERT_CHANNEL_ID = "ghost_drain_alert_channel"
        const val ALERT_NOTIFICATION_ID_BASE = 2000
    }

    init {
        createAlertNotificationChannel()
    }

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.alert_channel_name)
            val descriptionText = context.getString(R.string.alert_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(ALERT_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun postDrainAlert(analysis: SessionAnalysis) {
        if (analysis.status != AnalysisStatus.VALID || !analysis.isFlagged) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val title = context.getString(R.string.alert_notification_title)
        val durationFormatted = formatDuration(analysis.durationMillis)
        val text = if (analysis.durationHours >= 2.0) {
            context.getString(
                R.string.alert_notification_text_long,
                analysis.percentLost,
                durationFormatted,
                String.format(Locale.US, "%.1f", analysis.ratePerHour)
            )
        } else {
            context.getString(
                R.string.alert_notification_text_short,
                analysis.percentLost,
                durationFormatted
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            analysis.session.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationId = ALERT_NOTIFICATION_ID_BASE + (analysis.session.id % 1000).toInt()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Degrade gracefully if permission was revoked
        }
    }

    fun formatDuration(durationMillis: Long): String {
        val totalMinutes = (durationMillis / 60_000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours == 0 -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
            minutes == 0 -> "$hours ${if (hours == 1) "hour" else "hours"}"
            else -> {
                val hourPart = "$hours ${if (hours == 1) "hour" else "hours"}"
                val minutePart = "$minutes ${if (minutes == 1) "minute" else "minutes"}"
                "$hourPart $minutePart"
            }
        }
    }
}
