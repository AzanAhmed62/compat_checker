package com.compatchecker.app.domain.repository

import com.compatchecker.app.domain.model.AppNotification
import com.compatchecker.app.domain.model.AuthSession
import com.compatchecker.app.domain.model.Brand
import com.compatchecker.app.domain.model.Category
import com.compatchecker.app.domain.model.Comment
import com.compatchecker.app.domain.model.CommunityRequest
import com.compatchecker.app.domain.model.CompatibilityResult
import com.compatchecker.app.domain.model.CollectionDetail
import com.compatchecker.app.domain.model.CollectionSummary
import com.compatchecker.app.domain.model.Contribution
import com.compatchecker.app.domain.model.FollowResult
import com.compatchecker.app.domain.model.GuideDetail
import com.compatchecker.app.domain.model.GuideFeed
import com.compatchecker.app.domain.model.GuideSummary
import com.compatchecker.app.domain.model.LeaderboardEntry
import com.compatchecker.app.domain.model.ProductDetail
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.model.PublicProfile
import com.compatchecker.app.domain.model.Reputation
import com.compatchecker.app.domain.model.RuleDetail
import com.compatchecker.app.domain.model.SearchPage
import com.compatchecker.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface AuthRepository {
    val session: Flow<AuthSession?>
    suspend fun currentSession(): AuthSession?
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String, displayName: String?)
    suspend fun signOut()
}

interface CatalogRepository {
    suspend fun search(
        query: String,
        categorySlug: String?,
        brandSlug: String?,
        limit: Int,
        offset: Int,
    ): SearchPage
    suspend fun categories(): List<Category>
    suspend fun brands(): List<Brand>
    suspend fun product(id: String): ProductDetail
    suspend fun toggleFavorite(productId: String): Boolean
    suspend fun favorites(): List<ProductSummary>
    suspend fun cachedProduct(id: String): ProductDetail?
}

interface CompatibilityRepository {
    suspend fun check(productA: String, productB: String): CompatibilityResult
    suspend fun rule(ruleId: String): RuleDetail
    suspend fun castVote(ruleId: String, vote: String): RuleDetail
}

interface GuideRepository {
    suspend fun guides(): GuideFeed
    suspend fun guide(id: String): GuideDetail
}

interface CommunityRepository {
    suspend fun requests(): List<CommunityRequest>
    suspend fun createRequest(title: String, description: String, categoryId: String?): String
    suspend fun toggleRequestVote(requestId: String): Int
    suspend fun comments(targetType: String, targetId: String): List<Comment>
    suspend fun addComment(targetType: String, targetId: String, body: String): Comment
    suspend fun report(targetType: String, targetId: String, reason: String, details: String?)
}

interface SocialRepository {
    suspend fun collections(): List<CollectionSummary>
    suspend fun collection(id: String): CollectionDetail
    suspend fun createCollection(title: String, description: String?, isPublic: Boolean): String
    suspend fun updateCollection(
        collectionId: String,
        title: String?,
        description: String?,
        isPublic: Boolean?,
    ): Boolean
    suspend fun addToCollection(collectionId: String, productId: String, note: String?): Boolean
    suspend fun removeFromCollection(collectionId: String, productId: String): Boolean
    suspend fun deleteCollection(collectionId: String): Boolean
    suspend fun publicProfile(userId: String): PublicProfile
    suspend fun toggleFollow(userId: String): FollowResult
    suspend fun leaderboard(): List<LeaderboardEntry>
}

interface ProfileRepository {
    suspend fun me(): UserProfile
    suspend fun updateProfile(displayName: String?, bio: String?, locale: String?): UserProfile
    suspend fun reputation(): Reputation
    suspend fun contributions(): List<Contribution>
    suspend fun submitContribution(kind: String, payload: JsonObject): String
    suspend fun submitProductCorrection(productId: String, patch: JsonObject): String
    suspend fun notifications(): List<AppNotification>
    suspend fun markNotificationRead(id: String)
    suspend fun markAllNotificationsRead()
    suspend fun requestAccountDeletion(): String
    suspend fun cancelAccountDeletion()
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setThemeMode(mode: String)
    suspend fun setLanguage(language: String)
}

data class UserPreferences(val themeMode: String = "system", val language: String = "system")
