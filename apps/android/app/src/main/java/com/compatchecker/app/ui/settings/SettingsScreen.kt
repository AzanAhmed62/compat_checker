package com.compatchecker.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compatchecker.app.BuildConfig
import com.compatchecker.app.R
import com.compatchecker.app.domain.repository.PreferencesRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: PreferencesRepository,
    profiles: ProfileRepository,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = compatViewModel {
        SettingsViewModel(preferences, profiles)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    LaunchedEffect(state.profile?.id) {
        state.profile?.let {
            displayName = it.displayName
            bio = it.bio.orEmpty()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingPane(Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle(stringResource(R.string.settings_appearance))
                ThemeChoice(
                    label = stringResource(R.string.settings_theme_system),
                    selected = state.preferences.themeMode == "system",
                    onClick = { viewModel.setTheme("system") },
                )
                ThemeChoice(
                    label = stringResource(R.string.settings_theme_light),
                    selected = state.preferences.themeMode == "light",
                    onClick = { viewModel.setTheme("light") },
                )
                ThemeChoice(
                    label = stringResource(R.string.settings_theme_dark),
                    selected = state.preferences.themeMode == "dark",
                    onClick = { viewModel.setTheme("dark") },
                )

                SectionTitle(stringResource(R.string.settings_language))
                ThemeChoice(
                    label = stringResource(R.string.settings_language_system),
                    selected = state.preferences.language == "system",
                    onClick = {
                        viewModel.setLanguage("system")
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    },
                )
                ThemeChoice(
                    label = stringResource(R.string.settings_language_english),
                    selected = state.preferences.language == "en",
                    onClick = {
                        viewModel.setLanguage("en")
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                    },
                )
                ThemeChoice(
                    label = stringResource(R.string.settings_language_urdu),
                    selected = state.preferences.language == "ur",
                    onClick = {
                        viewModel.setLanguage("ur")
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ur"))
                    },
                )

                if (state.profile != null) {
                    SectionTitle(stringResource(R.string.settings_account))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(80) },
                        label = { Text(stringResource(R.string.settings_display_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it.take(500) },
                        label = { Text(stringResource(R.string.settings_bio)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Button(
                        onClick = { viewModel.saveProfile(displayName, bio) },
                        enabled = !state.saving && displayName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_save)) }
                }

                SectionTitle(stringResource(R.string.settings_privacy))
                Text(
                    stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
