package com.example.ghostdrainhunter.ui.report

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.ghostdrainhunter.R
import com.example.ghostdrainhunter.data.db.ScreenOffSession
import com.example.ghostdrainhunter.domain.GhostScoreResult
import com.example.ghostdrainhunter.report.PdfReportWriter
import com.example.ghostdrainhunter.report.ReportBuilder
import com.example.ghostdrainhunter.report.ReportData
import com.example.ghostdrainhunter.ui.theme.GhostPrimary
import com.example.ghostdrainhunter.ui.theme.VerdictAboveLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun ReportScreen(
    sessions: List<ScreenOffSession>,
    thresholdPerHour: Double,
    minSessionMinutes: Int,
    trackingStartTime: Long = 0L
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val is24Hour = remember { DateFormat.is24HourFormat(context) }

    var selectedPeriodDays by remember { mutableIntStateOf(7) }
    var isGenerating by remember { mutableStateOf(false) }

    val reportData = remember(sessions, selectedPeriodDays, thresholdPerHour, minSessionMinutes, is24Hour, trackingStartTime) {
        ReportBuilder.buildReport(
            sessions = sessions,
            periodDays = selectedPeriodDays,
            thresholdPerHour = thresholdPerHour,
            minSessionMinutes = minSessionMinutes,
            is24Hour = is24Hour,
            trackingStartTime = trackingStartTime
        )
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                isGenerating = true
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val writer = PdfReportWriter(context)
                        writer.writeReport(reportData, outputStream)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.pdf_saved_success, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isGenerating = false
                }
            }
        }
    }

    fun sharePdfReport() {
        if (reportData.isEmpty) return
        coroutineScope.launch(Dispatchers.IO) {
            isGenerating = true
            try {
                val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
                val reportFile = File(reportsDir, "GhostDrain_Report_${reportData.periodDays}d.pdf")

                reportFile.outputStream().use { outputStream ->
                    val writer = PdfReportWriter(context)
                    writer.writeReport(reportData, outputStream)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    reportFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Ghost Drain Hunter Battery Report")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Share Battery Report"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isGenerating = false
            }
        }
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
            text = stringResource(R.string.report_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 7 / 30 Day Period Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = selectedPeriodDays == 7,
                onClick = { selectedPeriodDays = 7 },
                label = { Text(stringResource(R.string.period_7_days)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GhostPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = GhostPrimary
                )
            )

            FilterChip(
                selected = selectedPeriodDays == 30,
                onClick = { selectedPeriodDays = 30 },
                label = { Text(stringResource(R.string.period_30_days)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GhostPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = GhostPrimary
                )
            )
        }

        if (reportData.isEmpty) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.empty_report_msg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Summary Preview Card
            ReportSummaryPreviewCard(reportData = reportData)

            // Export Actions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                createDocumentLauncher.launch("GhostDrain_Report_${reportData.periodDays}d.pdf")
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isGenerating
                        ) {
                            Text(stringResource(R.string.save_pdf_btn))
                        }

                        OutlinedButton(
                            onClick = { sharePdfReport() },
                            modifier = Modifier.weight(1f),
                            enabled = !isGenerating
                        ) {
                            Text(stringResource(R.string.share_pdf_btn))
                        }
                    }

                    if (isGenerating) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.generating_pdf),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Top Sessions Preview
            if (reportData.topHighestDrainSessions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Top Highest-Drain Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        reportData.topHighestDrainSessions.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "${item.dateFormatted} (${item.timeRangeFormatted})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Duration: ${item.durationFormatted} • Lost: ${item.percentLost}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.1f %%/hr", item.ratePerHour),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = VerdictAboveLimit
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ReportSummaryPreviewCard(reportData: ReportData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            val scoreText = when (val score = reportData.scoreResult) {
                is GhostScoreResult.NotEnoughData -> "Not enough data"
                is GhostScoreResult.Score -> "${score.score}/100 (${score.label})"
            }

            Text(
                text = "Summary Preview (${reportData.periodDays} Days)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ghost Drain Score:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(scoreText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Idle Time:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format(Locale.US, "%.1f h", reportData.totalValidIdleHours), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Average Idle Rate:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format(Locale.US, "%.2f %%/hr", reportData.avgDrainRatePerHour), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sessions Above Limit:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${reportData.sessionsAboveThresholdCount} of ${reportData.validSessionsCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = reportData.patternLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reportData.weekVsWeekSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
