package com.compatchecker.app.ui.profile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionPayloadsTest {
    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.content

    @Test
    fun `slugify normalizes free text`() {
        assertEquals("aurora-x2-display", slugify("Aurora X2 — Display"))
        assertEquals("sample-42", slugify("  Sample_42  "))
    }

    @Test
    fun `evidence URLs require http and reject example domains`() {
        assertTrue(isValidEvidenceUrl("https://docs.example.org/part"))
        assertTrue(isValidEvidenceUrl("http://support.vendor.test/bulletin"))
        assertFalse(isValidEvidenceUrl("ftp://docs.vendor.test/part"))
        assertFalse(isValidEvidenceUrl("https://example.com/part"))
        assertFalse(isValidEvidenceUrl("not a url"))
    }

    @Test
    fun `new product payload trims deduplicates and filters empty specs`() {
        val payload = buildNewProductPayload(
            name = "  Aurora X2  ",
            slug = "aurora-x2",
            brandId = "brand-1",
            categoryId = "category-1",
            modelNumber = " X2 ",
            releaseYear = 2026,
            summary = null,
            technicalNotes = " ",
            connectorType = null,
            formFactor = null,
            aliases = listOf(" Aurora ", "Aurora", "", "AX2"),
            specs = listOf(
                SpecDraft("Battery", "4200", "mAh", true),
                SpecDraft(" ", "ignored"),
            ),
        )

        assertEquals("Aurora X2", payload.text("name"))
        assertEquals("X2", payload.text("model_number"))
        assertEquals(JsonNull, payload["technical_notes"])
        assertEquals(2, payload["aliases"]!!.jsonArray.size)
        val specs = payload["specs"]!!.jsonArray
        assertEquals(1, specs.size)
        assertEquals("Battery", (specs[0] as JsonObject).text("label"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `new product rejects invalid slug`() {
        buildNewProductPayload(
            name = "Aurora X2",
            slug = "Aurora X2",
            brandId = "brand-1",
            categoryId = "category-1",
            modelNumber = null,
            releaseYear = null,
            summary = null,
            technicalNotes = null,
            connectorType = null,
            formFactor = null,
            aliases = emptyList(),
            specs = emptyList(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `new product rejects out of range release year`() {
        buildNewProductPayload(
            name = "Aurora X2",
            slug = "aurora-x2",
            brandId = "brand-1",
            categoryId = "category-1",
            modelNumber = null,
            releaseYear = 1880,
            summary = null,
            technicalNotes = null,
            connectorType = null,
            formFactor = null,
            aliases = emptyList(),
            specs = emptyList(),
        )
    }

    @Test
    fun `new rule requires one valid citation`() {
        val payload = buildNewRulePayload(
            subjectProductId = "10000000-0000-4000-8000-000000000001",
            objectProductId = "10000000-0000-4000-8000-000000000002",
            relationship = "fits",
            confidence = 40,
            summary = "Synthetic claim used only by the payload unit test.",
            technicalDetails = "No real compatibility claim is made.",
            requiredModifications = null,
            evidence = listOf(
                EvidenceDraft(
                    evidenceType = "service_bulletin",
                    title = "Synthetic service bulletin",
                    sourceUrl = "https://support.vendor.test/bulletin",
                ),
            ),
        )

        assertEquals("needs_verification", payload.text("verification"))
        assertEquals(1, payload["evidence"]!!.jsonArray.size)
        val evidence = payload["evidence"]!!.jsonArray[0] as JsonObject
        assertEquals("Synthetic service bulletin", evidence.text("title"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `new rule rejects identical products`() {
        buildNewRulePayload(
            subjectProductId = "10000000-0000-4000-8000-000000000001",
            objectProductId = "10000000-0000-4000-8000-000000000001",
            relationship = "fits",
            confidence = 40,
            summary = "Synthetic claim used only by the payload unit test.",
            technicalDetails = null,
            requiredModifications = null,
            evidence = listOf(
                EvidenceDraft("other", "Synthetic citation", "https://invalid.local/citation"),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `new rule rejects empty evidence`() {
        buildNewRulePayload(
            subjectProductId = "10000000-0000-4000-8000-000000000001",
            objectProductId = "10000000-0000-4000-8000-000000000002",
            relationship = "fits",
            confidence = 40,
            summary = "Synthetic claim used only by the payload unit test.",
            technicalDetails = null,
            requiredModifications = null,
            evidence = emptyList(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `evidence payload rejects example domains`() {
        buildEvidencePayload(
            ruleId = "20000000-0000-4000-8000-000000000001",
            evidence = EvidenceDraft(
                evidenceType = "other",
                title = "Synthetic citation",
                sourceUrl = "https://example.com/citation",
            ),
        )
    }

    @Test
    fun `guide payload keeps ordered steps`() {
        val payload = buildGuidePayload(
            title = "Synthetic guide",
            summary = "Synthetic guide summary for payload validation.",
            bodyMarkdown = "This synthetic body is long enough for guide payload validation.",
            difficulty = "moderate",
            steps = listOf(
                GuideStepDraft("Open the device", "Synthetic first step instructions."),
                GuideStepDraft("Replace the part", "Synthetic second step instructions."),
            ),
        )

        assertEquals("moderate", payload.text("difficulty"))
        val steps: JsonArray = payload["steps"]!!.jsonArray
        assertEquals(2, steps.size)
        assertEquals("Open the device", (steps[0] as JsonObject).text("title"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `guide payload rejects empty step titles`() {
        buildGuidePayload(
            title = "Synthetic guide",
            summary = "Synthetic guide summary for payload validation.",
            bodyMarkdown = "This synthetic body is long enough for guide payload validation.",
            difficulty = "easy",
            steps = listOf(GuideStepDraft("", "Synthetic step instructions.")),
        )
    }
}
