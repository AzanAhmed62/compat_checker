package com.compatchecker.app.data.repository

import com.compatchecker.app.data.remote.CommentDto
import com.compatchecker.app.data.remote.RequestDto
import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.data.remote.VoteResultDto
import com.compatchecker.app.domain.model.Comment
import com.compatchecker.app.domain.model.CommunityRequest
import com.compatchecker.app.domain.repository.CommunityRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CommunityRepositoryImpl(private val gateway: SupabaseGateway?) : CommunityRepository {
    override suspend fun requests(): List<CommunityRequest> {
        val remote = requireGateway()
        return remote.client.from("requests_public").select {
            order("vote_count", Order.DESCENDING)
            limit(100)
        }.decodeList<RequestDto>().map { it.toDomain() }
    }

    override suspend fun createRequest(title: String, description: String, categoryId: String?): String {
        val remote = requireGateway()
        val parameters = buildJsonObject {
            put("p_title", title.trim())
            put("p_description", description.trim())
            if (categoryId != null) put("p_category_id", categoryId)
        }
        return remote.client.postgrest.rpc("create_product_request", parameters)
            .decodeAs<String>()
    }

    override suspend fun toggleRequestVote(requestId: String): Int {
        val remote = requireGateway()
        val result = remote.client.postgrest.rpc(
            "toggle_request_vote",
            buildJsonObject { put("p_request_id", requestId) },
        ).decodeAs<VoteResultDto>()
        return result.votes
    }

    override suspend fun comments(targetType: String, targetId: String): List<Comment> {
        val remote = requireGateway()
        return remote.client.from("comments_public").select {
            filter {
                eq("target_type", targetType)
                eq("target_id", targetId)
            }
            order("created_at", Order.ASCENDING)
            limit(300)
        }.decodeList<CommentDto>().map { it.toDomain() }
    }

    override suspend fun addComment(targetType: String, targetId: String, body: String): Comment {
        val remote = requireGateway()
        val parameters = buildJsonObject {
            put("p_target_type", targetType)
            put("p_target_id", targetId)
            put("p_body", body.trim())
        }
        val result = remote.client.postgrest.rpc("create_comment", parameters)
            .decodeAs<JsonElement>()
        val newId = result.jsonObject["id"]?.jsonPrimitive?.content
        return comments(targetType, targetId).firstOrNull { it.id == newId }
            ?: error("Comment was submitted but could not be reloaded.")
    }

    override suspend fun report(targetType: String, targetId: String, reason: String, details: String?) {
        val remote = requireGateway()
        val parameters = buildJsonObject {
            put("p_target_type", targetType)
            put("p_target_id", targetId)
            put("p_reason", reason)
            if (!details.isNullOrBlank()) put("p_details", details.trim())
        }
        remote.client.postgrest.rpc("submit_report", parameters).decodeAs<JsonElement>()
    }

    private fun requireGateway(): SupabaseGateway =
        gateway ?: error("Compat Checker is not configured.")
}
