package com.example.ghostdrainhunter.data.debug

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.data.db.ScreenOffSessionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DebugSeeder(private val sessionDao: ScreenOffSessionDao) {

    suspend fun seedSampleData() = withContext(Dispatchers.IO) {
        // Clear previous sessions before seeding
        sessionDao.clearAll()

        val now = System.currentTimeMillis()
        val sessions = mutableListOf<ScreenOffSession>()

        // Generate 14 days of sessions backwards from today
        for (dayOffset in 13 downTo 0) {
            val baseCal = Calendar.getInstance().apply {
                timeInMillis = now - TimeUnit.DAYS.toMillis(dayOffset.toLong())
            }

            // 1. Long Night Session (e.g., 23:00 to 07:00, 8 hours)
            val nightStart = (baseCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val nightEnd = nightStart + TimeUnit.HOURS.toMillis(8)
            if (nightEnd <= now) {
                val isHighDrainNight = dayOffset % 3 == 0
                val startPct = 85
                val lostPct = if (isHighDrainNight) 16 else 5
                val endPct = (startPct - lostPct).coerceAtLeast(0)

                sessions.add(
                    ScreenOffSession(
                        startTime = nightStart,
                        startPercent = startPct,
                        endTime = nightEnd,
                        endPercent = endPct,
                        wasCharging = false,
                        status = ScreenOffSession.STATUS_CLOSED
                    )
                )
            }

            // 2. Daytime Work/Pocket Session (e.g., 10:30 to 12:30, 2 hours)
            val dayStart = (baseCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val dayEnd = dayStart + TimeUnit.HOURS.toMillis(2)
            if (dayEnd <= now) {
                val isHighDrain = dayOffset % 2 == 1
                val startPct = 70
                val lostPct = if (isHighDrain) 8 else 3
                val endPct = startPct - lostPct

                sessions.add(
                    ScreenOffSession(
                        startTime = dayStart,
                        startPercent = startPct,
                        endTime = dayEnd,
                        endPercent = endPct,
                        wasCharging = false,
                        status = ScreenOffSession.STATUS_CLOSED
                    )
                )
            }

            // 3. Too-short session (e.g., 8 minutes, excluded as TOO_SHORT)
            val shortStart = (baseCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 14)
                set(Calendar.MINUTE, 10)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val shortEnd = shortStart + TimeUnit.MINUTES.toMillis(8)
            if (shortEnd <= now) {
                sessions.add(
                    ScreenOffSession(
                        startTime = shortStart,
                        startPercent = 55,
                        endTime = shortEnd,
                        endPercent = 54,
                        wasCharging = false,
                        status = ScreenOffSession.STATUS_CLOSED
                    )
                )
            }

            // 4. Charged session (plugged in 19:00 to 20:30)
            val chargedStart = (baseCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val chargedEnd = chargedStart + TimeUnit.MINUTES.toMillis(90)
            if (chargedEnd <= now) {
                sessions.add(
                    ScreenOffSession(
                        startTime = chargedStart,
                        startPercent = 40,
                        endTime = chargedEnd,
                        endPercent = 90,
                        wasCharging = true,
                        status = ScreenOffSession.STATUS_CLOSED
                    )
                )
            }

            // 5. Afternoon Idle session (15:00 to 18:00, 3 hours)
            val afternoonStart = (baseCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 15)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val afternoonEnd = afternoonStart + TimeUnit.HOURS.toMillis(3)
            if (afternoonEnd <= now) {
                val startPct = 65
                val lostPct = 4
                val endPct = startPct - lostPct

                sessions.add(
                    ScreenOffSession(
                        startTime = afternoonStart,
                        startPercent = startPct,
                        endTime = afternoonEnd,
                        endPercent = endPct,
                        wasCharging = false,
                        status = ScreenOffSession.STATUS_CLOSED
                    )
                )
            }
        }

        // Insert all sessions
        for (session in sessions) {
            sessionDao.insert(session)
        }
    }
}
