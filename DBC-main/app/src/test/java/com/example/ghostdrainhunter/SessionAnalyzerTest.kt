package com.example.ghostdrainhunter

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.AnalysisStatus
import com.example.ghostdrainhunter.domain.DrainAlertNotifier
import com.example.ghostdrainhunter.domain.SessionAnalyzer
import com.example.ghostdrainhunter.domain.SessionVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnalyzerTest {

    private val oneHourMillis = 3_600_000L
    private val defaultThreshold = 3.0
    private val defaultMinDurationMinutes = 15

    @Test
    fun testExactlyAtThreshold_isWithinLimitAndNotFlagged() {
        val now = 1_000_000_000L
        val session = ScreenOffSession(
            id = 1,
            startTime = now,
            startPercent = 80,
            endTime = now + oneHourMillis,
            endPercent = 77, // lost 3% in 1 hour = 3.0 %/hr
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val analysis = SessionAnalyzer.analyze(session, defaultThreshold, defaultMinDurationMinutes)

        assertEquals(AnalysisStatus.VALID, analysis.status)
        assertEquals(3.0, analysis.ratePerHour, 0.001)
        assertFalse("At threshold should not be flagged", analysis.isFlagged)
        assertEquals(SessionVerdict.WITHIN_LIMIT, analysis.verdict)
    }

    @Test
    fun testOnePercentInTenMinutes_isTooShort() {
        val now = 1_000_000_000L
        val session = ScreenOffSession(
            id = 2,
            startTime = now,
            startPercent = 80,
            endTime = now + 10 * 60 * 1000L, // 10 minutes
            endPercent = 79, // lost 1%
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val analysis = SessionAnalyzer.analyze(session, defaultThreshold, defaultMinDurationMinutes)

        assertEquals(AnalysisStatus.TOO_SHORT, analysis.status)
        assertFalse(analysis.isFlagged)
        assertNull(analysis.verdict)
    }

    @Test
    fun testChargingSession_isExcludedAsCharged() {
        val now = 1_000_000_000L
        val session = ScreenOffSession(
            id = 3,
            startTime = now,
            startPercent = 50,
            endTime = now + 2 * oneHourMillis,
            endPercent = 40,
            wasCharging = true,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val analysis = SessionAnalyzer.analyze(session, defaultThreshold, defaultMinDurationMinutes)

        assertEquals(AnalysisStatus.CHARGED, analysis.status)
        assertFalse(analysis.isFlagged)
        assertNull(analysis.verdict)
    }

    @Test
    fun testNegativeLoss_isInvalid() {
        val now = 1_000_000_000L
        val session = ScreenOffSession(
            id = 4,
            startTime = now,
            startPercent = 50,
            endTime = now + oneHourMillis,
            endPercent = 60, // battery increased by 10%
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val analysis = SessionAnalyzer.analyze(session, defaultThreshold, defaultMinDurationMinutes)

        assertEquals(AnalysisStatus.INVALID, analysis.status)
        assertFalse(analysis.isFlagged)
        assertNull(analysis.verdict)
    }

    @Test
    fun testMissingEndTimeOrEndPercent_isInvalid() {
        val now = 1_000_000_000L
        val openSession = ScreenOffSession(
            id = 5,
            startTime = now,
            startPercent = 50,
            endTime = null,
            endPercent = null,
            wasCharging = false,
            status = ScreenOffSession.STATUS_OPEN
        )

        val analysis = SessionAnalyzer.analyze(openSession, defaultThreshold, defaultMinDurationMinutes)

        assertEquals(AnalysisStatus.INVALID, analysis.status)
        assertFalse(analysis.isFlagged)
        assertNull(analysis.verdict)
    }

    @Test
    fun testChangingThreshold_changesVerdictAndFlag() {
        val now = 1_000_000_000L
        // 8% lost in 2 hours = 4.0 %/hr
        val session = ScreenOffSession(
            id = 6,
            startTime = now,
            startPercent = 70,
            endTime = now + 2 * oneHourMillis,
            endPercent = 62,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        // Threshold = 5.0 %/hr -> Within limit (4.0 <= 5.0)
        val analysisAt5 = SessionAnalyzer.analyze(session, thresholdPerHour = 5.0, minSessionMinutes = 15)
        assertEquals(SessionVerdict.WITHIN_LIMIT, analysisAt5.verdict)
        assertFalse(analysisAt5.isFlagged)

        // Threshold = 3.0 %/hr -> Above limit (3.0 < 4.0 <= 6.0)
        val analysisAt3 = SessionAnalyzer.analyze(session, thresholdPerHour = 3.0, minSessionMinutes = 15)
        assertEquals(SessionVerdict.ABOVE_LIMIT, analysisAt3.verdict)
        assertTrue(analysisAt3.isFlagged)

        // Threshold = 1.5 %/hr -> Well above limit (4.0 > 2 * 1.5 = 3.0)
        val analysisAt1_5 = SessionAnalyzer.analyze(session, thresholdPerHour = 1.5, minSessionMinutes = 15)
        assertEquals(SessionVerdict.WELL_ABOVE_LIMIT, analysisAt1_5.verdict)
        assertTrue(analysisAt1_5.isFlagged)
    }
}
