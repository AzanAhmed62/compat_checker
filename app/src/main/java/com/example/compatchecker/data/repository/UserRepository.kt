package com.example.compatchecker.data.repository

import com.example.compatchecker.core.config.SupabaseConfig
import com.example.compatchecker.core.preferences.UserPreferencesRepository
import com.example.compatchecker.data.remote.SupabaseRemoteDataSource
import com.example.compatchecker.domain.model.UserProfile
import com.example.compatchecker.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserRepository {
    val currentUserProfile: Flow<UserProfile>
    suspend fun signIn(email: String, pass: String): Result<UserProfile>
    suspend fun signUp(email: String, pass: String, displayName: String): Result<UserProfile>
    suspend fun signOut()
    suspend fun requestAccountDeletion(): Boolean
}

class UserRepositoryImpl(
    private val remoteDataSource: SupabaseRemoteDataSource,
    private val userPreferences: UserPreferencesRepository
) : UserRepository {

    override val currentUserProfile: Flow<UserProfile> = userPreferences.userPreferencesFlow.map { prefs ->
        if (prefs.userId != null) {
            UserProfile(
                id = prefs.userId,
                email = prefs.userEmail ?: "user@example.com",
                displayName = prefs.userDisplayName ?: "Verified Repair Tech",
                role = UserRole.REPAIR_TECHNICIAN,
                contributionsCount = 6,
                verifiedBadges = listOf("Micro-Soldering L3", "ESD Certified", "Catalog Contributor")
            )
        } else {
            UserProfile(
                id = "guest",
                email = "",
                displayName = "Guest Technician",
                role = UserRole.GUEST,
                contributionsCount = 0,
                verifiedBadges = emptyList()
            )
        }
    }

    override suspend fun signIn(email: String, pass: String): Result<UserProfile> {
        if (SupabaseConfig.isConfigured) {
            val res = remoteDataSource.signInWithEmail(email, pass)
            return if (res.isSuccess) {
                val userId = res.getOrNull() ?: "auth_${System.currentTimeMillis()}"
                val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                userPreferences.setUserSession(userId, email, name)
                Result.success(UserProfile(userId, email, name, UserRole.REPAIR_TECHNICIAN, 1, listOf("Catalog Contributor")))
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("Failed to sign in"))
            }
        } else {
            // Local Guest Demo Login
            val pseudoId = "demo_user_${System.currentTimeMillis() % 1000}"
            val name = email.substringBefore("@").ifBlank { "Repair Tech" }.replaceFirstChar { it.uppercase() }
            userPreferences.setUserSession(pseudoId, email, name)
            return Result.success(UserProfile(pseudoId, email, name, UserRole.REPAIR_TECHNICIAN, 2, listOf("Field Verified")))
        }
    }

    override suspend fun signUp(email: String, pass: String, displayName: String): Result<UserProfile> {
        if (SupabaseConfig.isConfigured) {
            val res = remoteDataSource.signUpWithEmail(email, pass)
            return if (res.isSuccess) {
                val userId = res.getOrNull() ?: "auth_${System.currentTimeMillis()}"
                userPreferences.setUserSession(userId, email, displayName)
                Result.success(UserProfile(userId, email, displayName, UserRole.CONTRIBUTOR, 0, listOf("New Contributor")))
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("Failed to register"))
            }
        } else {
            val pseudoId = "demo_user_${System.currentTimeMillis() % 1000}"
            userPreferences.setUserSession(pseudoId, email, displayName)
            return Result.success(UserProfile(pseudoId, email, displayName, UserRole.CONTRIBUTOR, 0, listOf("New Contributor")))
        }
    }

    override suspend fun signOut() {
        if (SupabaseConfig.isConfigured) {
            remoteDataSource.signOut()
        }
        userPreferences.clearSession()
    }

    override suspend fun requestAccountDeletion(): Boolean {
        signOut()
        return true
    }
}
