package com.example.ghostdrainhunter.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ghostdrainhunter.R
import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.ComparisonVerdict
import com.example.ghostdrainhunter.domain.DailyTrendItem
import com.example.ghostdrainhunter.domain.TodayVsYesterdayResult
import com.example.ghostdrainhunter.domain.TrendCalculator
import com.example.ghostdrainhunter.domain.WeekVsWeekResult
import com.example.ghostdrainhunter.ui.theme.GhostPrimary
import com.example.ghostdrainhunter.ui.theme.GhostSecondary
import com.example.ghostdrainhunter.ui.theme.StatusExcluded
import com.example.ghostdrainhunter.ui.theme.VerdictAboveLimit
import com.example.ghostdrainhunter.ui.theme.VerdictWellAboveLimit
import com.example.ghostdrainhunter.ui.theme.VerdictWithinLimit
import java.util.Locale

@Composable
fun TrendsScreen(
    sessions: List<ScreenOffSession>,
    thresholdPerHour: Double,
    minSessionMinutes: Int,
    trackingStartTime: Long = 0L
) {
    val trendSummary = remember(sessions, thresholdPerHour, minSessionMinutes, trackingStartTime) {
        TrendCalculator.calculateTrends(
            sessions = sessions,
            minSessionMinutes = minSessionMinutes,
            trackingStartTime = trackingStartTime
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.trends_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Today vs Yesterday Card
        TodayVsYesterdayCard(result = trendSummary.todayVsYesterday)

        // 7-Day Bar Chart with Dashed Threshold
        WeeklyTrendChartCard(
            dailyItems = trendSummary.last7Days,
            thresholdPerHour = thresholdPerHour
        )

        // This Week vs Last Week Card
        WeekVsWeekCard(result = trendSummary.weekVsWeek)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TodayVsYesterdayCard(result: TodayVsYesterdayResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.today_vs_yesterday_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (result is TodayVsYesterdayResult.Comparison) {
                    VerdictBadge(verdict = result.verdict)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (result) {
                is TodayVsYesterdayResult.NotEnoughData -> {
                    Text(
                        text = stringResource(R.string.today_vs_yesterday_not_enough),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is TodayVsYesterdayResult.Comparison -> {
                    val wording = if (result.verdict == ComparisonVerdict.SIMILAR) {
                        stringResource(
                            R.string.today_vs_yesterday_similar,
                            result.todayRate,
                            result.yesterdayRate
                        )
                    } else {
                        stringResource(
                            R.string.today_vs_yesterday_format,
                            result.todayRate,
                            result.yesterdayRate,
                            result.percentDifference,
                            result.verdict.label
                        )
                    }

                    Text(
                        text = wording,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "Today: %.1f h idle", result.todayHours),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "Yesterday: %.1f h idle", result.yesterdayHours),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyTrendChartCard(
    dailyItems: List<DailyTrendItem>,
    thresholdPerHour: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.weekly_chart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.weekly_chart_threshold_legend, thresholdPerHour),
                    style = MaterialTheme.typography.labelSmall,
                    color = VerdictWellAboveLimit,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas 7-Day Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val maxRateInData = dailyItems.maxOfOrNull { it.ratePerHour } ?: 0.0
                val maxY = maxOf(maxRateInData, thresholdPerHour, 4.0) * 1.15
                val chartHeight = size.height - 24.dp.toPx()
                val chartWidth = size.width

                val barCount = dailyItems.size
                val barSpacing = 12.dp.toPx()
                val totalSpacing = barSpacing * (barCount - 1)
                val barWidth = (chartWidth - totalSpacing) / barCount

                // Draw Dashed Threshold Line
                val thresholdY = (chartHeight - (thresholdPerHour / maxY * chartHeight)).toFloat()
                drawLine(
                    color = VerdictWellAboveLimit,
                    start = Offset(0f, thresholdY),
                    end = Offset(chartWidth, thresholdY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )

                // Draw bars for each day
                dailyItems.forEachIndexed { index, item ->
                    val barHeight = if (item.ratePerHour > 0.0) {
                        ((item.ratePerHour / maxY) * chartHeight).toFloat().coerceAtLeast(4.dp.toPx())
                    } else {
                        3.dp.toPx()
                    }

                    val x = index * (barWidth + barSpacing)
                    val y = chartHeight - barHeight

                    val barColor = when {
                        item.idleHours < 0.1 -> StatusExcluded.copy(alpha = 0.25f)
                        item.ratePerHour > thresholdPerHour -> VerdictWellAboveLimit
                        else -> GhostPrimary
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Labels under each bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dailyItems.forEach { item ->
                    Text(
                        text = item.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WeekVsWeekCard(result: WeekVsWeekResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.week_vs_week_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (result is WeekVsWeekResult.Comparison) {
                    VerdictBadge(verdict = result.verdict)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (result) {
                is WeekVsWeekResult.NotEnoughData -> {
                    Text(
                        text = stringResource(R.string.week_vs_week_not_enough),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is WeekVsWeekResult.Comparison -> {
                    val wording = if (result.verdict == ComparisonVerdict.SIMILAR) {
                        stringResource(
                            R.string.week_vs_week_similar,
                            result.thisWeekRate,
                            result.prevWeekRate
                        )
                    } else {
                        stringResource(
                            R.string.week_vs_week_format,
                            result.thisWeekRate,
                            result.prevWeekRate,
                            result.percentDifference,
                            result.verdict.label
                        )
                    }

                    Text(
                        text = wording,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "This week: %.1f h idle", result.thisWeekHours),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "Previous week: %.1f h idle", result.prevWeekHours),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerdictBadge(verdict: ComparisonVerdict) {
    val (badgeColor, badgeText) = when (verdict) {
        ComparisonVerdict.LOWER -> VerdictWithinLimit to "Lower"
        ComparisonVerdict.HIGHER -> VerdictAboveLimit to "Higher"
        ComparisonVerdict.SIMILAR -> GhostSecondary to "Similar"
    }

    Surface(
        color = badgeColor.copy(alpha = 0.2f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = badgeText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            fontWeight = FontWeight.Bold
        )
    }
}
