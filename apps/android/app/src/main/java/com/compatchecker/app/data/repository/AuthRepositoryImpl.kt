package com.compatchecker.app.data.repository

import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.domain.model.AuthSession
import com.compatchecker.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class AuthRepositoryImpl(private val gateway: SupabaseGateway?) : AuthRepository {
    override val session: Flow<AuthSession?> = gateway?.authSession() ?: kotlinx.coroutines.flow.flowOf(null)

    override suspend fun currentSession(): AuthSession? = gateway?.currentSession()

    override suspend fun signIn(email: String, password: String) {
        val client = requireGateway()
        client.signIn(email.trim(), password)
    }

    override suspend fun signUp(email: String, password: String, displayName: String?) {
        val client = requireGateway()
        client.signUp(email.trim(), password, displayName)
    }

    override suspend fun signOut() {
        gateway?.signOut()
    }

    private fun requireGateway(): SupabaseGateway =
        gateway ?: error("Compat Checker is not configured. Add Supabase credentials first.")
}
