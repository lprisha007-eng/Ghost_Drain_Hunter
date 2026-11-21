package com.example.ghostdrainhunter.domain

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ComparisonVerdict(val label: String) {
    LOWER("lower"),
    HIGHER("higher"),
    SIMILAR("similar")
}

data class DailyTrendItem(
    val dayStartMillis: Long,
    val dayLabel: String,
    val idleHours: Double,
    val lostPercent: Double,
    val ratePerHour: Double
)

sealed interface TodayVsYesterdayResult {
    data class NotEnoughData(
        val todayIdleHours: Double,
        val yesterdayIdleHours: Double
    ) : TodayVsYesterdayResult

    data class Comparison(
        val todayRate: Double,
        val todayHours: Double,
        val yesterdayRate: Double,
        val yesterdayHours: Double,
        val percentDifference: Int,
        val verdict: ComparisonVerdict
    ) : TodayVsYesterdayResult
}

sealed interface WeekVsWeekResult {
    data class NotEnoughData(
        val thisWeekHours: Double,
        val prevWeekHours: Double
    ) : WeekVsWeekResult

    data class Comparison(
        val thisWeekRate: Double,
        val thisWeekHours: Double,
        val prevWeekRate: Double,
        val prevWeekHours: Double,
        val percentDifference: Int,
        val verdict: ComparisonVerdict
    ) : WeekVsWeekResult
}

data class TrendSummary(
    val todayVsYesterday: TodayVsYesterdayResult,
    val weekVsWeek: WeekVsWeekResult,
    val last7Days: List<DailyTrendItem>
)

object TrendCalculator {

    fun calculateTrends(
        sessions: List<ScreenOffSession>,
        now: Long = System.currentTimeMillis(),
        minSessionMinutes: Int = 15,
        trackingStartTime: Long = 0L
    ): TrendSummary {
        val validAnalyses = sessions
            .filter { it.startTime >= trackingStartTime && it.endTime != null && it.endPercent != null }
            .map {
                SessionAnalyzer.analyze(
                    session = it,
                    thresholdPerHour = 3.0,
                    minSessionMinutes = minSessionMinutes
                )
            }
            .filter { it.status == AnalysisStatus.VALID }

        // Aggregate day slices across all valid sessions
        val dayCoverageMap = mutableMapOf<Long, Double>()
        val dayLostMap = mutableMapOf<Long, Double>()

        for (analysis in validAnalyses) {
            val daySlices = TimeAllocator.splitIntoDaySlices(analysis.session, analysis.percentLost)
            for (slice in daySlices) {
                // Ensure slice falls at or after trackingStartTime's day
                if (slice.localDayStartMillis + TimeUnit.DAYS.toMillis(1) > trackingStartTime) {
                    dayCoverageMap[slice.localDayStartMillis] =
                        (dayCoverageMap[slice.localDayStartMillis] ?: 0.0) + slice.durationHours
                    dayLostMap[slice.localDayStartMillis] =
                        (dayLostMap[slice.localDayStartMillis] ?: 0.0) + slice.lostPercent
                }
            }
        }

        val todayMidnight = getStartOfDay(now)
        val yesterdayMidnight = getStartOfDay(now - TimeUnit.DAYS.toMillis(1))

        // 1. Today vs Yesterday
        val todayHours = dayCoverageMap[todayMidnight] ?: 0.0
        val todayLost = dayLostMap[todayMidnight] ?: 0.0
        val yesterdayHours = dayCoverageMap[yesterdayMidnight] ?: 0.0
        val yesterdayLost = dayLostMap[yesterdayMidnight] ?: 0.0

        val todayVsYesterday = if (todayHours < 1.0 || yesterdayHours < 1.0) {
            TodayVsYesterdayResult.NotEnoughData(todayHours, yesterdayHours)
        } else {
            val todayRate = todayLost / todayHours
            val yesterdayRate = yesterdayLost / yesterdayHours

            val verdict: ComparisonVerdict
            val diffPercent: Int

            if (yesterdayRate > 0.0) {
                val relDiff = (todayRate - yesterdayRate) / yesterdayRate
                if (abs(relDiff) <= 0.10) {
                    verdict = ComparisonVerdict.SIMILAR
                    diffPercent = (abs(relDiff) * 100).roundToInt()
                } else if (todayRate < yesterdayRate) {
                    verdict = ComparisonVerdict.LOWER
                    diffPercent = (abs(relDiff) * 100).roundToInt()
                } else {
                    verdict = ComparisonVerdict.HIGHER
                    diffPercent = (abs(relDiff) * 100).roundToInt()
                }
            } else {
                verdict = if (todayRate == 0.0) ComparisonVerdict.SIMILAR else ComparisonVerdict.HIGHER
                diffPercent = 0
            }

            TodayVsYesterdayResult.Comparison(
                todayRate = todayRate,
                todayHours = todayHours,
                yesterdayRate = yesterdayRate,
                yesterdayHours = yesterdayHours,
                percentDifference = diffPercent,
                verdict = verdict
            )
        }

        // 2. Last 7 Days Daily Trend Items
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val last7Days = (6 downTo 0).map { dayOffset ->
            val dayStart = getStartOfDay(now - TimeUnit.DAYS.toMillis(dayOffset.toLong()))
            val cov = dayCoverageMap[dayStart] ?: 0.0
            val lost = dayLostMap[dayStart] ?: 0.0
            val rate = if (cov > 0.0) lost / cov else 0.0
            DailyTrendItem(
                dayStartMillis = dayStart,
                dayLabel = dayFormat.format(Date(dayStart)),
                idleHours = cov,
                lostPercent = lost,
                ratePerHour = rate
            )
        }

        // 3. This Week (last 7 days: dayOffset 0..6) vs Previous Week (dayOffset 7..13)
        var thisWeekHours = 0.0
        var thisWeekLost = 0.0
        for (i in 0..6) {
            val dayStart = getStartOfDay(now - TimeUnit.DAYS.toMillis(i.toLong()))
            thisWeekHours += dayCoverageMap[dayStart] ?: 0.0
            thisWeekLost += dayLostMap[dayStart] ?: 0.0
        }

        var prevWeekHours = 0.0
        var prevWeekLost = 0.0
        for (i in 7..13) {
            val dayStart = getStartOfDay(now - TimeUnit.DAYS.toMillis(i.toLong()))
            prevWeekHours += dayCoverageMap[dayStart] ?: 0.0
            prevWeekLost += dayLostMap[dayStart] ?: 0.0
        }

        val weekVsWeek = if (thisWeekHours < 1.0 || prevWeekHours < 1.0) {
            WeekVsWeekResult.NotEnoughData(thisWeekHours, prevWeekHours)
        } else {
            val thisWeekRate = thisWeekLost / thisWeekHours
            val prevWeekRate = prevWeekLost / prevWeekHours

            val verdict: ComparisonVerdict
            val diffPercent: Int

            if (prevWeekRate > 0.0) {
                val relDiff = (thisWeekRate - prevWeekRate) / prevWeekRate
                if (abs(relDiff) <= 0.10) {
                    verdict = ComparisonVerdict.SIMILAR
                    diffPercent = (abs(relDiff) * 100).roundToInt()
                } else if (thisWeekRate < prevWeekRate) {
                    verdict = ComparisonVerdict.LOWER
                    diffPercent = (abs(relDiff) * 100).roundToInt()
                } else {
                    verdict = ComparisonVerdict.HIGHER
                    diffPercent = (abs(relDiff) * 100).roundToInt()
                }
            } else {
                verdict = if (thisWeekRate == 0.0) ComparisonVerdict.SIMILAR else ComparisonVerdict.HIGHER
                diffPercent = 0
            }

            WeekVsWeekResult.Comparison(
                thisWeekRate = thisWeekRate,
                thisWeekHours = thisWeekHours,
                prevWeekRate = prevWeekRate,
                prevWeekHours = prevWeekHours,
                percentDifference = diffPercent,
                verdict = verdict
            )
        }

        return TrendSummary(
            todayVsYesterday = todayVsYesterday,
            weekVsWeek = weekVsWeek,
            last7Days = last7Days
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
