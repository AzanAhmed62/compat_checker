package com.example.compatchecker.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CompatibilityStatus(val title: String, val badgeColor: Long) {
    COMPATIBLE("Direct Fit", 0xFF10B981),
    CONDITIONAL("Conditional / Requires Mod", 0xFFF59E0B),
    INCOMPATIBLE("Incompatible / Mismatch", 0xFFEF4444),
    UNVERIFIED("Unverified Research Needed", 0xFF64748B)
}

@Serializable
enum class ConfidenceLevel(val score: Int, val label: String) {
    HIGH_VERIFIED(95, "Verified High"),
    MEDIUM_CONFIRMED(75, "Confirmed Medium"),
    LOW_EXPERIMENTAL(40, "Experimental / Field Report")
}

@Serializable
enum class RiskLevel(val label: String, val levelNumber: Int) {
    NONE("No Known Hazards", 0),
    LOW("Minor Fit Adjustment", 1),
    MODERATE("Calibration / Software Reset Needed", 2),
    HIGH_SERIALIZED_LOCKOUT("Cryptographic Component Serial Lockout", 3)
}

@Serializable
enum class EvidenceType(val displayName: String) {
    OEM_MANUAL("OEM Service Documentation"),
    TEARDOWN_BENCHMARK("Teardown Lab Analysis"),
    COMMUNITY_REPAIR_LAB("Certified Community Repair Test"),
    SERVICE_BULLETIN("Manufacturer Technical Advisory")
}

@Serializable
data class EvidenceSource(
    val id: String,
    val title: String,
    val type: EvidenceType,
    val authorOrSource: String,
    val citation: String,
    val url: String = "",
    val verifiedDate: String,
    val isLabVerified: Boolean = true
)

@Serializable
data class CompatibilityRule(
    val id: String,
    val sourceProductId: String,
    val targetProductId: String,
    val sourceProductName: String,
    val targetProductName: String,
    val status: CompatibilityStatus,
    val confidence: ConfidenceLevel,
    val summary: String,
    val technicalDetails: String,
    val requiredModifications: String = "",
    val riskLevel: RiskLevel = RiskLevel.NONE,
    val evidenceSources: List<EvidenceSource> = emptyList()
)
