package com.example.ghostdrainhunter

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.ComparisonVerdict
import com.example.ghostdrainhunter.domain.PatternDetector
import com.example.ghostdrainhunter.domain.PatternResult
import com.example.ghostdrainhunter.domain.TimeAllocator
import com.example.ghostdrainhunter.domain.TodayVsYesterdayResult
import com.example.ghostdrainhunter.domain.TrendCalculator
import com.example.ghostdrainhunter.domain.WeekVsWeekResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class PatternAndTrendTest {

    private fun getMidnight(dayOffsetFromBase: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffsetFromBase)
        }.timeInMillis
    }

    @Test
    fun testSessionCrossingMidnight_splitsCorrectly() {
        val midnight = getMidnight(0)
        // Session from 23:00 (day -1) to 02:00 (day 0), 3 hours, 6% lost
        val start = midnight - TimeUnit.HOURS.toMillis(1) // 23:00
        val end = midnight + TimeUnit.HOURS.toMillis(2)   // 02:00
        val session = ScreenOffSession(
            id = 1,
            startTime = start,
            startPercent = 80,
            endTime = end,
            endPercent = 74, // 6% lost
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val hourSlices = TimeAllocator.splitIntoHourSlices(session, percentLost = 6)
        assertEquals(3, hourSlices.size)

        // Hour 23 slice (1 hour, 2% lost)
        assertEquals(23, hourSlices[0].hourOfDay)
        assertEquals(1.0, hourSlices[0].durationHours, 0.001)
        assertEquals(2.0, hourSlices[0].lostPercent, 0.001)

        // Hour 0 slice (1 hour, 2% lost)
        assertEquals(0, hourSlices[1].hourOfDay)
        assertEquals(1.0, hourSlices[1].durationHours, 0.001)
        assertEquals(2.0, hourSlices[1].lostPercent, 0.001)

        // Hour 1 slice (1 hour, 2% lost)
        assertEquals(1, hourSlices[2].hourOfDay)
        assertEquals(1.0, hourSlices[2].durationHours, 0.001)
        assertEquals(2.0, hourSlices[2].lostPercent, 0.001)

        // Day slices
        val daySlices = TimeAllocator.splitIntoDaySlices(session, percentLost = 6)
        assertEquals(2, daySlices.size)
        // Day -1 has 1 hour and 2% lost
        assertEquals(1.0, daySlices[0].durationHours, 0.001)
        assertEquals(2.0, daySlices[0].lostPercent, 0.001)
        // Day 0 has 2 hours and 4% lost
        assertEquals(2.0, daySlices[1].durationHours, 0.001)
        assertEquals(4.0, daySlices[1].lostPercent, 0.001)
    }

    @Test
    fun testSessionInsideOneHour_createsSingleSlice() {
        val midnight = getMidnight(0)
        // 14:15 to 14:45 (30 minutes in hour 14)
        val start = midnight + TimeUnit.HOURS.toMillis(14) + TimeUnit.MINUTES.toMillis(15)
        val end = midnight + TimeUnit.HOURS.toMillis(14) + TimeUnit.MINUTES.toMillis(45)
        val session = ScreenOffSession(
            id = 2,
            startTime = start,
            startPercent = 70,
            endTime = end,
            endPercent = 69, // 1% lost
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val hourSlices = TimeAllocator.splitIntoHourSlices(session, percentLost = 1)
        assertEquals(1, hourSlices.size)
        assertEquals(14, hourSlices[0].hourOfDay)
        assertEquals(0.5, hourSlices[0].durationHours, 0.001)
        assertEquals(1.0, hourSlices[0].lostPercent, 0.001)
    }

    @Test
    fun testWindowWrappingFrom11PMTo1AM() {
        val midnight = getMidnight(0)
        val now = midnight + TimeUnit.HOURS.toMillis(12)

        // Sessions across 2 nights with high drain between 23:00 and 01:00
        val sessions = mutableListOf<ScreenOffSession>()
        for (i in 1..2) {
            val mid = midnight - TimeUnit.DAYS.toMillis(i.toLong())

            // Evening session (21:00 to 23:00, 2h losing 2% = 1.0%/hr)
            sessions.add(
                ScreenOffSession(
                    id = (i * 10).toLong(),
                    startTime = mid - TimeUnit.HOURS.toMillis(3), // 21:00
                    startPercent = 85,
                    endTime = mid - TimeUnit.HOURS.toMillis(1),   // 23:00
                    endPercent = 83, // 2% lost in 2h = 1.0%/hr
                    wasCharging = false,
                    status = ScreenOffSession.STATUS_CLOSED
                )
            )

            // High drain window (23:00 to 01:00, 2h losing 8% = 4.0%/hr)
            sessions.add(
                ScreenOffSession(
                    id = (i * 10 + 1).toLong(),
                    startTime = mid - TimeUnit.HOURS.toMillis(1), // 23:00
                    startPercent = 83,
                    endTime = mid + TimeUnit.HOURS.toMillis(1),   // 01:00
                    endPercent = 75, // 8% lost in 2h = 4.0%/hr
                    wasCharging = false,
                    status = ScreenOffSession.STATUS_CLOSED
                )
            )

            // Night session (01:00 to 03:00, 2h losing 2% = 1.0%/hr)
            sessions.add(
                ScreenOffSession(
                    id = (i * 10 + 2).toLong(),
                    startTime = mid + TimeUnit.HOURS.toMillis(1), // 01:00
                    startPercent = 75,
                    endTime = mid + TimeUnit.HOURS.toMillis(3),   // 03:00
                    endPercent = 73, // 2% lost in 2h = 1.0%/hr
                    wasCharging = false,
                    status = ScreenOffSession.STATUS_CLOSED
                )
            )
        }

        val result = PatternDetector.detectPattern(sessions, now = now)
        assertTrue(result is PatternResult.DetectedPattern)
        val pattern = result as PatternResult.DetectedPattern

        // Peak window: 23:00 to 01:00 (startHour = 23, endHour = 1)
        assertEquals(23, pattern.startHour)
        assertEquals(1, pattern.endHour)
        assertEquals(4.0, pattern.windowRate, 0.01)
    }

    @Test
    fun testTiesGoToEarliestWindow() {
        val midnight = getMidnight(0)
        val now = midnight + TimeUnit.HOURS.toMillis(12)

        // Window 1: 02:00 to 04:00 (2h, 6% lost = 3.0%/hr)
        val s1 = ScreenOffSession(
            id = 1,
            startTime = midnight - TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(2),
            startPercent = 80,
            endTime = midnight - TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(4),
            endPercent = 74,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        // Window 2: 14:00 to 16:00 (2h, 6% lost = 3.0%/hr)
        val s2 = ScreenOffSession(
            id = 2,
            startTime = midnight - TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(14),
            startPercent = 70,
            endTime = midnight - TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(16),
            endPercent = 64,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        // Lower baseline window: 08:00 to 10:00 (2h, 2% lost = 1.0%/hr)
        val s3 = ScreenOffSession(
            id = 3,
            startTime = midnight - TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(8),
            startPercent = 74,
            endTime = midnight - TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(10),
            endPercent = 72,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        // Tie between hour 2 (3.0%/hr) and hour 14 (3.0%/hr). Earliest should win!
        val result = PatternDetector.detectPattern(listOf(s1, s2, s3), now = now)
        assertTrue(result is PatternResult.DetectedPattern)
        val pattern = result as PatternResult.DetectedPattern
        assertEquals(2, pattern.startHour)
        assertEquals(3.0, pattern.windowRate, 0.01)
    }

    @Test
    fun testOneDayWithNoData_returnsNotEnoughData() {
        val midnight = getMidnight(0)
        val now = midnight + TimeUnit.HOURS.toMillis(15) // today afternoon

        // Yesterday had a session, today has 0 sessions
        val yesterdaySession = ScreenOffSession(
            id = 1,
            startTime = midnight - TimeUnit.HOURS.toMillis(18),
            startPercent = 80,
            endTime = midnight - TimeUnit.HOURS.toMillis(16), // 2 hours
            endPercent = 76,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val trends = TrendCalculator.calculateTrends(listOf(yesterdaySession), now = now)
        assertTrue(trends.todayVsYesterday is TodayVsYesterdayResult.NotEnoughData)
    }

    @Test
    fun testThinData_returnsNoClearPattern() {
        val midnight = getMidnight(0)
        val now = midnight + TimeUnit.HOURS.toMillis(12)

        // Only 30 minutes in a single hour (< 2.0h window coverage)
        val shortSession = ScreenOffSession(
            id = 1,
            startTime = midnight - TimeUnit.HOURS.toMillis(5),
            startPercent = 80,
            endTime = midnight - TimeUnit.HOURS.toMillis(5) + TimeUnit.MINUTES.toMillis(30),
            endPercent = 79,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val result = PatternDetector.detectPattern(listOf(shortSession), now = now)
        assertEquals(PatternResult.NoClearPattern, result)
    }

    @Test
    fun testWeekOverWeekComparison() {
        val midnight = getMidnight(0)
        val now = midnight + TimeUnit.HOURS.toMillis(12)

        val sessions = mutableListOf<ScreenOffSession>()

        // This week (days 0..6): 2 hours each day with 2% lost = 1.0 %/hr
        for (i in 0..6) {
            val mid = midnight - TimeUnit.DAYS.toMillis(i.toLong())
            sessions.add(
                ScreenOffSession(
                    id = i.toLong(),
                    startTime = mid + TimeUnit.HOURS.toMillis(1),
                    startPercent = 80,
                    endTime = mid + TimeUnit.HOURS.toMillis(3),
                    endPercent = 78, // 2% lost in 2h = 1.0%/hr
                    wasCharging = false,
                    status = ScreenOffSession.STATUS_CLOSED
                )
            )
        }

        // Previous week (days 7..13): 2 hours each day with 4% lost = 2.0 %/hr
        for (i in 7..13) {
            val mid = midnight - TimeUnit.DAYS.toMillis(i.toLong())
            sessions.add(
                ScreenOffSession(
                    id = (i + 100).toLong(),
                    startTime = mid + TimeUnit.HOURS.toMillis(1),
                    startPercent = 80,
                    endTime = mid + TimeUnit.HOURS.toMillis(3),
                    endPercent = 76, // 4% lost in 2h = 2.0%/hr
                    wasCharging = false,
                    status = ScreenOffSession.STATUS_CLOSED
                )
            )
        }

        val trends = TrendCalculator.calculateTrends(sessions, now = now)
        assertTrue(trends.weekVsWeek is WeekVsWeekResult.Comparison)
        val comp = trends.weekVsWeek as WeekVsWeekResult.Comparison
        assertEquals(1.0, comp.thisWeekRate, 0.01)
        assertEquals(2.0, comp.prevWeekRate, 0.01)
        assertEquals(ComparisonVerdict.LOWER, comp.verdict)
        assertEquals(50, comp.percentDifference)
    }
}
