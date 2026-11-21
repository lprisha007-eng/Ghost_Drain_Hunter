package com.example.ghostdrainhunter

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.report.ReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReportBuilderTest {

    private val now = 1_700_000_000_000L

    @Test
    fun testEmptyPeriod_returnsEmptyReport() {
        val report = ReportBuilder.buildReport(
            sessions = emptyList(),
            periodDays = 7,
            thresholdPerHour = 3.0,
            now = now
        )

        assertTrue(report.isEmpty)
        assertEquals(7, report.periodDays)
        assertEquals(0, report.validSessionsCount)
        assertEquals(0, report.excludedSessionsCount)
        assertEquals(0.0, report.totalValidIdleHours, 0.001)
        assertEquals(7, report.dailyTable.size)
        assertTrue(report.topHighestDrainSessions.isEmpty())
    }

    @Test
    fun testSevenDayReportWithData() {
        val sessions = mutableListOf<ScreenOffSession>()

        // 3 valid sessions inside the last 7 days
        // Session 1: 2 days ago, 4 hours, 8% lost = 2.0%/hr
        sessions.add(
            ScreenOffSession(
                id = 1,
                startTime = now - TimeUnit.DAYS.toMillis(2),
                startPercent = 80,
                endTime = now - TimeUnit.DAYS.toMillis(2) + TimeUnit.HOURS.toMillis(4),
                endPercent = 72,
                wasCharging = false,
                status = ScreenOffSession.STATUS_CLOSED
            )
        )

        // Session 2: 3 days ago, 2 hours, 10% lost = 5.0%/hr (above 3.0 threshold)
        sessions.add(
            ScreenOffSession(
                id = 2,
                startTime = now - TimeUnit.DAYS.toMillis(3),
                startPercent = 70,
                endTime = now - TimeUnit.DAYS.toMillis(3) + TimeUnit.HOURS.toMillis(2),
                endPercent = 60,
                wasCharging = false,
                status = ScreenOffSession.STATUS_CLOSED
            )
        )

        // Session 3: 4 days ago, 2 hours, 2% lost = 1.0%/hr
        sessions.add(
            ScreenOffSession(
                id = 3,
                startTime = now - TimeUnit.DAYS.toMillis(4),
                startPercent = 60,
                endTime = now - TimeUnit.DAYS.toMillis(4) + TimeUnit.HOURS.toMillis(2),
                endPercent = 58,
                wasCharging = false,
                status = ScreenOffSession.STATUS_CLOSED
            )
        )

        // Excluded session (charging)
        sessions.add(
            ScreenOffSession(
                id = 4,
                startTime = now - TimeUnit.DAYS.toMillis(1),
                startPercent = 40,
                endTime = now - TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(2),
                endPercent = 90,
                wasCharging = true,
                status = ScreenOffSession.STATUS_CLOSED
            )
        )

        val report = ReportBuilder.buildReport(
            sessions = sessions,
            periodDays = 7,
            thresholdPerHour = 3.0,
            now = now
        )

        assertFalse(report.isEmpty)
        assertEquals(7, report.periodDays)
        assertEquals(3, report.validSessionsCount)
        assertEquals(1, report.excludedSessionsCount)
        assertEquals(8.0, report.totalValidIdleHours, 0.001) // 4 + 2 + 2 = 8h
        assertEquals(20, report.totalPercentLost)            // 8 + 10 + 2 = 20%
        assertEquals(2.5, report.avgDrainRatePerHour, 0.001) // 20 / 8 = 2.5%/hr
        assertEquals(1, report.sessionsAboveThresholdCount)  // Session 2 is 5.0 > 3.0
        assertEquals(7, report.dailyTable.size)

        // Check top sessions ordering: Session 2 (5.0%/hr) should be first
        assertEquals(3, report.topHighestDrainSessions.size)
        assertEquals(2L, report.topHighestDrainSessions[0].id)
        assertEquals(5.0, report.topHighestDrainSessions[0].ratePerHour, 0.001)
    }

    @Test
    fun testThirtyDayReport_hasThirtyDailyRows() {
        val report = ReportBuilder.buildReport(
            sessions = emptyList(),
            periodDays = 30,
            thresholdPerHour = 3.0,
            now = now
        )

        assertEquals(30, report.periodDays)
        assertEquals(30, report.dailyTable.size)
    }
}
