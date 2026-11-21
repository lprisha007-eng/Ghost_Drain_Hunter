package com.example.ghostdrainhunter.domain

import com.example.ghostdrainhunter.data.db.ScreenOffSession
import java.util.Calendar

data class HourSlice(
    val hourOfDay: Int, // 0 to 23
    val durationMillis: Long,
    val durationHours: Double,
    val lostPercent: Double,
    val localDayStartMillis: Long // midnight timestamp of the local calendar day
)

data class DaySlice(
    val localDayStartMillis: Long, // midnight timestamp of the local calendar day
    val durationMillis: Long,
    val durationHours: Double,
    val lostPercent: Double
)

object TimeAllocator {

    /**
     * Splits a session into local-clock hour slices.
     * Each slice receives:
     * lost% = session.percentLost * (sliceDuration / sessionDuration)
     * coverage = sliceDuration / 3,600,000.0
     * Handles sessions spanning multiple hours, crossing midnight, and DST changes.
     */
    fun splitIntoHourSlices(
        session: ScreenOffSession,
        percentLost: Int
    ): List<HourSlice> {
        val startTime = session.startTime
        val endTime = session.endTime ?: return emptyList()

        if (endTime <= startTime || percentLost < 0) {
            return emptyList()
        }

        val totalDurationMillis = endTime - startTime
        val slices = mutableListOf<HourSlice>()

        val cal = Calendar.getInstance().apply {
            timeInMillis = startTime
        }

        var currentStart = startTime

        while (currentStart < endTime) {
            cal.timeInMillis = currentStart

            val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)

            // Start of day (midnight)
            val dayStartCal = (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val localDayStartMillis = dayStartCal.timeInMillis

            // Next hour boundary
            val nextHourCal = (cal.clone() as Calendar).apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.HOUR_OF_DAY, 1)
            }
            val nextHourMillis = nextHourCal.timeInMillis

            val sliceEnd = minOf(nextHourMillis, endTime)
            val sliceDuration = sliceEnd - currentStart

            if (sliceDuration > 0) {
                val ratio = sliceDuration.toDouble() / totalDurationMillis.toDouble()
                val sliceLost = percentLost * ratio
                val durationHours = sliceDuration.toDouble() / 3_600_000.0

                slices.add(
                    HourSlice(
                        hourOfDay = hourOfDay,
                        durationMillis = sliceDuration,
                        durationHours = durationHours,
                        lostPercent = sliceLost,
                        localDayStartMillis = localDayStartMillis
                    )
                )
            }

            currentStart = sliceEnd
        }

        return slices
    }

    /**
     * Splits a session into local calendar day slices (split at local midnight).
     */
    fun splitIntoDaySlices(
        session: ScreenOffSession,
        percentLost: Int
    ): List<DaySlice> {
        val hourSlices = splitIntoHourSlices(session, percentLost)
        if (hourSlices.isEmpty()) return emptyList()

        // Aggregate hour slices by their localDayStartMillis
        return hourSlices
            .groupBy { it.localDayStartMillis }
            .map { (dayStart, slices) ->
                DaySlice(
                    localDayStartMillis = dayStart,
                    durationMillis = slices.sumOf { it.durationMillis },
                    durationHours = slices.sumOf { it.durationHours },
                    lostPercent = slices.sumOf { it.lostPercent }
                )
            }
            .sortedBy { it.localDayStartMillis }
    }
}
