package com.example.ghostdrainhunter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ghostdrainhunter.data.capture.CaptureService
import com.example.ghostdrainhunter.data.settings.UserSettings
import com.example.ghostdrainhunter.ui.dashboard.DashboardScreen
import com.example.ghostdrainhunter.ui.dashboard.DashboardViewModel
import com.example.ghostdrainhunter.ui.history.HistoryScreen
import com.example.ghostdrainhunter.ui.onboarding.OnboardingScreen
import com.example.ghostdrainhunter.ui.report.ReportScreen
import com.example.ghostdrainhunter.ui.settings.SettingsScreen
import com.example.ghostdrainhunter.ui.theme.GhostDrainHunterTheme
import com.example.ghostdrainhunter.ui.trends.TrendsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GhostDrainApplication
        val container = app.container

        setContent {
            GhostDrainHunterTheme {
                MainApp(
                    container = container,
                    onStartMonitoring = { startMonitoringService() },
                    onStopMonitoring = { stopMonitoringService() }
                )
            }
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun MainApp(
    container: AppContainer,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    val settings by container.settingsStore.settingsFlow.collectAsState(initial = UserSettings())
    val sessions by container.sessionDao.observeAll().collectAsState(initial = emptyList())
    val isMonitoring by CaptureService.isMonitoring.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        if (settings.isMonitoringUserEnabled && !isMonitoring) {
            onStartMonitoring()
        }
    }

    // Auto-start monitoring when MainActivity opens and user hasn't explicitly disabled it
    LaunchedEffect(settings.isMonitoringUserEnabled, isMonitoring) {
        if (settings.isMonitoringUserEnabled && !isMonitoring) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onStartMonitoring()
                }
            } else {
                onStartMonitoring()
            }
        }
    }

    val dashboardViewModel: DashboardViewModel = viewModel {
        DashboardViewModel(
            sessionDao = container.sessionDao,
            settingsStore = container.settingsStore
        )
    }
    val dashboardState by dashboardViewModel.uiState.collectAsState()

    // Show onboarding on first launch until completed or skipped
    if (!settings.isOnboardingCompleted) {
        OnboardingScreen(
            onComplete = {
                coroutineScope.launch {
                    container.settingsStore.setOnboardingCompleted(true)
                }
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_dashboard)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_history)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_trends)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_report)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    uiState = dashboardState,
                    onStartMonitoring = {
                        coroutineScope.launch { container.settingsStore.setMonitoringUserEnabled(true) }
                        onStartMonitoring()
                    },
                    onStopMonitoring = {
                        coroutineScope.launch { container.settingsStore.setMonitoringUserEnabled(false) }
                        onStopMonitoring()
                    },
                    onNavigateToSettings = { selectedTab = 4 }
                )
                1 -> HistoryScreen(
                    sessions = sessions,
                    thresholdPerHour = settings.thresholdPerHour,
                    minSessionMinutes = settings.minSessionMinutes,
                    trackingStartTime = settings.trackingStartTime
                )
                2 -> TrendsScreen(
                    sessions = sessions,
                    thresholdPerHour = settings.thresholdPerHour,
                    minSessionMinutes = settings.minSessionMinutes,
                    trackingStartTime = settings.trackingStartTime
                )
                3 -> ReportScreen(
                    sessions = sessions,
                    thresholdPerHour = settings.thresholdPerHour,
                    minSessionMinutes = settings.minSessionMinutes,
                    trackingStartTime = settings.trackingStartTime
                )
                4 -> SettingsScreen(
                    container = container,
                    settings = settings
                )
            }
        }
    }
}
