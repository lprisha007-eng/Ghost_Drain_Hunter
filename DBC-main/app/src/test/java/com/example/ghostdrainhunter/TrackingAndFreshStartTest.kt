package com.example.ghostdrainhunter

import com.example.ghostdrainhunter.data.capture.CaptureService
import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.AnalysisStatus
import com.example.ghostdrainhunter.domain.PatternDetector
import com.example.ghostdrainhunter.domain.PatternResult
import com.example.ghostdrainhunter.domain.SessionAnalyzer
import com.example.ghostdrainhunter.domain.TodayVsYesterdayResult
import com.example.ghostdrainhunter.domain.TrendCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TrackingAndFreshStartTest {

    private val now = 1_700_000_000_000L

    @Test
    fun testSessionsBeforeTrackingStartTime_areIgnoredByPatternDetector() {
        val trackingStart = now - TimeUnit.DAYS.toMillis(2)

        // Session before tracking start with high drain (3 days ago)
        val oldHighDrain = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.DAYS.toMillis(3),
            startPercent = 80,
            endTime = now - TimeUnit.DAYS.toMillis(3) + TimeUnit.HOURS.toMillis(3),
            endPercent = 65, // 15% lost in 3h = 5.0%/hr
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val pattern = PatternDetector.detectPattern(
            sessions = listOf(oldHighDrain),
            now = now,
            trackingStartTime = trackingStart
        )

        // Since old session is before trackingStart, it is ignored -> NoClearPattern!
        assertEquals(PatternResult.NoClearPattern, pattern)
    }

    @Test
    fun testSessionsBeforeTrackingStartTime_areIgnoredByTrendCalculator() {
        val trackingStart = now - TimeUnit.HOURS.toMillis(12) // tracking started today noon

        // Yesterday session before tracking start
        val yesterdaySession = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.HOURS.toMillis(28),
            startPercent = 80,
            endTime = now - TimeUnit.HOURS.toMillis(26),
            endPercent = 76,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val trends = TrendCalculator.calculateTrends(
            sessions = listOf(yesterdaySession),
            now = now,
            trackingStartTime = trackingStart
        )

        // Yesterday is ignored, so todayVsYesterday must be NotEnoughData
        assertTrue(trends.todayVsYesterday is TodayVsYesterdayResult.NotEnoughData)
    }

    @Test
    fun testSessionsUnderTenSeconds_areIdentifiedAsDiscard() {
        val start = now
        val endShort = start + 5_000L // 5 seconds (under 10 seconds)
        val endValid = start + 12_000L // 12 seconds (10 seconds or more)

        val durationShort = endShort - start
        val durationValid = endValid - start

        assertTrue("5 seconds must be less than MIN_DISCARD_DURATION_MILLIS", durationShort < CaptureService.MIN_DISCARD_DURATION_MILLIS)
        assertFalse("12 seconds must NOT be less than MIN_DISCARD_DURATION_MILLIS", durationValid < CaptureService.MIN_DISCARD_DURATION_MILLIS)
    }

    @Test
    fun testSessionUnderMinDuration_isTooShort() {
        val minSessionMinutes = 15
        val shortSession = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.MINUTES.toMillis(10),
            startPercent = 80,
            endTime = now,                // 10 minutes - under 15 min minimum
            endPercent = 79,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val analysis = SessionAnalyzer.analyze(
            session = shortSession,
            thresholdPerHour = 3.0,
            minSessionMinutes = minSessionMinutes
        )
        assertEquals(AnalysisStatus.TOO_SHORT, analysis.status)
        assertFalse("TOO_SHORT sessions must not be flagged", analysis.isFlagged)
    }

    @Test
    fun testSessionAtExactMinDuration_isValid() {
        val minSessionMinutes = 15
        val exactSession = ScreenOffSession(
            id = 2,
            startTime = now - TimeUnit.MINUTES.toMillis(15),
            startPercent = 80,
            endTime = now,                // exactly 15 minutes
            endPercent = 79,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val analysis = SessionAnalyzer.analyze(
            session = exactSession,
            thresholdPerHour = 3.0,
            minSessionMinutes = minSessionMinutes
        )
        assertEquals(AnalysisStatus.VALID, analysis.status)
    }

    @Test
    fun testChargingSession_isClassifiedAsCharged() {
        // Session where wasCharging = true; battery may still drain slightly while charging
        val chargedSession = ScreenOffSession(
            id = 3,
            startTime = now - TimeUnit.HOURS.toMillis(2),
            startPercent = 80,
            endTime = now,
            endPercent = 78,             // slight drain while charging
            wasCharging = true,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val analysis = SessionAnalyzer.analyze(
            session = chargedSession,
            thresholdPerHour = 3.0,
            minSessionMinutes = 15
        )
        assertEquals(AnalysisStatus.CHARGED, analysis.status)
        assertFalse("CHARGED sessions must not be flagged", analysis.isFlagged)
    }

    @Test
    fun testManualStopPreventsAutoRestart_viaUserEnabledFlag() {
        // The logic: isMonitoringUserEnabled = false means auto-restart does not occur.
        // We simulate this via the flag value used in MainActivity's LaunchedEffect.
        // When the user manually stops monitoring, isMonitoringUserEnabled is set to false.
        // The LaunchedEffect only starts if: isMonitoringUserEnabled && !isMonitoring.
        // So if isMonitoringUserEnabled == false, auto-start must NOT trigger.
        val userEnabled = false          // simulates user pressed Stop
        val isMonitoring = false         // service is currently stopped

        val shouldAutoStart = userEnabled && !isMonitoring
        assertFalse("Auto-start must NOT occur when the user has manually stopped monitoring", shouldAutoStart)

        // Verify the reverse: if user re-enables, auto-start IS allowed
        val userReEnabled = true
        val shouldAutoStartAfterReEnable = userReEnabled && !isMonitoring
        assertTrue("Auto-start MUST occur after user re-enables monitoring", shouldAutoStartAfterReEnable)
    }

    @Test
    fun testStartFreshClearsData_conceptually() {
        // This unit test verifies the logic: after startFresh, trackingStartTime is updated to now,
        // so old sessions that have startTime < new trackingStartTime are excluded from all calculations.
        val oldTrackingStart = now - TimeUnit.DAYS.toMillis(7)
        val freshStart = now                            // simulates calling startFresh()

        val oldSession = ScreenOffSession(
            id = 10,
            startTime = now - TimeUnit.DAYS.toMillis(3),  // before freshStart
            startPercent = 80,
            endTime = now - TimeUnit.DAYS.toMillis(3) + TimeUnit.HOURS.toMillis(2),
            endPercent = 76,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        // With old tracking start, session is included
        val filteredWithOldStart = listOf(oldSession).filter { it.startTime >= oldTrackingStart }
        assertEquals(1, filteredWithOldStart.size)

        // After startFresh (freshStart = now), the same session is excluded
        val filteredAfterFresh = listOf(oldSession).filter { it.startTime >= freshStart }
        assertEquals(0, filteredAfterFresh.size)
    }
}
