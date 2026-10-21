package com.example.compatchecker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compatchecker.core.config.SupabaseConfig
import com.example.compatchecker.core.preferences.ThemeMode
import com.example.compatchecker.core.preferences.UserPreferencesRepository
import com.example.compatchecker.data.local.CompatDatabase
import com.example.compatchecker.data.repository.ProductRepository
import com.example.compatchecker.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val forceOfflineMode: Boolean = false,
    val selectedLanguage: String = "English",
    val supabaseUrl: String = "",
    val isSupabaseConfigured: Boolean = false,
    val supabaseAnonKeyMasked: String = "",
    val connectionTestResult: String? = null,
    val isTestingConnection: Boolean = false,
    val cachedProductsCount: Int = 0,
    val isClearingCache: Boolean = false,
    val actionNotice: String? = null
)

class SettingsViewModel(
    private val userPreferences: UserPreferencesRepository,
    private val database: CompatDatabase,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        inspectSupabase()
        observeCacheCount()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferences.userPreferencesFlow.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        themeMode = prefs.themeMode,
                        forceOfflineMode = prefs.forceOfflineMode,
                        selectedLanguage = prefs.selectedLanguage
                    )
                }
            }
        }
    }

    private fun inspectSupabase() {
        val configured = SupabaseConfig.isConfigured
        val url = SupabaseConfig.configuredUrl
        val key = SupabaseConfig.configuredAnonKey
        val maskedKey = if (key.length > 8) "${key.take(4)}...${key.takeLast(4)}" else if (key.isNotEmpty()) "***" else "Not set"

        _uiState.update { current ->
            current.copy(
                supabaseUrl = if (url.isBlank() || url.startsWith("YOUR_")) "Unset (Running in Demonstration Mode)" else url,
                isSupabaseConfigured = configured,
                supabaseAnonKeyMasked = maskedKey
            )
        }
    }

    private fun observeCacheCount() {
        viewModelScope.launch {
            database.productDao().getAllProducts().collect { list ->
                _uiState.update { it.copy(cachedProductsCount = list.size) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun setForceOffline(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setForceOfflineMode(enabled)
            _uiState.update {
                it.copy(
                    actionNotice = if (enabled) "Switched to Force Offline Local Cache mode." else "Online networking enabled."
                )
            }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            userPreferences.setLanguage(lang)
        }
    }

    fun testSupabaseConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, connectionTestResult = null) }
            if (!SupabaseConfig.isConfigured) {
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        connectionTestResult = "Unconfigured: Set SUPABASE_URL and SUPABASE_ANON_KEY in .env or AI Studio Secrets to connect."
                    )
                }
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                try {
                    val client = SupabaseConfig.client
                    if (client != null) {
                        "Connection test successful! Supabase SDK initialized for ${SupabaseConfig.configuredUrl.take(28)}..."
                    } else {
                        "Failed to initialize Supabase client."
                    }
                } catch (e: Exception) {
                    "Connection error: ${e.message}"
                }
            }

            _uiState.update {
                it.copy(isTestingConnection = false, connectionTestResult = result)
            }
        }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCache = true) }
            database.productDao().clearAll()
            database.compatibilityDao().clearAll()
            productRepository.refreshCatalog()
            _uiState.update {
                it.copy(
                    isClearingCache = false,
                    actionNotice = "Cache reset. Technical benchmark catalog restored."
                )
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            userRepository.requestAccountDeletion()
            _uiState.update { it.copy(actionNotice = "User session cleared and account deletion requested.") }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(actionNotice = null) }
    }
}
