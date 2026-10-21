package com.compatchecker.app.data.repository

import com.compatchecker.app.data.remote.GuideDetailDto
import com.compatchecker.app.data.remote.GuideDto
import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.domain.model.GuideDetail
import com.compatchecker.app.domain.model.GuideSummary
import com.compatchecker.app.domain.repository.GuideRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GuideRepositoryImpl(private val gateway: SupabaseGateway?) : GuideRepository {
    private fun urlFor(path: String?): String? = gateway?.publicMediaUrl(path)

    override suspend fun guides(): List<GuideSummary> {
        val remote = requireGateway()
        return remote.client.from("guides_public").select()
            .decodeList<GuideDto>()
            .map { it.toDomain() }
            .sortedByDescending { it.publishedAt ?: "" }
    }

    override suspend fun guide(id: String): GuideDetail {
        val remote = requireGateway()
        val dto = remote.client.postgrest
            .rpc("get_guide_detail", buildJsonObject { put("p_guide_id", id) })
            .decodeAs<GuideDetailDto?>()
            ?: error("Guide not found.")
        return dto.toDomain(::urlFor)
    }

    private fun requireGateway(): SupabaseGateway =
        gateway ?: error("Compat Checker is not configured.")
}
