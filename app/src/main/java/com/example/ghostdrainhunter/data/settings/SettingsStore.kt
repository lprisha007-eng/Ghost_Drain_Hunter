package com.example.ghostdrainhunter.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val thresholdPerHour: Double = 3.0,
    val minSessionMinutes: Int = 15,
    val alertsEnabled: Boolean = true,
    val isOnboardingCompleted: Boolean = false,
    val trackingStartTime: Long = 0L,
    val isCleanStartDone: Boolean = false,
    val isMonitoringUserEnabled: Boolean = true
)

class SettingsStore(private val context: Context) {
    companion object {
        val KEY_THRESHOLD = doublePreferencesKey("drain_threshold_per_hour")
        val KEY_MIN_DURATION = intPreferencesKey("min_session_minutes")
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_TRACKING_START_TIME = longPreferencesKey("tracking_start_time")
        val KEY_CLEAN_START_DONE = booleanPreferencesKey("clean_start_done_v1")
        val KEY_MONITORING_USER_ENABLED = booleanPreferencesKey("monitoring_user_enabled")

        const val DEFAULT_THRESHOLD = 3.0
        const val DEFAULT_MIN_DURATION = 15
        const val DEFAULT_ALERTS_ENABLED = true
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val threshold = preferences[KEY_THRESHOLD] ?: DEFAULT_THRESHOLD
            val minDuration = preferences[KEY_MIN_DURATION] ?: DEFAULT_MIN_DURATION
            val alerts = preferences[KEY_ALERTS_ENABLED] ?: DEFAULT_ALERTS_ENABLED
            val onboarding = preferences[KEY_ONBOARDING_COMPLETED] ?: false
            val trackingStart = preferences[KEY_TRACKING_START_TIME] ?: 0L
            val cleanStart = preferences[KEY_CLEAN_START_DONE] ?: false
            val monitoringEnabled = preferences[KEY_MONITORING_USER_ENABLED] ?: true
            UserSettings(
                thresholdPerHour = threshold,
                minSessionMinutes = minDuration,
                alertsEnabled = alerts,
                isOnboardingCompleted = onboarding,
                trackingStartTime = trackingStart,
                isCleanStartDone = cleanStart,
                isMonitoringUserEnabled = monitoringEnabled
            )
        }

    suspend fun setThreshold(threshold: Double) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THRESHOLD] = threshold
        }
    }

    suspend fun setMinSessionMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MIN_DURATION] = minutes
        }
    }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setTrackingStartTime(timeMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TRACKING_START_TIME] = timeMillis
        }
    }

    suspend fun setCleanStartDone(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CLEAN_START_DONE] = done
        }
    }

    suspend fun setMonitoringUserEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MONITORING_USER_ENABLED] = enabled
        }
    }

    suspend fun startFresh(now: Long = System.currentTimeMillis()) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TRACKING_START_TIME] = now
            preferences[KEY_CLEAN_START_DONE] = true
        }
    }
}
