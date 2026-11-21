package com.example.ghostdrainhunter.domain

import com.example.ghostdrainhunter.data.db.ScreenOffSession

sealed interface PatternResult {
    data object NoClearPattern : PatternResult

    data class DetectedPattern(
        val startHour: Int, // 0 to 23
        val endHour: Int,   // (startHour + 2) % 24
        val windowRate: Double,
        val hourlyRates: List<Double?>, // size 24, null if coverage < 1.0h
        val hourlyCoverages: List<Double> // size 24
    ) : PatternResult
}

object PatternDetector {
    const val FOURTEEN_DAYS_MILLIS = 14L * 24 * 3600 * 1000L
    const val MIN_HOUR_COVERAGE = 1.0
    const val MIN_WINDOW_COVERAGE = 2.0

    fun detectPattern(
        sessions: List<ScreenOffSession>,
        now: Long = System.currentTimeMillis(),
        minSessionMinutes: Int = 15,
        trackingStartTime: Long = 0L
    ): PatternResult {
        val cutoff = maxOf(now - FOURTEEN_DAYS_MILLIS, trackingStartTime)

        // Valid sessions within the last 14 days and >= trackingStartTime
        val validAnalyses = sessions
            .filter { it.startTime >= cutoff && it.endTime != null && it.endPercent != null }
            .map {
                SessionAnalyzer.analyze(
                    session = it,
                    thresholdPerHour = 3.0,
                    minSessionMinutes = minSessionMinutes
                )
            }
            .filter { it.status == AnalysisStatus.VALID }

        if (validAnalyses.isEmpty()) {
            return PatternResult.NoClearPattern
        }

        // Aggregate hourly lost % and coverage hours
        val lostByHour = DoubleArray(24) { 0.0 }
        val coverageByHour = DoubleArray(24) { 0.0 }

        for (analysis in validAnalyses) {
            val slices = TimeAllocator.splitIntoHourSlices(analysis.session, analysis.percentLost)
            for (slice in slices) {
                if (slice.hourOfDay in 0..23) {
                    lostByHour[slice.hourOfDay] += slice.lostPercent
                    coverageByHour[slice.hourOfDay] += slice.durationHours
                }
            }
        }

        // Compute hourly rates (only if coverage >= 1 hour)
        val hourlyRates = List(24) { h ->
            if (coverageByHour[h] >= MIN_HOUR_COVERAGE) {
                lostByHour[h] / coverageByHour[h]
            } else {
                null
            }
        }

        // Two-hour windows wrapping past midnight: window h covers h and (h+1)%24
        // Requires combined coverage >= 2.0 hours
        var bestStartHour = -1
        var maxRate = -1.0
        val windowRates = mutableListOf<Double>()

        for (h in 0..23) {
            val nextH = (h + 1) % 24
            val combinedCoverage = coverageByHour[h] + coverageByHour[nextH]
            if (combinedCoverage >= MIN_WINDOW_COVERAGE) {
                val combinedLost = lostByHour[h] + lostByHour[nextH]
                val rate = combinedLost / combinedCoverage
                windowRates.add(rate)
                if (rate > maxRate) {
                    maxRate = rate
                    bestStartHour = h
                }
            }
        }

        if (bestStartHour == -1 || windowRates.isEmpty()) {
            return PatternResult.NoClearPattern
        }

        val minRate = windowRates.minOrNull() ?: maxRate
        if (maxRate - minRate < 0.0001) {
            return PatternResult.NoClearPattern
        }

        return PatternResult.DetectedPattern(
            startHour = bestStartHour,
            endHour = (bestStartHour + 2) % 24,
            windowRate = maxRate,
            hourlyRates = hourlyRates,
            hourlyCoverages = coverageByHour.toList()
        )
    }

    fun formatWindowString(startHour: Int, endHour: Int, is24Hour: Boolean): String {
        return if (is24Hour) {
            String.format(java.util.Locale.US, "%02d:00 and %02d:00", startHour, endHour)
        } else {
            "${format12Hour(startHour)} and ${format12Hour(endHour)}"
        }
    }

    private fun format12Hour(hour: Int): String {
        return when (hour) {
            0 -> "12 AM"
            12 -> "12 PM"
            in 1..11 -> "$hour AM"
            else -> "${hour - 12} PM"
        }
    }
}
