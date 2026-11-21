package com.example.ghostdrainhunter.domain

import com.example.ghostdrainhunter.data.db.ScreenOffSession

enum class AnalysisStatus {
    VALID,
    TOO_SHORT,
    CHARGED,
    INVALID
}

enum class SessionVerdict(val label: String) {
    WITHIN_LIMIT("Within limit"),
    ABOVE_LIMIT("Above limit"),
    WELL_ABOVE_LIMIT("Well above limit")
}

data class SessionAnalysis(
    val session: ScreenOffSession,
    val durationMillis: Long,
    val durationHours: Double,
    val percentLost: Int,
    val ratePerHour: Double,
    val status: AnalysisStatus,
    val isFlagged: Boolean,
    val verdict: SessionVerdict?,
    val exclusionReason: String? = null
)

object SessionAnalyzer {
    fun analyze(
        session: ScreenOffSession,
        thresholdPerHour: Double,
        minSessionMinutes: Int = 15
    ): SessionAnalysis {
        val endTime = session.endTime
        val endPercent = session.endPercent

        if (endTime == null || endPercent == null || endTime < session.startTime) {
            return SessionAnalysis(
                session = session,
                durationMillis = 0L,
                durationHours = 0.0,
                percentLost = 0,
                ratePerHour = 0.0,
                status = AnalysisStatus.INVALID,
                isFlagged = false,
                verdict = null,
                exclusionReason = "Invalid session data"
            )
        }

        val durationMillis = endTime - session.startTime
        val durationHours = durationMillis.toDouble() / 3_600_000.0
        val percentLost = session.startPercent - endPercent
        val ratePerHour = if (durationHours > 0.0) percentLost / durationHours else 0.0

        val status: AnalysisStatus
        val exclusionReason: String?

        if (percentLost < 0) {
            status = AnalysisStatus.INVALID
            exclusionReason = "Battery percentage increased"
        } else if (session.wasCharging) {
            status = AnalysisStatus.CHARGED
            exclusionReason = "Charger connected"
        } else if (durationMillis < minSessionMinutes * 60_000L) {
            status = AnalysisStatus.TOO_SHORT
            exclusionReason = "Shorter than minimum ($minSessionMinutes min)"
        } else {
            status = AnalysisStatus.VALID
            exclusionReason = null
        }

        val isFlagged = status == AnalysisStatus.VALID && ratePerHour > thresholdPerHour

        val verdict: SessionVerdict? = if (status == AnalysisStatus.VALID) {
            when {
                ratePerHour <= thresholdPerHour -> SessionVerdict.WITHIN_LIMIT
                ratePerHour <= 2.0 * thresholdPerHour -> SessionVerdict.ABOVE_LIMIT
                else -> SessionVerdict.WELL_ABOVE_LIMIT
            }
        } else {
            null
        }

        return SessionAnalysis(
            session = session,
            durationMillis = durationMillis,
            durationHours = durationHours,
            percentLost = percentLost,
            ratePerHour = ratePerHour,
            status = status,
            isFlagged = isFlagged,
            verdict = verdict,
            exclusionReason = exclusionReason
        )
    }
}
