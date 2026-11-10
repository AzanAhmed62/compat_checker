package com.compatchecker.app.data.repository

import com.compatchecker.app.data.remote.CollectionDetailDto
import com.compatchecker.app.data.remote.CollectionsDto
import com.compatchecker.app.data.remote.PublicProfileDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes collection indexes from the list_my_collections contract`() {
        val dto = json.decodeFromString<CollectionsDto>(
            """
            {"items":[{"id":"60000000-0000-4000-8000-000000000001",
            "title":"Teardown","description":"Notes",
            "is_public":true,"item_count":3,
            "created_at":"2026-11-01T10:00:00Z",
            "updated_at":"2026-11-02T10:00:00Z"}]}
            """.trimIndent(),
        )

        val summary = dto.items.single().toDomain()
        assertEquals("Teardown", summary.title)
        assertTrue(summary.isPublic)
        assertEquals(3, summary.itemCount)
    }

    @Test
    fun `decodes public collection detail and resolves media URLs`() {
        val dto = json.decodeFromString<CollectionDetailDto>(
            """
            {"id":"60000000-0000-4000-8000-000000000001",
            "title":"Teardown","description":null,"is_public":true,"is_owner":false,
            "owner_username":"sample_contributor","owner_display_name":"Sample Contributor",
            "items":[{"product_id":"10000000-0000-4000-8000-000000000001",
            "product_name":"Aurora X1","product_slug":"aurora-x1","model_number":"AX1",
            "summary":"Phone","brand_name":"Sample Electronics","brand_slug":"sample-electronics",
            "category_name":"Smartphones","category_slug":"smartphones",
            "image_path":"products/aurora.jpg","note":"Sample phone",
            "added_at":"2026-11-01T10:00:00Z"}]}
            """.trimIndent(),
        )

        val detail = dto.toDomain { path -> path?.let { "https://media.test/$it" } }
        assertFalse(detail.isOwner)
        assertEquals("sample_contributor", detail.ownerUsername)
        assertEquals("sample-electronics", detail.items.single().brandSlug)
        assertEquals("https://media.test/products/aurora.jpg", detail.items.single().imageUrl)
    }

    @Test
    fun `decodes public profile follow state and aggregate counts`() {
        val dto = json.decodeFromString<PublicProfileDto>(
            """
            {"id":"00000000-0000-4000-8000-000000000003",
            "username":"sample_contributor","display_name":"Sample Contributor",
            "bio":null,"avatar_url":null,"role":"contributor",
            "created_at":"2026-01-01T00:00:00Z",
            "reputation_points":42,"reputation_tier":"newcomer",
            "approved_contributions":2,"published_rules":1,
            "followers":4,"following":1,"is_following":true,"is_self":false,
            "recent_contributions":[{"kind":"new_rule","status":"approved",
            "reviewed_at":"2026-11-01T00:00:00Z"}]}
            """.trimIndent(),
        )

        val profile = dto.toDomain { null }
        assertEquals(4, profile.followers)
        assertEquals(1, profile.following)
        assertTrue(profile.isFollowing)
        assertFalse(profile.isSelf)
        assertEquals("new_rule", profile.recentContributions.single().kind)
    }
}
