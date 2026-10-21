package com.example.compatchecker.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RequestStatus(val label: String) {
    OPEN_VOTING("Open for Votes"),
    UNDER_LAB_TEST("In Lab Validation"),
    VERIFIED("Verified in Catalog"),
    CLOSED("Closed / Infeasible")
}

@Serializable
data class ProductRequest(
    val id: String,
    val targetDeviceName: String,
    val targetModelNumber: String,
    val componentType: ProductCategory,
    val notes: String,
    val votesCount: Int = 1,
    val hasUserVoted: Boolean = false,
    val status: RequestStatus = RequestStatus.OPEN_VOTING,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class CommentType(val label: String) {
    GENERAL("General Note"),
    WARNING("Technical Warning"),
    COMPATIBILITY_TIP("Fitment Tip"),
    QUESTION("Question")
}

@Serializable
data class Comment(
    val id: String,
    val productId: String,
    val authorName: String,
    val authorRole: String,
    val content: String,
    val type: CommentType = CommentType.GENERAL,
    val upvotes: Int = 0,
    val hasUserUpvoted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class ContributionStatus(val label: String) {
    PENDING_REVIEW("Pending Review"),
    ACCEPTED("Merged to Catalog"),
    REJECTED("Needs Further Evidence")
}

@Serializable
data class Contribution(
    val id: String,
    val title: String,
    val sourceProductName: String,
    val targetProductName: String,
    val proposedStatus: CompatibilityStatus,
    val evidenceUrl: String,
    val technicalNotes: String,
    val submitterEmail: String,
    val status: ContributionStatus = ContributionStatus.PENDING_REVIEW,
    val submittedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class GuideDifficulty(val label: String) {
    NOVICE("Novice (Adhesive/Screws)"),
    INTERMEDIATE("Intermediate (Heat Gun/Prying)"),
    ADVANCED("Advanced (Micro-connectors/BMS)"),
    EXPERT("Expert (Micro-soldering/IC Swap)")
}

@Serializable
data class GuideStep(
    val stepNumber: Int,
    val title: String,
    val instructions: String,
    val warning: String = ""
)

@Serializable
data class RepairGuide(
    val id: String,
    val productId: String,
    val title: String,
    val difficulty: GuideDifficulty,
    val estimatedMinutes: Int,
    val requiredTools: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val steps: List<GuideStep> = emptyList()
)

@Serializable
data class Favorite(
    val id: String,
    val productId: String,
    val productName: String,
    val brand: String,
    val category: ProductCategory,
    val modelNumber: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class UserRole(val label: String) {
    GUEST("Guest Explorer"),
    ENTHUSIAST("Hardware Enthusiast"),
    CONTRIBUTOR("Active Contributor"),
    REPAIR_TECHNICIAN("Certified Repair Tech"),
    MAINTAINER("Catalog Maintainer")
}

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val role: UserRole = UserRole.GUEST,
    val contributionsCount: Int = 0,
    val verifiedBadges: List<String> = emptyList()
)
