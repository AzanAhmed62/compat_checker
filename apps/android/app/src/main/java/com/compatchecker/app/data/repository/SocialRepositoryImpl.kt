package com.compatchecker.app.data.repository

import com.compatchecker.app.data.remote.CollectionDetailDto
import com.compatchecker.app.data.remote.CollectionsDto
import com.compatchecker.app.data.remote.FollowResultDto
import com.compatchecker.app.data.remote.LeaderboardDto
import com.compatchecker.app.data.remote.PublicProfileDto
import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.domain.model.CollectionDetail
import com.compatchecker.app.domain.model.CollectionSummary
import com.compatchecker.app.domain.model.FollowResult
import com.compatchecker.app.domain.model.LeaderboardEntry
import com.compatchecker.app.domain.model.PublicProfile
import com.compatchecker.app.domain.repository.SocialRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SocialRepositoryImpl(private val gateway: SupabaseGateway?) : SocialRepository {
    private fun urlFor(path: String?): String? = gateway?.publicMediaUrl(path)

    override suspend fun collections(): List<CollectionSummary> {
        val result = requireGateway().client.postgrest
            .rpc("list_my_collections", buildJsonObject {})
            .decodeAs<CollectionsDto>()
        return result.items.map { it.toDomain() }
    }

    override suspend fun collection(id: String): CollectionDetail {
        val dto = requireGateway().client.postgrest
            .rpc("get_collection", buildJsonObject { put("p_collection_id", id) })
            .decodeAs<CollectionDetailDto?>()
            ?: error("Collection not found or not visible.")
        return dto.toDomain(::urlFor)
    }

    override suspend fun createCollection(
        title: String,
        description: String?,
        isPublic: Boolean,
    ): String {
        val parameters = buildJsonObject {
            put("p_title", title.trim())
            if (!description.isNullOrBlank()) put("p_description", description.trim())
            put("p_is_public", isPublic)
        }
        return requireGateway().client.postgrest
            .rpc("create_collection", parameters)
            .decodeAs<String>()
    }

    override suspend fun updateCollection(
        collectionId: String,
        title: String?,
        description: String?,
        isPublic: Boolean?,
    ): Boolean {
        val parameters = buildJsonObject {
            put("p_collection_id", collectionId)
            put("p_title", title)
            put("p_description", description)
            put("p_is_public", isPublic)
        }
        return requireGateway().client.postgrest
            .rpc("update_collection", parameters)
            .decodeAs<Boolean>()
    }

    override suspend fun addToCollection(
        collectionId: String,
        productId: String,
        note: String?,
    ): Boolean {
        val parameters = buildJsonObject {
            put("p_collection_id", collectionId)
            put("p_product_id", productId)
            if (!note.isNullOrBlank()) put("p_note", note.trim())
        }
        return requireGateway().client.postgrest
            .rpc("add_to_collection", parameters)
            .decodeAs<Boolean>()
    }

    override suspend fun removeFromCollection(collectionId: String, productId: String): Boolean {
        val parameters = buildJsonObject {
            put("p_collection_id", collectionId)
            put("p_product_id", productId)
        }
        return requireGateway().client.postgrest
            .rpc("remove_from_collection", parameters)
            .decodeAs<Boolean>()
    }

    override suspend fun deleteCollection(collectionId: String): Boolean =
        requireGateway().client.postgrest
            .rpc("delete_collection", buildJsonObject { put("p_collection_id", collectionId) })
            .decodeAs<Boolean>()

    override suspend fun publicProfile(userId: String): PublicProfile {
        val dto = requireGateway().client.postgrest
            .rpc("get_public_profile", buildJsonObject { put("p_user_id", userId) })
            .decodeAs<PublicProfileDto?>()
            ?: error("Profile not found.")
        return dto.toDomain(::urlFor)
    }

    override suspend fun toggleFollow(userId: String): FollowResult {
        val dto = requireGateway().client.postgrest
            .rpc("toggle_follow", buildJsonObject { put("p_user_id", userId) })
            .decodeAs<FollowResultDto>()
        return FollowResult(dto.userId, dto.following, dto.followers)
    }

    override suspend fun leaderboard(): List<LeaderboardEntry> {
        val result = requireGateway().client.postgrest
            .rpc("reputation_leaderboard", buildJsonObject { put("p_limit", 50) })
            .decodeAs<LeaderboardDto>()
        return result.items.map { it.toDomain(::urlFor) }
    }

    private fun requireGateway(): SupabaseGateway =
        gateway ?: error("Compat Checker is not configured.")
}
