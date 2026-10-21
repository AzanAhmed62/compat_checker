package com.example.compatchecker.core.config

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
    private const val TAG = "SupabaseConfig"

    val configuredUrl: String = BuildConfig.SUPABASE_URL
    val configuredAnonKey: String = BuildConfig.SUPABASE_ANON_KEY

    /**
     * Determines whether real Supabase credentials have been provided in BuildConfig
     * via .env or the Secrets panel.
     */
    val isConfigured: Boolean by lazy {
        val url = configuredUrl.trim()
        val key = configuredAnonKey.trim()
        url.isNotBlank() &&
                !url.startsWith("YOUR_") &&
                (url.startsWith("https://") || url.startsWith("http://")) &&
                key.isNotBlank() &&
                !key.startsWith("YOUR_")
    }

    private var _client: SupabaseClient? = null

    val client: SupabaseClient?
        get() {
            if (!isConfigured) return null
            if (_client == null) {
                synchronized(this) {
                    if (_client == null) {
                        try {
                            _client = createSupabaseClient(
                                supabaseUrl = configuredUrl.trim(),
                                supabaseKey = configuredAnonKey.trim()
                            ) {
                                install(Auth)
                                install(Postgrest)
                                install(Realtime)
                                install(Storage)
                            }
                            Log.i(TAG, "Supabase client successfully initialized for $configuredUrl")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to initialize Supabase client: ${e.message}", e)
                        }
                    }
                }
            }
            return _client
        }

    fun getConfigurationNotice(): String {
        return if (isConfigured) {
            "Connected to Supabase project at ${configuredUrl.take(24)}..."
        } else {
            "Demonstration Mode: Live Supabase backend is unconfigured. Set SUPABASE_URL and SUPABASE_ANON_KEY in .env to connect."
        }
    }
}
