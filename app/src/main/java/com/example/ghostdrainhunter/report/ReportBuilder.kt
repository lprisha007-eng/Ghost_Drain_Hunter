package com.example.ghostdrainhunter.report

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.AnalysisStatus
import com.example.ghostdrainhunter.domain.GhostScoreCalculator
import com.example.ghostdrainhunter.domain.GhostScoreResult
import com.example.ghostdrainhunter.domain.PatternDetector
import com.example.ghostdrainhunter.domain.PatternResult
import com.example.ghostdrainhunter.domain.SessionAnalyzer
import com.example.ghostdrainhunter.domain.TimeAllocator
import com.example.ghostdrainhunter.domain.TrendCalculator
import com.example.ghostdrainhunter.domain.WeekVsWeekResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TopSessionItem(
    val id: Long,
    val dateFormatted: String,
    val timeRangeFormatted: String,
    val durationFormatted: String,
    val percentLost: Int,
    val ratePerHour: Double
)

data class ReportDailyRow(
    val dayLabel: String,
    val idleHours: Double,
    val percentLost: Double,
    val ratePerHour: Double
)

data class ReportData(
    val periodDays: Int, // 7 or 30
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val generatedAtMillis: Long,
    val scoreResult: GhostScoreResult,
    val totalValidIdleHours: Double,
    val validSessionsCount: Int,
    val excludedSessionsCount: Int,
    val totalPercentLost: Int,
    val avgDrainRatePerHour: Double,
    val thresholdPerHour: Double,
    val sessionsAboveThresholdCount: Int,
    val topHighestDrainSessions: List<TopSessionItem>,
    val patternLine: String,
    val dailyTable: List<ReportDailyRow>,
    val weekVsWeekSummary: String,
    val isEmpty: Boolean,
    val disclaimer: String = "This is a local heuristic based on your measured idle drain. It is not an official battery-health measurement."
)

object ReportBuilder {

    fun buildReport(
        sessions: List<ScreenOffSession>,
        periodDays: Int = 7, // 7 or 30
        thresholdPerHour: Double = 3.0,
        minSessionMinutes: Int = 15,
        now: Long = System.currentTimeMillis(),
        is24Hour: Boolean = false,
        trackingStartTime: Long = 0L
    ): ReportData {
        val periodCutoff = maxOf(now - TimeUnit.DAYS.toMillis(periodDays.toLong()), trackingStartTime)

        val sessionsInPeriod = sessions.filter {
            it.startTime >= periodCutoff && it.endTime != null && it.endPercent != null
        }

        val analyzedInPeriod = sessionsInPeriod.map { session ->
            SessionAnalyzer.analyze(
                session = session,
                thresholdPerHour = thresholdPerHour,
                minSessionMinutes = minSessionMinutes
            )
        }

        val validSessions = analyzedInPeriod.filter { it.status == AnalysisStatus.VALID }
        val excludedSessions = analyzedInPeriod.filter { it.status != AnalysisStatus.VALID }

        val totalIdleHours = validSessions.sumOf { it.durationHours }
        val totalPercentLost = validSessions.sumOf { it.percentLost }
        val avgDrainRate = if (totalIdleHours > 0.0) totalPercentLost / totalIdleHours else 0.0
        val aboveThresholdCount = validSessions.count { it.ratePerHour > thresholdPerHour }

        val scoreResult = GhostScoreCalculator.calculate(
            sessions = sessionsInPeriod,
            now = now,
            minSessionMinutes = minSessionMinutes,
            trackingStartTime = trackingStartTime
        )

        val timeFormat = if (is24Hour) SimpleDateFormat("HH:mm", Locale.getDefault()) else SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        val top5Sessions = validSessions
            .sortedByDescending { it.ratePerHour }
            .take(5)
            .map { analysis ->
                val s = analysis.session
                val startStr = timeFormat.format(Date(s.startTime))
                val endStr = s.endTime?.let { timeFormat.format(Date(it)) } ?: "--"
                val totalMins = analysis.durationMillis / 60_000
                val hrs = totalMins / 60
                val mins = totalMins % 60
                val durStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"

                TopSessionItem(
                    id = s.id,
                    dateFormatted = dateFormat.format(Date(s.startTime)),
                    timeRangeFormatted = "$startStr - $endStr",
                    durationFormatted = durStr,
                    percentLost = analysis.percentLost,
                    ratePerHour = analysis.ratePerHour
                )
            }

        val patternResult = PatternDetector.detectPattern(
            sessions = sessions,
            now = now,
            minSessionMinutes = minSessionMinutes,
            trackingStartTime = trackingStartTime
        )
        val patternLine = when (patternResult) {
            PatternResult.NoClearPattern -> "No clear pattern yet"
            is PatternResult.DetectedPattern -> {
                val windowStr = PatternDetector.formatWindowString(
                    patternResult.startHour,
                    patternResult.endHour,
                    is24Hour
                )
                String.format(
                    Locale.US,
                    "Your highest recorded idle drain was between %s (%.1f %%/hr).",
                    windowStr,
                    patternResult.windowRate
                )
            }
        }

        val dayCoverageMap = mutableMapOf<Long, Double>()
        val dayLostMap = mutableMapOf<Long, Double>()
        for (analysis in validSessions) {
            val daySlices = TimeAllocator.splitIntoDaySlices(analysis.session, analysis.percentLost)
            for (slice in daySlices) {
                dayCoverageMap[slice.localDayStartMillis] =
                    (dayCoverageMap[slice.localDayStartMillis] ?: 0.0) + slice.durationHours
                dayLostMap[slice.localDayStartMillis] =
                    (dayLostMap[slice.localDayStartMillis] ?: 0.0) + slice.lostPercent
            }
        }

        val dailyFormat = SimpleDateFormat("MMM d (EEE)", Locale.getDefault())
        val dailyTable = (periodDays - 1 downTo 0).map { dayOffset ->
            val dayStart = getStartOfDay(now - TimeUnit.DAYS.toMillis(dayOffset.toLong()))
            val cov = dayCoverageMap[dayStart] ?: 0.0
            val lost = dayLostMap[dayStart] ?: 0.0
            val rate = if (cov > 0.0) lost / cov else 0.0

            ReportDailyRow(
                dayLabel = dailyFormat.format(Date(dayStart)),
                idleHours = cov,
                percentLost = lost,
                ratePerHour = rate
            )
        }

        val trends = TrendCalculator.calculateTrends(sessions, now, minSessionMinutes, trackingStartTime)
        val weekVsWeekSummary = when (val w = trends.weekVsWeek) {
            is WeekVsWeekResult.NotEnoughData -> "This week vs last week: Not enough data"
            is WeekVsWeekResult.Comparison -> {
                String.format(
                    Locale.US,
                    "%.1f %%/hr this week vs %.1f %%/hr last week (%d%% %s)",
                    w.thisWeekRate,
                    w.prevWeekRate,
                    w.percentDifference,
                    w.verdict.label
                )
            }
        }

        val isEmpty = validSessions.isEmpty() || totalIdleHours == 0.0

        return ReportData(
            periodDays = periodDays,
            periodStartMillis = periodCutoff,
            periodEndMillis = now,
            generatedAtMillis = now,
            scoreResult = scoreResult,
            totalValidIdleHours = totalIdleHours,
            validSessionsCount = validSessions.size,
            excludedSessionsCount = excludedSessions.size,
            totalPercentLost = totalPercentLost,
            avgDrainRatePerHour = avgDrainRate,
            thresholdPerHour = thresholdPerHour,
            sessionsAboveThresholdCount = aboveThresholdCount,
            topHighestDrainSessions = top5Sessions,
            patternLine = patternLine,
            dailyTable = dailyTable,
            weekVsWeekSummary = weekVsWeekSummary,
            isEmpty = isEmpty
        )
    }

    private fun getStartOfDay(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
