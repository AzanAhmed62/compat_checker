package com.compatchecker.app.domain.model

data class ProductSummary(
    val id: String,
    val slug: String,
    val name: String,
    val modelNumber: String?,
    val brandName: String,
    val brandSlug: String,
    val categoryName: String,
    val categorySlug: String,
    val summary: String?,
    val imageUrl: String?,
    val ruleCount: Int,
)

data class ProductSpec(val label: String, val value: String, val unit: String?, val critical: Boolean)

data class MediaItem(val url: String, val alt: String?)

data class ProductDetail(
    val id: String,
    val slug: String,
    val name: String,
    val modelNumber: String?,
    val releaseYear: Int?,
    val summary: String?,
    val technicalNotes: String?,
    val connectorType: String?,
    val formFactor: String?,
    val brandName: String,
    val brandSlug: String,
    val categoryName: String,
    val categorySlug: String,
    val aliases: List<String>,
    val specs: List<ProductSpec>,
    val media: List<MediaItem>,
    val rules: List<ProductRule>,
    val guides: List<GuideLink>,
    val commentCount: Int,
    val isFavorite: Boolean,
    val offline: Boolean = false,
)

data class ProductRule(
    val id: String,
    val relationship: String,
    val verification: String,
    val confidence: Int,
    val riskLevel: String?,
    val summary: String?,
    val otherProductId: String,
    val otherProductName: String,
    val evidenceCount: Int,
    val confirmCount: Int,
    val disputeCount: Int,
)

data class Evidence(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val publisher: String?,
    val excerpt: String?,
    val evidenceType: String,
    val capturedAt: String?,
)

data class CheckedRule(
    val id: String,
    val relationship: String,
    val verification: String,
    val confidence: Int,
    val riskLevel: String?,
    val summary: String?,
    val technicalDetails: String?,
    val requiredModifications: String?,
    val subjectProductId: String,
    val subjectProductName: String,
    val objectProductId: String,
    val objectProductName: String,
    val direction: String,
    val evidence: List<Evidence>,
    val confirmCount: Int,
    val disputeCount: Int,
)

data class CompatibilityResult(
    val known: Boolean,
    val outcome: String,
    val sameProduct: Boolean,
    val ruleCount: Int,
    val rules: List<CheckedRule>,
    val hasIncompatibility: Boolean,
    val checkedAt: String?,
)

data class RuleDetail(
    val id: String,
    val relationship: String,
    val verification: String,
    val confidence: Int,
    val riskLevel: String?,
    val summary: String?,
    val technicalDetails: String?,
    val requiredModifications: String?,
    val subjectProductId: String,
    val subjectProductName: String,
    val subjectProductSlug: String,
    val subjectBrandName: String,
    val objectProductId: String,
    val objectProductName: String,
    val objectProductSlug: String,
    val objectBrandName: String,
    val evidenceCount: Int,
    val confirmCount: Int,
    val disputeCount: Int,
    val myVote: String?,
    val evidence: List<Evidence>,
)

data class GuideLink(val id: String, val slug: String, val title: String, val summary: String?, val difficulty: String)

data class GuideSummary(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String?,
    val bodyMarkdown: String,
    val difficulty: String,
    val productId: String?,
    val productName: String?,
    val authorName: String,
    val stepCount: Int,
    val publishedAt: String?,
)

data class GuideStep(val id: String, val position: Int, val title: String, val body: String, val imageUrl: String?)

data class GuideDetail(
    val guide: GuideSummary,
    val steps: List<GuideStep>,
    val commentCount: Int,
    val offline: Boolean = false,
)

data class GuideFeed(val guides: List<GuideSummary>, val offline: Boolean = false)

data class Category(val id: String, val slug: String, val name: String)

data class Brand(val id: String, val slug: String, val name: String)

data class CommunityRequest(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val voteCount: Int,
    val categoryName: String?,
    val createdAt: String?,
)

data class Comment(
    val id: String,
    val parentId: String?,
    val body: String,
    val username: String,
    val displayName: String,
    val role: String,
    val avatarUrl: String?,
    val createdAt: String?,
)

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String?,
    val role: String,
    val accountStatus: String,
    val locale: String,
    val reputationPoints: Int,
    val reputationTier: String,
    val unreadNotifications: Int,
    val favoriteCount: Int,
    val canContribute: Boolean,
    val canModerate: Boolean,
    val canAdmin: Boolean,
    val deletionRequestedAt: String?,
)

data class ReputationEntry(val id: String, val delta: Int, val reason: String, val createdAt: String?)

data class Reputation(val points: Int, val tier: String, val entries: List<ReputationEntry>)

data class Contribution(val id: String, val kind: String, val status: String, val reviewNote: String?, val createdAt: String?, val reviewedAt: String?)

data class AppNotification(val id: String, val type: String, val title: String, val body: String?, val link: String?, val readAt: String?, val createdAt: String?)

data class SearchPage(val items: List<ProductSummary>, val total: Int, val offline: Boolean = false)

data class AuthSession(val userId: String, val email: String?)

data class CollectionSummary(
    val id: String,
    val title: String,
    val description: String?,
    val isPublic: Boolean,
    val itemCount: Int,
    val createdAt: String?,
    val updatedAt: String?,
)

data class CollectionItem(
    val productId: String,
    val productName: String,
    val productSlug: String,
    val modelNumber: String?,
    val summary: String?,
    val brandName: String,
    val brandSlug: String,
    val categoryName: String,
    val categorySlug: String,
    val imageUrl: String?,
    val note: String?,
    val addedAt: String?,
)

data class CollectionDetail(
    val id: String,
    val title: String,
    val description: String?,
    val isPublic: Boolean,
    val isOwner: Boolean,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val items: List<CollectionItem>,
)

data class RecentContribution(val kind: String, val status: String, val reviewedAt: String?)

data class PublicProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String?,
    val avatarUrl: String?,
    val role: String,
    val createdAt: String?,
    val reputationPoints: Int,
    val reputationTier: String,
    val approvedContributions: Int,
    val publishedRules: Int,
    val followers: Int,
    val following: Int,
    val isFollowing: Boolean,
    val isSelf: Boolean,
    val recentContributions: List<RecentContribution>,
)

data class LeaderboardEntry(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    val reputationPoints: Int,
    val reputationTier: String,
    val approvedContributions: Int,
)

data class FollowResult(val userId: String, val following: Boolean, val followers: Int)
