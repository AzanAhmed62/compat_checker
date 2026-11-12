package com.compatchecker.app.ui.profile

import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Structured payloads for the contribution kinds supported by
 * `public.submit_contribution`. Validation here is only a fast client-side
 * guard; the database re-validates every field and controls publication.
 */
enum class ContributionKind(val wire: String) {
    NEW_PRODUCT("new_product"),
    NEW_RULE("new_rule"),
    EVIDENCE("evidence"),
    GUIDE("guide"),
}

data class EvidenceDraft(
    val evidenceType: String,
    val title: String,
    val sourceUrl: String,
    val publisher: String? = null,
    val excerpt: String? = null,
    val capturedAt: String? = null,
)

data class SpecDraft(
    val label: String,
    val value: String,
    val unit: String? = null,
    val isCritical: Boolean = false,
)

data class GuideStepDraft(
    val title: String,
    val body: String,
    val imagePath: String? = null,
)

private val slugPattern = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
private val evidenceTypes = setOf(
    "oem_document", "service_bulletin", "teardown", "lab_test", "community_report", "other",
)
private val relationships = setOf(
    "fits", "works_with", "same_as", "variant_of", "not_compatible_with",
)
private val guideDifficulties = setOf("easy", "moderate", "difficult", "expert")

internal fun slugify(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .take(80)

internal fun isValidSlug(value: String): Boolean = slugPattern.matches(value)

internal fun isValidEvidenceUrl(value: String): Boolean =
    Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(value) &&
        !Regex("^https?://(www\\.)?example\\.(com|org|net)", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)

private fun requireText(value: String, label: String, min: Int = 1, max: Int = Int.MAX_VALUE) {
    val length = value.trim().length
    require(length in min..max) { "$label must contain $min-${max.coerceAtMost(50000)} characters" }
}

private fun evidenceJson(draft: EvidenceDraft): JsonObject {
    require(draft.evidenceType in evidenceTypes) { "Choose a valid evidence type" }
    requireText(draft.title, "Evidence title", 3, 250)
    require(isValidEvidenceUrl(draft.sourceUrl.trim())) {
        "Evidence source URLs must be real http(s) addresses"
    }
    return buildJsonObject {
        put("evidence_type", draft.evidenceType)
        put("title", draft.title.trim())
        put("source_url", draft.sourceUrl.trim())
        put("publisher", draft.publisher?.trim()?.ifBlank { null })
        put("excerpt", draft.excerpt?.trim()?.ifBlank { null })
        put("captured_at", draft.capturedAt?.trim()?.ifBlank { null })
    }
}

private fun evidenceArray(drafts: List<EvidenceDraft>): JsonArray {
    require(drafts.isNotEmpty()) { "At least one evidence item is required" }
    return buildJsonArray { drafts.forEach { add(evidenceJson(it)) } }
}

internal fun buildNewProductPayload(
    name: String,
    slug: String,
    brandId: String,
    categoryId: String,
    modelNumber: String?,
    releaseYear: Int?,
    summary: String?,
    technicalNotes: String?,
    connectorType: String?,
    formFactor: String?,
    aliases: List<String>,
    specs: List<SpecDraft>,
): JsonObject {
    requireText(name, "Product name", 1, 200)
    require(isValidSlug(slug.trim())) { "Slug must be lowercase words separated by single hyphens" }
    require(brandId.isNotBlank()) { "Choose a brand" }
    require(categoryId.isNotBlank()) { "Choose a category" }
    require(releaseYear == null || releaseYear in 1990..2100) { "Release year must be between 1990 and 2100" }

    return buildJsonObject {
        put("name", name.trim())
        put("slug", slug.trim())
        put("brand_id", brandId)
        put("category_id", categoryId)
        put("model_number", modelNumber?.trim()?.ifBlank { null })
        put("release_year", releaseYear)
        put("summary", summary?.trim()?.ifBlank { null })
        put("technical_notes", technicalNotes?.trim()?.ifBlank { null })
        put("connector_type", connectorType?.trim()?.ifBlank { null })
        put("form_factor", formFactor?.trim()?.ifBlank { null })
        put("aliases", buildJsonArray {
            aliases.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { add(it) }
        })
        put("specs", buildJsonArray {
            specs.filter { it.label.isNotBlank() && it.value.isNotBlank() }.forEach { spec ->
                addJsonObject {
                    put("label", spec.label.trim())
                    put("value", spec.value.trim())
                    put("unit", spec.unit?.trim()?.ifBlank { null })
                    put("is_critical", spec.isCritical)
                }
            }
        })
    }
}

internal fun buildNewRulePayload(
    subjectProductId: String,
    objectProductId: String,
    relationship: String,
    confidence: Int,
    summary: String,
    technicalDetails: String?,
    requiredModifications: String?,
    evidence: List<EvidenceDraft>,
): JsonObject {
    require(subjectProductId.isNotBlank() && objectProductId.isNotBlank()) { "Choose both products" }
    require(subjectProductId != objectProductId) { "A product cannot be compared with itself" }
    require(relationship in relationships) { "Choose a valid relationship" }
    require(confidence in 0..100) { "Confidence must be between 0 and 100" }
    requireText(summary, "Summary", 10, 300)

    return buildJsonObject {
        put("subject_product_id", subjectProductId)
        put("object_product_id", objectProductId)
        put("relationship", relationship)
        put("verification", "needs_verification")
        put("risk_level", "none")
        put("confidence", confidence)
        put("summary", summary.trim())
        put("technical_details", technicalDetails?.trim()?.ifBlank { null })
        put("required_modifications", requiredModifications?.trim()?.ifBlank { null })
        put("evidence", evidenceArray(evidence))
    }
}

internal fun buildEvidencePayload(ruleId: String, evidence: EvidenceDraft): JsonObject {
    require(ruleId.isNotBlank()) { "A rule is required" }
    return buildJsonObject {
        put("rule_id", ruleId)
        put("evidence", evidenceJson(evidence))
    }
}

internal fun buildGuidePayload(
    title: String,
    summary: String,
    bodyMarkdown: String,
    difficulty: String,
    steps: List<GuideStepDraft>,
): JsonObject {
    requireText(title, "Guide title", 3, 200)
    requireText(summary, "Guide summary", 10, 1000)
    requireText(bodyMarkdown, "Guide body", 20, 50000)
    require(difficulty in guideDifficulties) { "Choose a valid difficulty" }
    require(steps.size <= 100) { "A guide cannot exceed 100 steps" }
    steps.forEachIndexed { index, step ->
        require(step.title.isNotBlank() && step.body.isNotBlank()) {
            "Step ${index + 1} requires a title and body"
        }
    }

    return buildJsonObject {
        put("title", title.trim())
        put("summary", summary.trim())
        put("body_md", bodyMarkdown.trim())
        put("difficulty", difficulty)
        put("steps", buildJsonArray {
            steps.forEach { step ->
                addJsonObject {
                    put("title", step.title.trim())
                    put("body", step.body.trim())
                    put("image_path", step.imagePath?.trim()?.ifBlank { null })
                }
            }
        })
    }
}
