package com.example.maps123.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class ThemePrefs(private val context: Context) {

    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    private val VOICE_ENABLED_KEY = booleanPreferencesKey("voice_enabled")
    private val GHOST_MODE_KEY = booleanPreferencesKey("ghost_mode")
    private val LAST_ANNOUNCEMENT_TIME_KEY = longPreferencesKey("last_announcement_time")

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE_KEY] ?: false
    }
    
    val isVoiceEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[VOICE_ENABLED_KEY] ?: false
    }

    val isGhostMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[GHOST_MODE_KEY] ?: false
    }

    val lastAnnouncementTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_ANNOUNCEMENT_TIME_KEY] ?: 0L
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }
    
    suspend fun saveVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VOICE_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveGhostMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[GHOST_MODE_KEY] = enabled
        }
    }

    suspend fun saveLastAnnouncementTime(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_ANNOUNCEMENT_TIME_KEY] = timestamp
        }
    }
}
