package com.compatchecker.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProductSummaryDto(
    val id: String,
    val slug: String = "",
    val name: String,
    @SerialName("model_number") val modelNumber: String? = null,
    val summary: String? = null,
    @SerialName("brand_name") val brandName: String = "",
    @SerialName("brand_slug") val brandSlug: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("category_slug") val categorySlug: String = "",
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("rule_count") val ruleCount: Int = 0,
)

@Serializable
data class SearchPageDto(
    val items: List<ProductSummaryDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SpecDto(
    val label: String,
    val value: String,
    val unit: String? = null,
    @SerialName("is_critical") val isCritical: Boolean = false,
)

@Serializable
data class MediaDto(val path: String, val alt: String? = null)

@Serializable
data class ProductDto(
    val id: String,
    val slug: String = "",
    val name: String,
    @SerialName("model_number") val modelNumber: String? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    val summary: String? = null,
    @SerialName("technical_notes") val technicalNotes: String? = null,
    @SerialName("connector_type") val connectorType: String? = null,
    @SerialName("form_factor") val formFactor: String? = null,
    @SerialName("brand_name") val brandName: String = "",
    @SerialName("brand_slug") val brandSlug: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("category_slug") val categorySlug: String = "",
    val aliases: List<String> = emptyList(),
    val specs: List<SpecDto> = emptyList(),
    val media: List<MediaDto> = emptyList(),
)

@Serializable
data class ProductRuleDto(
    val id: String,
    val relationship: String,
    val verification: String,
    val confidence: Int = 0,
    @SerialName("risk_level") val riskLevel: String? = null,
    val summary: String? = null,
    @SerialName("other_product_id") val otherProductId: String,
    @SerialName("other_product_name") val otherProductName: String,
    @SerialName("evidence_count") val evidenceCount: Int = 0,
    @SerialName("confirm_count") val confirmCount: Int = 0,
    @SerialName("dispute_count") val disputeCount: Int = 0,
)

@Serializable
data class GuideLinkDto(
    val id: String,
    val slug: String = "",
    val title: String,
    val summary: String? = null,
    val difficulty: String = "",
)

@Serializable
data class ProductDetailDto(
    val id: String,
    val slug: String = "",
    val name: String,
    @SerialName("model_number") val modelNumber: String? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    val summary: String? = null,
    @SerialName("technical_notes") val technicalNotes: String? = null,
    @SerialName("connector_type") val connectorType: String? = null,
    @SerialName("form_factor") val formFactor: String? = null,
    @SerialName("brand_name") val brandName: String = "",
    @SerialName("brand_slug") val brandSlug: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("category_slug") val categorySlug: String = "",
    val aliases: List<String> = emptyList(),
    val specs: List<SpecDto> = emptyList(),
    val media: List<MediaDto> = emptyList(),
    val rules: List<ProductRuleDto> = emptyList(),
    val guides: List<GuideLinkDto> = emptyList(),
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
)

@Serializable
data class EvidenceDto(
    val id: String,
    val title: String,
    @SerialName("source_url") val sourceUrl: String,
    val publisher: String? = null,
    val excerpt: String? = null,
    @SerialName("evidence_type") val evidenceType: String,
    @SerialName("captured_at") val capturedAt: String? = null,
)

@Serializable
data class CheckedRuleDto(
    val id: String,
    val relationship: String,
    val verification: String,
    val confidence: Int = 0,
    @SerialName("risk_level") val riskLevel: String? = null,
    val summary: String? = null,
    @SerialName("technical_details") val technicalDetails: String? = null,
    @SerialName("required_modifications") val requiredModifications: String? = null,
    @SerialName("subject_product_id") val subjectProductId: String,
    @SerialName("subject_product_name") val subjectProductName: String,
    @SerialName("object_product_id") val objectProductId: String,
    @SerialName("object_product_name") val objectProductName: String,
    val direction: String = "",
    val evidence: List<EvidenceDto> = emptyList(),
    @SerialName("confirm_count") val confirmCount: Int = 0,
    @SerialName("dispute_count") val disputeCount: Int = 0,
)

@Serializable
data class CompatibilityResultDto(
    val known: Boolean = false,
    val outcome: String = "unknown",
    @SerialName("same_product") val sameProduct: Boolean = false,
    @SerialName("rule_count") val ruleCount: Int = 0,
    val rules: List<CheckedRuleDto> = emptyList(),
    @SerialName("has_incompatibility") val hasIncompatibility: Boolean = false,
    @SerialName("checked_at") val checkedAt: String? = null,
)

@Serializable
data class RuleDetailDto(
    val id: String,
    val relationship: String,
    val verification: String,
    val confidence: Int = 0,
    val risk: String? = null,
    val summary: String? = null,
    @SerialName("technical_details") val technicalDetails: String? = null,
    @SerialName("required_modifications") val requiredModifications: String? = null,
    @SerialName("subject_product_id") val subjectProductId: String,
    @SerialName("subject_product_name") val subjectProductName: String = "",
    @SerialName("subject_product_slug") val subjectProductSlug: String = "",
    @SerialName("subject_brand_name") val subjectBrandName: String = "",
    @SerialName("object_product_id") val objectProductId: String,
    @SerialName("object_product_name") val objectProductName: String = "",
    @SerialName("object_product_slug") val objectProductSlug: String = "",
    @SerialName("object_brand_name") val objectBrandName: String = "",
    @SerialName("evidence_count") val evidenceCount: Int = 0,
    @SerialName("confirm_count") val confirmCount: Int = 0,
    @SerialName("dispute_count") val disputeCount: Int = 0,
    @SerialName("my_vote") val myVote: String? = null,
    val evidence: List<EvidenceDto> = emptyList(),
)

@Serializable
data class GuideDto(
    val id: String,
    val slug: String = "",
    val title: String,
    val summary: String? = null,
    @SerialName("body_md") val bodyMarkdown: String = "",
    val difficulty: String = "",
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("author_display_name") val authorName: String = "Compat Checker editorial",
    @SerialName("step_count") val stepCount: Int = 0,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class GuideStepDto(
    val id: String,
    val position: Int,
    val title: String,
    val body: String,
    @SerialName("image_path") val imagePath: String? = null,
)

@Serializable
data class GuideDetailDto(
    val id: String,
    val slug: String = "",
    val title: String,
    val summary: String? = null,
    @SerialName("body_md") val bodyMarkdown: String = "",
    val difficulty: String = "",
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("author_display_name") val authorName: String = "Compat Checker editorial",
    @SerialName("step_count") val stepCount: Int = 0,
    @SerialName("published_at") val publishedAt: String? = null,
    val steps: List<GuideStepDto> = emptyList(),
    @SerialName("comment_count") val commentCount: Int = 0,
)

@Serializable
data class CategoryDto(val id: String, val slug: String, val name: String)

@Serializable
data class BrandDto(val id: String, val slug: String, val name: String)

@Serializable
data class RequestDto(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class VoteResultDto(val voted: Boolean, val votes: Int = 0)

@Serializable
data class ToggleFavoriteDto(
    @SerialName("is_favorite") val isFavorite: Boolean,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
)

@Serializable
data class FavoritesDto(val items: List<ProductDto> = emptyList())

@Serializable
data class CommentDto(
    val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    val body: String,
    val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    val role: String = "member",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class MeDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val bio: String? = null,
    val role: String,
    @SerialName("account_status") val accountStatus: String,
    val locale: String = "en",
    @SerialName("reputation_points") val reputationPoints: Int = 0,
    @SerialName("reputation_tier") val reputationTier: String = "visitor",
    @SerialName("unread_notifications") val unreadNotifications: Int = 0,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("deletion_requested_at") val deletionRequestedAt: String? = null,
    val capabilities: CapabilitiesDto = CapabilitiesDto(),
)

@Serializable
data class CapabilitiesDto(
    @SerialName("can_contribute") val canContribute: Boolean = false,
    @SerialName("can_moderate") val canModerate: Boolean = false,
    @SerialName("can_admin") val canAdmin: Boolean = false,
)

@Serializable
data class ReputationEntryDto(
    val id: String,
    val delta: Int,
    val reason: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ReputationDto(
    val points: Int = 0,
    val tier: String = "visitor",
    val entries: List<ReputationEntryDto> = emptyList(),
)

@Serializable
data class ContributionDto(
    val id: String,
    val kind: String,
    val status: String,
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

@Serializable
data class ContributionsDto(val items: List<ContributionDto> = emptyList())

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String? = null,
    val link: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NotificationsDto(
    val items: List<NotificationDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class CollectionDto(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CollectionsDto(val items: List<CollectionDto> = emptyList())

@Serializable
data class CollectionItemDto(
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("product_slug") val productSlug: String = "",
    @SerialName("model_number") val modelNumber: String? = null,
    val summary: String? = null,
    @SerialName("brand_name") val brandName: String = "",
    @SerialName("brand_slug") val brandSlug: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("category_slug") val categorySlug: String = "",
    @SerialName("image_path") val imagePath: String? = null,
    val note: String? = null,
    @SerialName("added_at") val addedAt: String? = null,
)

@Serializable
data class CollectionDetailDto(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("is_owner") val isOwner: Boolean = false,
    @SerialName("owner_username") val ownerUsername: String = "",
    @SerialName("owner_display_name") val ownerDisplayName: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val items: List<CollectionItemDto> = emptyList(),
)

@Serializable
data class RecentContributionDto(
    val kind: String,
    val status: String,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

@Serializable
data class PublicProfileDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "member",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reputation_points") val reputationPoints: Int = 0,
    @SerialName("reputation_tier") val reputationTier: String = "visitor",
    @SerialName("approved_contributions") val approvedContributions: Int = 0,
    @SerialName("published_rules") val publishedRules: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("is_following") val isFollowing: Boolean = false,
    @SerialName("is_self") val isSelf: Boolean = false,
    @SerialName("recent_contributions") val recentContributions: List<RecentContributionDto> = emptyList(),
)

@Serializable
data class LeaderboardEntryDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String = "member",
    @SerialName("reputation_points") val reputationPoints: Int = 0,
    @SerialName("reputation_tier") val reputationTier: String = "visitor",
    @SerialName("approved_contributions") val approvedContributions: Int = 0,
)

@Serializable
data class LeaderboardDto(val items: List<LeaderboardEntryDto> = emptyList())

@Serializable
data class FollowResultDto(
    @SerialName("user_id") val userId: String,
    val following: Boolean,
    val followers: Int = 0,
)

@Serializable
data class ContributionPayloadDto(
    @SerialName("product_id") val productId: String,
    val patch: JsonObject,
)

@Serializable
data class DeletionResultDto(
    val status: String,
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("purge_after") val purgeAfter: String? = null,
    val message: String? = null,
)
