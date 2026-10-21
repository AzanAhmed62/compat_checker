package com.compatchecker.app.data.remote

import com.compatchecker.app.core.config.AppConfig
import com.compatchecker.app.domain.model.AuthSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.auth.status.SessionStatus

class SupabaseGateway(config: AppConfig) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = config.supabaseUrl,
        supabaseKey = config.supabaseAnonKey,
    ) {
        install(Auth)
        install(Postgrest)
    }

    val baseUrl: String = config.supabaseUrl.trimEnd('/')

    fun publicMediaUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("https://") || path.startsWith("http://")) return path
        return "$baseUrl/storage/v1/object/public/media/${path.trimStart('/')}"
    }

    fun authSession(): Flow<AuthSession?> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> {
                val user = status.session.user
                AuthSession(
                    userId = user?.id.orEmpty(),
                    email = user?.email,
                ).takeIf { it.userId.isNotBlank() }
            }
            else -> null
        }
    }

    suspend fun currentSession(): AuthSession? {
        val session = client.auth.currentSessionOrNull() ?: return null
        val user = session.user ?: return null
        return AuthSession(user.id, user.email)
    }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String?) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            if (!displayName.isNullOrBlank()) {
                data = buildJsonObject { put("display_name", displayName.trim()) }
            }
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }
}
