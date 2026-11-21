package com.example.ghostdrainhunter.ui.dashboard

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ghostdrainhunter.R
import com.example.ghostdrainhunter.domain.GhostScoreResult
import com.example.ghostdrainhunter.domain.PatternDetector
import com.example.ghostdrainhunter.domain.PatternResult
import com.example.ghostdrainhunter.domain.SessionAnalysis
import com.example.ghostdrainhunter.domain.SessionVerdict
import com.example.ghostdrainhunter.ui.theme.GhostPrimary
import com.example.ghostdrainhunter.ui.theme.GhostSecondary
import com.example.ghostdrainhunter.ui.theme.StatusExcluded
import com.example.ghostdrainhunter.ui.theme.VerdictAboveLimit
import com.example.ghostdrainhunter.ui.theme.VerdictWellAboveLimit
import com.example.ghostdrainhunter.ui.theme.VerdictWithinLimit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    var showExplanationSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (uiState) {
        DashboardUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is DashboardUiState.Content -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Monitoring Service Status Bar with "Monitoring since <date, time>"
                MonitoringStatusBar(
                    isMonitoring = uiState.isMonitoring,
                    trackingStartTime = uiState.trackingStartTime,
                    onStart = onStartMonitoring,
                    onStop = onStopMonitoring
                )

                // Pocket Drain Warning Banner (Red)
                AnimatedVisibility(
                    visible = uiState.isLatestSessionFlagged && uiState.latestSessionAnalysis != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    uiState.latestSessionAnalysis?.let { analysis ->
                        PocketDrainBanner(
                            analysis = analysis,
                            thresholdPerHour = uiState.thresholdPerHour
                        )
                    }
                }

                // Hero Ghost Drain Score Card
                GhostScoreHeroCard(
                    scoreResult = uiState.scoreResult,
                    onShowExplanation = { showExplanationSheet = true }
                )

                // Drain Pattern Detector Card
                PatternCard(patternResult = uiState.patternResult)

                // Last Screen-Off Session Card
                LastScreenOffCard(
                    analysis = uiState.latestSessionAnalysis,
                    hasAnySessions = uiState.hasAnySessionsSinceTrackingStart
                )

                // Smart Insights Card
                SmartInsightsCard(uiState = uiState)

                Spacer(modifier = Modifier.height(16.dp))
            }

            // How this score is calculated Bottom Sheet
            if (showExplanationSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showExplanationSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    ScoreExplanationSheetContent(
                        scoreResult = uiState.scoreResult,
                        onClose = { showExplanationSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
fun MonitoringStatusBar(
    isMonitoring: Boolean,
    trackingStartTime: Long,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isMonitoring) GhostPrimary else MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isMonitoring) {
                            stringResource(R.string.status_monitoring_active)
                        } else {
                            stringResource(R.string.status_monitoring_stopped)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = if (isMonitoring) onStop else onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isMonitoring) {
                            stringResource(R.string.btn_stop_monitoring)
                        } else {
                            stringResource(R.string.btn_start_monitoring)
                        }
                    )
                }
            }

            if (trackingStartTime > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.monitoring_since_format, dateFormat.format(Date(trackingStartTime))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PocketDrainBanner(
    analysis: SessionAnalysis,
    thresholdPerHour: Double
) {
    val durationFormatted = remember(analysis.durationMillis) {
        val totalMins = analysis.durationMillis / 60_000
        val hrs = totalMins / 60
        val mins = totalMins % 60
        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VerdictWellAboveLimit),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.pocket_drain_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.pocket_drain_banner_desc,
                        analysis.percentLost,
                        durationFormatted,
                        analysis.ratePerHour,
                        thresholdPerHour
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }
    }
}

@Composable
fun GhostScoreHeroCard(
    scoreResult: GhostScoreResult,
    onShowExplanation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.score_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onShowExplanation) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.how_calculated_btn),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (scoreResult) {
                is GhostScoreResult.NotEnoughData -> {
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = StatusExcluded.copy(alpha = 0.25f),
                                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "--",
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 42.sp),
                            fontWeight = FontWeight.Bold,
                            color = StatusExcluded
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.score_not_enough_data),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Real progress values
                    Text(
                        text = stringResource(
                            R.string.score_progress_format,
                            scoreResult.validSessionsCount,
                            scoreResult.idleHours
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.score_not_enough_data_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                is GhostScoreResult.Score -> {
                    val score = scoreResult.score
                    val ringColor = when (score) {
                        in 80..100 -> VerdictWithinLimit
                        in 60..79 -> VerdictAboveLimit
                        else -> VerdictWellAboveLimit
                    }

                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 14.dp.toPx()
                            drawCircle(
                                color = ringColor.copy(alpha = 0.2f),
                                style = Stroke(width = strokeWidth)
                            )
                            val sweepAngle = (score / 100f) * 360f
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score",
                                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 46.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "/ 100",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.score_format, score, scoreResult.label),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = ringColor
                    )
                }
            }
        }
    }
}

@Composable
fun PatternCard(patternResult: PatternResult) {
    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.pattern_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (patternResult) {
                PatternResult.NoClearPattern -> {
                    Text(
                        text = stringResource(R.string.pattern_no_clear_pattern),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PatternResult.DetectedPattern -> {
                    val windowStr = PatternDetector.formatWindowString(
                        patternResult.startHour,
                        patternResult.endHour,
                        is24Hour
                    )

                    Text(
                        text = stringResource(R.string.pattern_detected_format, windowStr),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.pattern_rate_format, patternResult.windowRate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = VerdictAboveLimit,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        val maxHourlyRate = patternResult.hourlyRates.filterNotNull().maxOrNull() ?: 1.0
                        val barMaxVal = maxOf(maxHourlyRate, 1.0)
                        val barSpacing = 3.dp.toPx()
                        val totalSpacing = barSpacing * 23
                        val barWidth = (size.width - totalSpacing) / 24f

                        val peakH1 = patternResult.startHour
                        val peakH2 = (patternResult.startHour + 1) % 24

                        for (h in 0..23) {
                            val rate = patternResult.hourlyRates[h]
                            val isPeak = (h == peakH1 || h == peakH2)
                            val barHeight = if (rate != null && rate > 0.0) {
                                ((rate / barMaxVal) * (size.height - 10.dp.toPx())).toFloat().coerceAtLeast(4.dp.toPx())
                            } else {
                                3.dp.toPx()
                            }

                            val x = h * (barWidth + barSpacing)
                            val y = size.height - barHeight

                            val barColor = when {
                                isPeak -> VerdictAboveLimit
                                rate != null -> GhostSecondary
                                else -> StatusExcluded.copy(alpha = 0.3f)
                            }

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0h", style = MaterialTheme.typography.labelSmall, color = StatusExcluded)
                        Text("6h", style = MaterialTheme.typography.labelSmall, color = StatusExcluded)
                        Text("12h", style = MaterialTheme.typography.labelSmall, color = StatusExcluded)
                        Text("18h", style = MaterialTheme.typography.labelSmall, color = StatusExcluded)
                        Text("23h", style = MaterialTheme.typography.labelSmall, color = StatusExcluded)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.pattern_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LastScreenOffCard(analysis: SessionAnalysis?, hasAnySessions: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.last_screen_off_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (!hasAnySessions || analysis == null) {
                Text(
                    text = stringResource(R.string.no_recent_session),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val durationFormatted = remember(analysis.durationMillis) {
                    val totalMins = analysis.durationMillis / 60_000
                    val hrs = totalMins / 60
                    val mins = totalMins % 60
                    if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                }
                val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(analysis.session.startTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val verdictText = when (analysis.verdict) {
                        SessionVerdict.WITHIN_LIMIT -> SessionVerdict.WITHIN_LIMIT.label
                        SessionVerdict.ABOVE_LIMIT -> SessionVerdict.ABOVE_LIMIT.label
                        SessionVerdict.WELL_ABOVE_LIMIT -> SessionVerdict.WELL_ABOVE_LIMIT.label
                        null -> analysis.status.name
                    }
                    val verdictColor = when (analysis.verdict) {
                        SessionVerdict.WITHIN_LIMIT -> VerdictWithinLimit
                        SessionVerdict.ABOVE_LIMIT -> VerdictAboveLimit
                        SessionVerdict.WELL_ABOVE_LIMIT -> VerdictWellAboveLimit
                        null -> StatusExcluded
                    }

                    Surface(
                        color = verdictColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = verdictText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = verdictColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.session_duration_format, durationFormatted),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.session_lost_format, analysis.percentLost),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.session_rate_format, analysis.ratePerHour),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (analysis.isFlagged) VerdictWellAboveLimit else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${analysis.session.startPercent}% → ${analysis.session.endPercent ?: "--"}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreExplanationSheetContent(
    scoreResult: GhostScoreResult,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.score_formula_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.score_formula_text),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        when (scoreResult) {
            is GhostScoreResult.NotEnoughData -> {
                Text(
                    text = stringResource(
                        R.string.score_progress_format,
                        scoreResult.validSessionsCount,
                        scoreResult.idleHours
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.score_not_enough_data_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is GhostScoreResult.Score -> {
                StatRow(
                    label = stringResource(R.string.stat_sessions_used),
                    value = "${scoreResult.sessionsCount}"
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatRow(
                    label = stringResource(R.string.stat_idle_hours),
                    value = String.format(Locale.US, "%.1f h", scoreResult.totalIdleHours)
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatRow(
                    label = stringResource(R.string.stat_percent_lost),
                    value = "${scoreResult.totalPercentLost}%"
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatRow(
                    label = stringResource(R.string.stat_avg_rate),
                    value = String.format(Locale.US, "%.2f %%/hr", scoreResult.avgRatePerHour)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.score_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SmartInsightsCard(uiState: DashboardUiState.Content) {
    val insights = remember(uiState.scoreResult, uiState.latestSessionAnalysis, uiState.patternResult) {
        buildInsights(uiState)
    }

    if (insights.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Smart Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            insights.forEachIndexed { idx, insight ->
                if (idx > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        color = insight.color.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = insight.badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = insight.color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = insight.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class Insight(val badge: String, val title: String, val body: String, val color: Color)

private fun buildInsights(uiState: DashboardUiState.Content): List<Insight> {
    val list = mutableListOf<Insight>()

    when (val score = uiState.scoreResult) {
        is GhostScoreResult.Score -> {
            if (score.score < 60) {
                list.add(
                    Insight(
                        badge = "HIGH DRAIN",
                        title = "Above-average idle battery drain detected",
                        body = "Your average idle drain rate is %.1f%%/hr over %d valid sessions. Consider checking background apps, location services, or Wi-Fi scanning.".format(
                            score.avgRatePerHour, score.sessionsCount
                        ),
                        color = VerdictWellAboveLimit
                    )
                )
            } else if (score.score >= 80) {
                list.add(
                    Insight(
                        badge = "HEALTHY",
                        title = "Battery idle drain is well within normal range",
                        body = "%.1f%%/hr average across %d sessions. Keep it up.".format(
                            score.avgRatePerHour, score.sessionsCount
                        ),
                        color = VerdictWithinLimit
                    )
                )
            }
        }
        is GhostScoreResult.NotEnoughData -> {
            if (!uiState.hasAnySessionsSinceTrackingStart) {
                list.add(
                    Insight(
                        badge = "TIP",
                        title = "Lock your screen to start collecting data",
                        body = "Ghost Drain Hunter measures battery use while your screen is off. Turn off your screen and check back in a few minutes.",
                        color = StatusExcluded
                    )
                )
            }
        }
    }

    uiState.latestSessionAnalysis?.let { analysis ->
        if (analysis.isFlagged) {
            list.add(
                Insight(
                    badge = "ALERT",
                    title = "Last session exceeded your drain threshold",
                    body = "%.1f%%/hr vs your threshold of %.1f%%/hr. This suggests an app or service was active while your screen was off.".format(
                        analysis.ratePerHour, uiState.thresholdPerHour
                    ),
                    color = VerdictWellAboveLimit
                )
            )
        }
    }

    when (val pattern = uiState.patternResult) {
        is PatternResult.DetectedPattern -> {
            list.add(
                Insight(
                    badge = "PATTERN",
                    title = "Drain peak identified in your usage",
                    body = "Your highest measured idle drain (%.1f%%/hr) tends to occur during a specific time window. This could indicate a scheduled sync or push activity.".format(
                        pattern.windowRate
                    ),
                    color = VerdictAboveLimit
                )
            )
        }
        PatternResult.NoClearPattern -> { /* no insight */ }
    }

    return list
}
