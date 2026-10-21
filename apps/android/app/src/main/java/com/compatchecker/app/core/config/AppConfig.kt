package com.compatchecker.app.core.config

import android.util.Base64
import com.compatchecker.app.BuildConfig
import java.net.URI

/**
 * Configuration enters the app only through BuildConfig, populated from local
 * properties or environment variables at build time. A service-role key must
 * never be supplied here.
 */
data class AppConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
) {
    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    val containsServiceRoleKey: Boolean
        get() = looksLikeServiceRole(supabaseAnonKey)

    val hasValidUrl: Boolean
        get() = runCatching {
            val uri = URI(supabaseUrl)
            val https = uri.scheme == "https" && !uri.host.isNullOrBlank()
            val debugLocalHttp = BuildConfig.DEBUG &&
                uri.scheme == "http" &&
                uri.host in setOf("10.0.2.2", "localhost", "127.0.0.1")
            https || debugLocalHttp
        }.getOrDefault(false)

    val isUsable: Boolean
        get() = isConfigured && hasValidUrl && !containsServiceRoleKey

    val configurationError: String?
        get() = when {
            !isConfigured -> "Supabase URL and anon key are not configured."
            containsServiceRoleKey -> "A service-role or secret key was supplied. Use the public anon/publishable key only."
            !hasValidUrl -> "SUPABASE_URL must be HTTPS. Local HTTP is accepted only in debug builds for emulator hosts."
            else -> null
        }

    companion object {
        fun fromBuildConfig(): AppConfig = AppConfig(
            supabaseUrl = BuildConfig.SUPABASE_URL.trim(),
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim(),
        )

        private fun looksLikeServiceRole(key: String): Boolean {
            if (key.startsWith("sb_secret_", ignoreCase = true)) return true
            val payload = key.split('.').getOrNull(1) ?: return false
            return runCatching {
                val decoded = Base64.decode(
                    payload.replace('-', '+').replace('_', '/'),
                    Base64.NO_WRAP or Base64.URL_SAFE,
                )
                val text = decoded.decodeToString()
                text.contains("\"role\":\"service_role\"") || text.contains("\"role\": \"service_role\"")
            }.getOrDefault(false)
        }
    }
}
