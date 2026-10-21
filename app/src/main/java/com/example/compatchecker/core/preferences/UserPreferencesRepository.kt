package com.example.compatchecker.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "compat_user_prefs")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val forceOfflineMode: Boolean = false,
    val showTechnicalDetailsExpanded: Boolean = true,
    val selectedLanguage: String = "English",
    val lastSyncTimestamp: Long = 0L,
    val userId: String? = null,
    val userEmail: String? = null,
    val userDisplayName: String? = null
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FORCE_OFFLINE = booleanPreferencesKey("force_offline")
        val SHOW_TECH_DETAILS = booleanPreferencesKey("show_tech_details")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val LAST_SYNC = longPreferencesKey("last_sync_timestamp")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name
            val theme = try {
                ThemeMode.valueOf(themeStr)
            } catch (e: Exception) {
                ThemeMode.DARK
            }
            UserPreferences(
                themeMode = theme,
                forceOfflineMode = preferences[PreferencesKeys.FORCE_OFFLINE] ?: false,
                showTechnicalDetailsExpanded = preferences[PreferencesKeys.SHOW_TECH_DETAILS] ?: true,
                selectedLanguage = preferences[PreferencesKeys.SELECTED_LANGUAGE] ?: "English",
                lastSyncTimestamp = preferences[PreferencesKeys.LAST_SYNC] ?: 0L,
                userId = preferences[PreferencesKeys.USER_ID],
                userEmail = preferences[PreferencesKeys.USER_EMAIL],
                userDisplayName = preferences[PreferencesKeys.USER_DISPLAY_NAME]
            )
        }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setForceOfflineMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.FORCE_OFFLINE] = enabled
        }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SELECTED_LANGUAGE] = language
        }
    }

    suspend fun updateLastSync(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_SYNC] = timestamp
        }
    }

    suspend fun setUserSession(id: String?, email: String?, displayName: String?) {
        context.dataStore.edit { prefs ->
            if (id != null) prefs[PreferencesKeys.USER_ID] = id else prefs.remove(PreferencesKeys.USER_ID)
            if (email != null) prefs[PreferencesKeys.USER_EMAIL] = email else prefs.remove(PreferencesKeys.USER_EMAIL)
            if (displayName != null) prefs[PreferencesKeys.USER_DISPLAY_NAME] = displayName else prefs.remove(PreferencesKeys.USER_DISPLAY_NAME)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.USER_ID)
            prefs.remove(PreferencesKeys.USER_EMAIL)
            prefs.remove(PreferencesKeys.USER_DISPLAY_NAME)
        }
    }
}
