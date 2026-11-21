package com.example.ghostdrainhunter

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.GhostScoreCalculator
import com.example.ghostdrainhunter.domain.GhostScoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class GhostScoreCalculatorTest {

    private val now = 1_700_000_000_000L

    @Test
    fun testWorkedExample_gives92LowIdleDrain() {
        val session1 = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.HOURS.toMillis(24),
            startPercent = 80,
            endTime = now - TimeUnit.HOURS.toMillis(16), // 8 hours
            endPercent = 74, // 6% lost
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val session2 = ScreenOffSession(
            id = 2,
            startTime = now - TimeUnit.HOURS.toMillis(15),
            startPercent = 74,
            endTime = now - TimeUnit.HOURS.toMillis(8), // 7 hours
            endPercent = 68, // 6% lost
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val session3 = ScreenOffSession(
            id = 3,
            startTime = now - TimeUnit.HOURS.toMillis(6),
            startPercent = 68,
            endTime = now - TimeUnit.HOURS.toMillis(1), // 5 hours
            endPercent = 64, // 4% lost
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val result = GhostScoreCalculator.calculate(listOf(session1, session2, session3), now = now)

        assertTrue(result is GhostScoreResult.Score)
        val scoreResult = result as GhostScoreResult.Score
        assertEquals(92, scoreResult.score)
        assertEquals("Low idle drain", scoreResult.label)
        assertEquals(3, scoreResult.sessionsCount)
        assertEquals(20.0, scoreResult.totalIdleHours, 0.001)
        assertEquals(16, scoreResult.totalPercentLost)
        assertEquals(0.8, scoreResult.avgRatePerHour, 0.001)
    }

    @Test
    fun testZeroDrain_gives100Score() {
        val s1 = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.HOURS.toMillis(10),
            startPercent = 80,
            endTime = now - TimeUnit.HOURS.toMillis(8), // 2 hours
            endPercent = 80, // 0% lost
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val s2 = ScreenOffSession(
            id = 2,
            startTime = now - TimeUnit.HOURS.toMillis(7),
            startPercent = 80,
            endTime = now - TimeUnit.HOURS.toMillis(5), // 2 hours
            endPercent = 80, // 0% lost
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val s3 = ScreenOffSession(
            id = 3,
            startTime = now - TimeUnit.HOURS.toMillis(4),
            startPercent = 80,
            endTime = now - TimeUnit.HOURS.toMillis(2), // 2 hours
            endPercent = 80, // 0% lost
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val result = GhostScoreCalculator.calculate(listOf(s1, s2, s3), now = now)

        assertTrue(result is GhostScoreResult.Score)
        val scoreResult = result as GhostScoreResult.Score
        assertEquals(100, scoreResult.score)
        assertEquals("Low idle drain", scoreResult.label)
    }

    @Test
    fun testVeryHighDrain_clampsToZero() {
        val s1 = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.HOURS.toMillis(10),
            startPercent = 90,
            endTime = now - TimeUnit.HOURS.toMillis(8), // 2h
            endPercent = 66, // 24% lost -> 12%/hr
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val s2 = ScreenOffSession(
            id = 2,
            startTime = now - TimeUnit.HOURS.toMillis(7),
            startPercent = 66,
            endTime = now - TimeUnit.HOURS.toMillis(5), // 2h
            endPercent = 42, // 24% lost -> 12%/hr
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val s3 = ScreenOffSession(
            id = 3,
            startTime = now - TimeUnit.HOURS.toMillis(4),
            startPercent = 42,
            endTime = now - TimeUnit.HOURS.toMillis(2), // 2h
            endPercent = 18, // 24% lost -> 12%/hr
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val result = GhostScoreCalculator.calculate(listOf(s1, s2, s3), now = now)

        assertTrue(result is GhostScoreResult.Score)
        val scoreResult = result as GhostScoreResult.Score
        assertEquals(0, scoreResult.score)
        assertEquals("Very high idle drain", scoreResult.label)
    }

    @Test
    fun testNotEnoughData_fewerThanThreeSessionsOrUnderFourHours() {
        val s1 = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.HOURS.toMillis(10),
            startPercent = 80,
            endTime = now - TimeUnit.HOURS.toMillis(7), // 3h
            endPercent = 75,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val s2 = ScreenOffSession(
            id = 2,
            startTime = now - TimeUnit.HOURS.toMillis(6),
            startPercent = 75,
            endTime = now - TimeUnit.HOURS.toMillis(3), // 3h
            endPercent = 70,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val resultFewSessions = GhostScoreCalculator.calculate(listOf(s1, s2), now = now)
        assertTrue(resultFewSessions is GhostScoreResult.NotEnoughData)
        val notEnough = resultFewSessions as GhostScoreResult.NotEnoughData
        assertEquals(2, notEnough.validSessionsCount)
        assertEquals(6.0, notEnough.idleHours, 0.001)

        val s3 = ScreenOffSession(
            id = 3,
            startTime = now - TimeUnit.HOURS.toMillis(2),
            startPercent = 70,
            endTime = now - TimeUnit.HOURS.toMillis(1), // 1h
            endPercent = 68,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val s1Short = s1.copy(endTime = s1.startTime + TimeUnit.HOURS.toMillis(1)) // 1h
        val s2Short = s2.copy(endTime = s2.startTime + TimeUnit.HOURS.toMillis(1)) // 1h

        val resultUnderFourHours = GhostScoreCalculator.calculate(listOf(s1Short, s2Short, s3), now = now)
        assertTrue(resultUnderFourHours is GhostScoreResult.NotEnoughData)
        val notEnough2 = resultUnderFourHours as GhostScoreResult.NotEnoughData
        assertEquals(3, notEnough2.validSessionsCount)
        assertEquals(3.0, notEnough2.idleHours, 0.001)
    }

    @Test
    fun testExcludedSessions_areIgnored() {
        val valid1 = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.HOURS.toMillis(10),
            startPercent = 80,
            endTime = now - TimeUnit.HOURS.toMillis(7), // 3h
            endPercent = 75,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val valid2 = ScreenOffSession(
            id = 2,
            startTime = now - TimeUnit.HOURS.toMillis(6),
            startPercent = 75,
            endTime = now - TimeUnit.HOURS.toMillis(3), // 3h
            endPercent = 70,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val charged = ScreenOffSession(
            id = 3,
            startTime = now - TimeUnit.HOURS.toMillis(2),
            startPercent = 50,
            endTime = now - TimeUnit.HOURS.toMillis(1),
            endPercent = 80,
            wasCharging = true,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val tooShort = ScreenOffSession(
            id = 4,
            startTime = now - TimeUnit.MINUTES.toMillis(30),
            startPercent = 70,
            endTime = now - TimeUnit.MINUTES.toMillis(20), // 10 min < 15 min
            endPercent = 69,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val result = GhostScoreCalculator.calculate(listOf(valid1, valid2, charged, tooShort), now = now)
        assertTrue(result is GhostScoreResult.NotEnoughData)
    }

    @Test
    fun testSessionsOlderThanSevenDays_areIgnored() {
        val validRecent1 = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.DAYS.toMillis(2),
            startPercent = 80,
            endTime = now - TimeUnit.DAYS.toMillis(2) + TimeUnit.HOURS.toMillis(3),
            endPercent = 75,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val validRecent2 = ScreenOffSession(
            id = 2,
            startTime = now - TimeUnit.DAYS.toMillis(3),
            startPercent = 75,
            endTime = now - TimeUnit.DAYS.toMillis(3) + TimeUnit.HOURS.toMillis(3),
            endPercent = 70,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val oldSession = ScreenOffSession(
            id = 3,
            startTime = now - TimeUnit.DAYS.toMillis(8), // 8 days old!
            startPercent = 70,
            endTime = now - TimeUnit.DAYS.toMillis(8) + TimeUnit.HOURS.toMillis(3),
            endPercent = 65,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        val result = GhostScoreCalculator.calculate(listOf(validRecent1, validRecent2, oldSession), now = now)
        assertTrue(result is GhostScoreResult.NotEnoughData)
    }

    @Test
    fun testSessionsBeforeTrackingStartTime_areIgnoredByScore() {
        val trackingStart = now - TimeUnit.DAYS.toMillis(2)

        // Session 1: Before tracking start (3 days ago)
        val s1 = ScreenOffSession(
            id = 1,
            startTime = now - TimeUnit.DAYS.toMillis(3),
            startPercent = 80,
            endTime = now - TimeUnit.DAYS.toMillis(3) + TimeUnit.HOURS.toMillis(4),
            endPercent = 72,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        // Session 2 & 3: After tracking start (1 day ago & 12 hours ago)
        val s2 = ScreenOffSession(
            id = 2,
            startTime = now - TimeUnit.DAYS.toMillis(1),
            startPercent = 70,
            endTime = now - TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(3),
            endPercent = 67,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )
        val s3 = ScreenOffSession(
            id = 3,
            startTime = now - TimeUnit.HOURS.toMillis(12),
            startPercent = 67,
            endTime = now - TimeUnit.HOURS.toMillis(8),
            endPercent = 63,
            wasCharging = false,
            status = ScreenOffSession.STATUS_CLOSED
        )

        // Only 2 sessions after trackingStart, so score should return NotEnoughData!
        val result = GhostScoreCalculator.calculate(
            sessions = listOf(s1, s2, s3),
            now = now,
            trackingStartTime = trackingStart
        )
        assertTrue(result is GhostScoreResult.NotEnoughData)
        val notEnough = result as GhostScoreResult.NotEnoughData
        assertEquals(2, notEnough.validSessionsCount) // s1 was ignored!
    }
}
