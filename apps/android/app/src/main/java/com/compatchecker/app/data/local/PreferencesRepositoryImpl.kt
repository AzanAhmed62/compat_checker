package com.compatchecker.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.compatchecker.app.domain.repository.PreferencesRepository
import com.compatchecker.app.domain.repository.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "compat_checker_settings")

class PreferencesRepositoryImpl(private val context: Context) : PreferencesRepository {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val languageKey = stringPreferencesKey("language")

    override val preferences: Flow<UserPreferences> = context.settingsDataStore.data.map { values ->
        UserPreferences(
            themeMode = values[themeKey] ?: "system",
            language = values[languageKey] ?: "system",
        )
    }

    override suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { it[themeKey] = mode }
    }

    override suspend fun setLanguage(language: String) {
        context.settingsDataStore.edit { it[languageKey] = language }
    }
}
