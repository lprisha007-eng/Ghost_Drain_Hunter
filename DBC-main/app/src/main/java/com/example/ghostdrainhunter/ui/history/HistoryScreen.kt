package com.example.ghostdrainhunter.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ghostdrainhunter.R
import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.AnalysisStatus
import com.example.ghostdrainhunter.domain.SessionAnalysis
import com.example.ghostdrainhunter.domain.SessionAnalyzer
import com.example.ghostdrainhunter.domain.SessionVerdict
import com.example.ghostdrainhunter.ui.theme.StatusExcluded
import com.example.ghostdrainhunter.ui.theme.VerdictAboveLimit
import com.example.ghostdrainhunter.ui.theme.VerdictWellAboveLimit
import com.example.ghostdrainhunter.ui.theme.VerdictWithinLimit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    RATE_DESC,
    RATE_ASC
}

enum class HistoryFilter {
    ALL, VALID, TOO_SHORT, CHARGING
}

@Composable
fun HistoryScreen(
    sessions: List<ScreenOffSession>,
    thresholdPerHour: Double,
    minSessionMinutes: Int,
    trackingStartTime: Long = 0L
) {
    var historyFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }

    // Filter by trackingStartTime and analyze
    val analyzedSessions = remember(sessions, thresholdPerHour, minSessionMinutes, trackingStartTime) {
        sessions
            .filter { it.startTime >= trackingStartTime }
            .map { session ->
                SessionAnalyzer.analyze(
                    session = session,
                    thresholdPerHour = thresholdPerHour,
                    minSessionMinutes = minSessionMinutes
                )
            }
    }

    val filteredSessions = remember(analyzedSessions, historyFilter) {
        when (historyFilter) {
            HistoryFilter.ALL -> analyzedSessions
            HistoryFilter.VALID -> analyzedSessions.filter { it.status == AnalysisStatus.VALID }
            HistoryFilter.TOO_SHORT -> analyzedSessions.filter { it.status == AnalysisStatus.TOO_SHORT }
            HistoryFilter.CHARGING -> analyzedSessions.filter { it.status == AnalysisStatus.CHARGED }
        }
    }

    val sortedSessions = remember(filteredSessions, sortOption) {
        when (sortOption) {
            SortOption.DATE_DESC -> filteredSessions.sortedByDescending { it.session.startTime }
            SortOption.DATE_ASC -> filteredSessions.sortedBy { it.session.startTime }
            SortOption.RATE_DESC -> filteredSessions.sortedByDescending { it.ratePerHour }
            SortOption.RATE_ASC -> filteredSessions.sortedBy { it.ratePerHour }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status Filter Chips Row
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = historyFilter == HistoryFilter.ALL,
                    onClick = { historyFilter = HistoryFilter.ALL },
                    label = { Text(stringResource(R.string.filter_all)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            item {
                FilterChip(
                    selected = historyFilter == HistoryFilter.VALID,
                    onClick = { historyFilter = HistoryFilter.VALID },
                    label = { Text(stringResource(R.string.filter_valid)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VerdictWithinLimit.copy(alpha = 0.25f),
                        selectedLabelColor = VerdictWithinLimit
                    )
                )
            }
            item {
                FilterChip(
                    selected = historyFilter == HistoryFilter.TOO_SHORT,
                    onClick = { historyFilter = HistoryFilter.TOO_SHORT },
                    label = { Text(stringResource(R.string.filter_too_short)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StatusExcluded.copy(alpha = 0.2f),
                        selectedLabelColor = StatusExcluded
                    )
                )
            }
            item {
                FilterChip(
                    selected = historyFilter == HistoryFilter.CHARGING,
                    onClick = { historyFilter = HistoryFilter.CHARGING },
                    label = { Text(stringResource(R.string.filter_charging)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VerdictAboveLimit.copy(alpha = 0.2f),
                        selectedLabelColor = VerdictAboveLimit
                    )
                )
            }
            item {
                FilterChip(
                    selected = sortOption == SortOption.DATE_DESC || sortOption == SortOption.DATE_ASC,
                    onClick = {
                        sortOption = if (sortOption == SortOption.DATE_DESC) {
                            SortOption.DATE_ASC
                        } else {
                            SortOption.DATE_DESC
                        }
                    },
                    label = {
                        val arrow = if (sortOption == SortOption.DATE_ASC) "↑" else "↓"
                        Text("${stringResource(R.string.sort_by_date)} $arrow")
                    }
                )
            }
            item {
                FilterChip(
                    selected = sortOption == SortOption.RATE_DESC || sortOption == SortOption.RATE_ASC,
                    onClick = {
                        sortOption = if (sortOption == SortOption.RATE_DESC) {
                            SortOption.RATE_ASC
                        } else {
                            SortOption.RATE_DESC
                        }
                    },
                    label = {
                        val arrow = if (sortOption == SortOption.RATE_ASC) "↑" else "↓"
                        Text("${stringResource(R.string.sort_by_rate)} $arrow")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (sortedSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (historyFilter != HistoryFilter.ALL) {
                        stringResource(R.string.no_sessions_filtered)
                    } else {
                        stringResource(R.string.no_sessions_yet)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = sortedSessions,
                    key = { it.session.id }
                ) { analysis ->
                    HistoryItemCard(analysis = analysis)
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(analysis: SessionAnalysis) {
    val session = analysis.session
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val durationFormatted = remember(analysis.durationMillis) {
        val totalMins = analysis.durationMillis / 60_000
        val hrs = totalMins / 60
        val mins = totalMins % 60
        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    }

    val isExcluded = analysis.status != AnalysisStatus.VALID
    val cardColor = if (isExcluded) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.session_id_label, session.id),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExcluded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (analysis.isFlagged) {
                        Surface(
                            color = VerdictWellAboveLimit,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.flagged_badge),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = verdictColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(
                        R.string.session_start_format,
                        dateFormat.format(Date(session.startTime)),
                        session.startPercent
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isExcluded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (session.endTime != null && session.endPercent != null) {
                        stringResource(
                            R.string.session_end_format,
                            dateFormat.format(Date(session.endTime)),
                            session.endPercent
                        )
                    } else {
                        stringResource(R.string.in_progress)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isExcluded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.session_duration_format, durationFormatted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isExcluded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.session_lost_format, analysis.percentLost),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isExcluded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.session_rate_format, analysis.ratePerHour),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExcluded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else if (analysis.isFlagged) VerdictWellAboveLimit else MaterialTheme.colorScheme.onSurface
                )
            }

            if (isExcluded) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(
                            R.string.session_charging_label,
                            if (session.wasCharging) stringResource(R.string.yes) else stringResource(R.string.no)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    analysis.exclusionReason?.let { reason ->
                        Text(
                            text = stringResource(R.string.session_reason_label, reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
