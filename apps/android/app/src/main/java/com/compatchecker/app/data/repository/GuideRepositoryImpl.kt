package com.compatchecker.app.data.repository

import com.compatchecker.app.data.local.CachedGuideDetailEntity
import com.compatchecker.app.data.local.CachedGuideEntity
import com.compatchecker.app.data.local.CatalogDao
import com.compatchecker.app.data.remote.GuideDetailDto
import com.compatchecker.app.data.remote.GuideDto
import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.domain.model.GuideDetail
import com.compatchecker.app.domain.model.GuideFeed
import com.compatchecker.app.domain.repository.GuideRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GuideRepositoryImpl(
    private val gateway: SupabaseGateway?,
    private val dao: CatalogDao,
) : GuideRepository {
    private fun urlFor(path: String?): String? = gateway?.publicMediaUrl(path)

    override suspend fun guides(): GuideFeed {
        val remote = gateway ?: return cachedGuides()
        return try {
            val guides = remote.client.from("guides_public").select()
                .decodeList<GuideDto>()
                .map { it.toDomain() }
                .sortedByDescending { it.publishedAt ?: "" }
            val now = System.currentTimeMillis()
            dao.upsertGuides(
                guides.map { guide ->
                    CachedGuideEntity(
                        id = guide.id,
                        payload = remote.json.encodeToString(GuideDto.serializer(), guide.toDto()),
                        cachedAt = now,
                    )
                },
            )
            dao.deleteOldGuides(now - CACHE_MAX_AGE_MS)
            GuideFeed(guides = guides)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            runCatching { cachedGuides() }.getOrElse { throw error }
        }
    }

    override suspend fun guide(id: String): GuideDetail {
        val remote = gateway ?: return cachedGuide(id)
            ?: error("No saved copy of this guide is available.")
        return try {
            val dto = remote.client.postgrest
                .rpc("get_guide_detail", buildJsonObject { put("p_guide_id", id) })
                .decodeAs<GuideDetailDto?>()
                ?: error("Guide not found.")
            dao.upsertGuideDetail(
                CachedGuideDetailEntity(
                    id = id,
                    payload = remote.json.encodeToString(GuideDetailDto.serializer(), dto),
                    cachedAt = System.currentTimeMillis(),
                ),
            )
            dao.deleteOldGuideDetails(System.currentTimeMillis() - CACHE_MAX_AGE_MS)
            dto.toDomain(::urlFor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            cachedGuide(id) ?: throw error
        }
    }

    private suspend fun cachedGuides(): GuideFeed {
        val json = gateway?.json ?: error("Compat Checker is not configured.")
        val guides = dao.allGuides().mapNotNull { cached ->
            runCatching {
                json.decodeFromString(GuideDto.serializer(), cached.payload).toDomain()
            }.getOrNull()
        }.sortedByDescending { it.publishedAt ?: "" }
        if (guides.isEmpty()) throw OfflineCacheMissException(
            "You are offline and no saved guides are available yet.",
        )
        return GuideFeed(guides = guides, offline = true)
    }

    private suspend fun cachedGuide(id: String): GuideDetail? {
        val cached = dao.guideDetail(id) ?: return null
        val json = gateway?.json ?: return null
        return runCatching {
            json.decodeFromString(GuideDetailDto.serializer(), cached.payload)
                .toDomain(::urlFor)
                .copy(offline = true)
        }.getOrNull()
    }

    private fun com.compatchecker.app.domain.model.GuideSummary.toDto() = GuideDto(
        id = id,
        slug = slug,
        title = title,
        summary = summary,
        bodyMarkdown = bodyMarkdown,
        difficulty = difficulty,
        productId = productId,
        productName = productName,
        authorName = authorName,
        stepCount = stepCount,
        publishedAt = publishedAt,
    )

    private companion object {
        const val CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
