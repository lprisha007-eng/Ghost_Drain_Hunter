package com.example.ghostdrainhunter.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghostdrainhunter.data.capture.CaptureService
import com.example.ghostdrainhunter.data.db.ScreenOffSessionDao
import com.example.ghostdrainhunter.data.settings.SettingsStore
import com.example.ghostdrainhunter.domain.AnalysisStatus
import com.example.ghostdrainhunter.domain.GhostScoreCalculator
import com.example.ghostdrainhunter.domain.GhostScoreResult
import com.example.ghostdrainhunter.domain.PatternDetector
import com.example.ghostdrainhunter.domain.PatternResult
import com.example.ghostdrainhunter.domain.SessionAnalysis
import com.example.ghostdrainhunter.domain.SessionAnalyzer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Content(
        val scoreResult: GhostScoreResult,
        val patternResult: PatternResult,
        val latestSessionAnalysis: SessionAnalysis?,
        val isLatestSessionFlagged: Boolean,
        val thresholdPerHour: Double,
        val minSessionMinutes: Int,
        val isMonitoring: Boolean,
        val trackingStartTime: Long,
        val hasAnySessionsSinceTrackingStart: Boolean
    ) : DashboardUiState
}

class DashboardViewModel(
    private val sessionDao: ScreenOffSessionDao,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        sessionDao.observeAll(),
        settingsStore.settingsFlow,
        CaptureService.isMonitoring
    ) { allSessions, settings, isMonitoring ->
        val trackingStart = settings.trackingStartTime

        // Filter sessions by trackingStartTime
        val sessions = allSessions.filter { it.startTime >= trackingStart }
        val hasAnySessions = sessions.isNotEmpty()

        val scoreResult = GhostScoreCalculator.calculate(
            sessions = sessions,
            minSessionMinutes = settings.minSessionMinutes,
            trackingStartTime = trackingStart
        )

        val patternResult = PatternDetector.detectPattern(
            sessions = sessions,
            minSessionMinutes = settings.minSessionMinutes,
            trackingStartTime = trackingStart
        )

        val analyzedSessions = sessions
            .filter { it.endTime != null && it.endPercent != null }
            .map {
                SessionAnalyzer.analyze(
                    session = it,
                    thresholdPerHour = settings.thresholdPerHour,
                    minSessionMinutes = settings.minSessionMinutes
                )
            }

        val latestValidSession = analyzedSessions.firstOrNull { it.status == AnalysisStatus.VALID }
        val isLatestSessionFlagged = latestValidSession?.isFlagged == true

        DashboardUiState.Content(
            scoreResult = scoreResult,
            patternResult = patternResult,
            latestSessionAnalysis = latestValidSession ?: analyzedSessions.firstOrNull(),
            isLatestSessionFlagged = isLatestSessionFlagged,
            thresholdPerHour = settings.thresholdPerHour,
            minSessionMinutes = settings.minSessionMinutes,
            isMonitoring = isMonitoring,
            trackingStartTime = trackingStart,
            hasAnySessionsSinceTrackingStart = hasAnySessions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )
}
