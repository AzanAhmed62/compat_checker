package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compatchecker.CompatApplication
import com.example.compatchecker.core.preferences.ThemeMode
import com.example.compatchecker.ui.navigation.AppNavigation
import com.example.compatchecker.ui.theme.CompatCheckerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as CompatApplication).container

        setContent {
            val userPrefs by appContainer.userPreferences.userPreferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.example.compatchecker.core.preferences.UserPreferences()
            )

            CompatCheckerTheme(themeMode = userPrefs.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(container = appContainer)
                }
            }
        }
    }
}
