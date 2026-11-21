package com.example.ghostdrainhunter.domain

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import kotlin.math.roundToInt

sealed interface GhostScoreResult {
    data class NotEnoughData(
        val validSessionsCount: Int = 0,
        val idleHours: Double = 0.0
    ) : GhostScoreResult
    
    data class Score(
        val score: Int,
        val label: String,
        val sessionsCount: Int,
        val totalIdleHours: Double,
        val totalPercentLost: Int,
        val avgRatePerHour: Double
    ) : GhostScoreResult
}

object GhostScoreCalculator {
    const val MIN_VALID_SESSIONS = 3
    const val MIN_IDLE_HOURS = 4.0
    const val SEVEN_DAYS_MILLIS = 7L * 24 * 3600 * 1000L

    fun calculate(
        sessions: List<ScreenOffSession>,
        now: Long = System.currentTimeMillis(),
        minSessionMinutes: Int = 15,
        trackingStartTime: Long = 0L
    ): GhostScoreResult {
        val cutoff = maxOf(now - SEVEN_DAYS_MILLIS, trackingStartTime)

        // Only valid sessions within last 7 days and after trackingStartTime
        val validAnalyses = sessions
            .filter { it.startTime >= cutoff }
            .map { session ->
                SessionAnalyzer.analyze(
                    session = session,
                    thresholdPerHour = 3.0,
                    minSessionMinutes = minSessionMinutes
                )
            }
            .filter { it.status == AnalysisStatus.VALID }

        val sessionsCount = validAnalyses.size
        val totalIdleHours = validAnalyses.sumOf { it.durationHours }
        val totalPercentLost = validAnalyses.sumOf { it.percentLost }

        if (sessionsCount < MIN_VALID_SESSIONS || totalIdleHours < MIN_IDLE_HOURS) {
            return GhostScoreResult.NotEnoughData(
                validSessionsCount = sessionsCount,
                idleHours = totalIdleHours
            )
        }

        val avgRate = if (totalIdleHours > 0.0) totalPercentLost / totalIdleHours else 0.0
        val rawScore = 100.0 - (10.0 * avgRate)
        val score = rawScore.coerceIn(0.0, 100.0).roundToInt()

        val label = when (score) {
            in 80..100 -> "Low idle drain"
            in 60..79 -> "Moderate idle drain"
            in 40..59 -> "High idle drain"
            else -> "Very high idle drain"
        }

        return GhostScoreResult.Score(
            score = score,
            label = label,
            sessionsCount = sessionsCount,
            totalIdleHours = totalIdleHours,
            totalPercentLost = totalPercentLost,
            avgRatePerHour = avgRate
        )
    }
}
