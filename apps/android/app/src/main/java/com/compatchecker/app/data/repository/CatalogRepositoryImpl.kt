package com.compatchecker.app.data.repository

import com.compatchecker.app.data.local.CachedDetailEntity
import com.compatchecker.app.data.local.CachedProductEntity
import com.compatchecker.app.data.local.CatalogDao
import com.compatchecker.app.data.remote.BrandDto
import com.compatchecker.app.data.remote.CategoryDto
import com.compatchecker.app.data.remote.FavoritesDto
import com.compatchecker.app.data.remote.ProductDetailDto
import com.compatchecker.app.data.remote.SearchPageDto
import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.data.remote.ToggleFavoriteDto
import com.compatchecker.app.domain.model.Brand
import com.compatchecker.app.domain.model.Category
import com.compatchecker.app.domain.model.ProductDetail
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.model.SearchPage
import com.compatchecker.app.domain.repository.CatalogRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CatalogRepositoryImpl(
    private val gateway: SupabaseGateway?,
    private val dao: CatalogDao,
) : CatalogRepository {
    private fun urlFor(path: String?): String? = gateway?.publicMediaUrl(path)

    override suspend fun search(
        query: String,
        categorySlug: String?,
        brandSlug: String?,
        limit: Int,
        offset: Int,
    ): SearchPage {
        val remote = gateway ?: return cachedSearch(query, categorySlug, brandSlug, limit, offset)
        return try {
            val parameters = buildJsonObject {
                put("p_query", query.trim())
                if (categorySlug != null) put("p_category", categorySlug)
                if (brandSlug != null) put("p_brand", brandSlug)
                put("p_limit", limit)
                put("p_offset", offset)
            }
            val result = remote.client.postgrest
                .rpc("search_products", parameters)
                .decodeAs<SearchPageDto>()
            val items = result.items.map { it.toDomain(::urlFor) }
            dao.upsertProducts(items.map { it.toCache() })
            dao.deleteOldProducts(System.currentTimeMillis() - CACHE_MAX_AGE_MS)
            SearchPage(items = items, total = result.total)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            cachedSearch(query, categorySlug, brandSlug, limit, offset)
        }
    }

    override suspend fun categories(): List<Category> {
        val remote = gateway ?: return emptyList()
        return try {
            remote.client.from("categories_public").select().decodeList<CategoryDto>()
                .map { it.toDomain() }
                .sortedBy { it.name }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun brands(): List<Brand> {
        val remote = gateway ?: return emptyList()
        return try {
            remote.client.from("brands_public").select().decodeList<BrandDto>()
                .map { Brand(it.id, it.slug, it.name) }
                .sortedBy { it.name }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun product(id: String): ProductDetail {
        val remote = gateway ?: return cachedProduct(id)
            ?: error("No saved copy of this product is available.")
        return try {
            val parameters = buildJsonObject { put("p_product_id", id) }
            val dto = remote.client.postgrest
                .rpc("get_product_detail", parameters)
                .decodeAs<ProductDetailDto?>()
                ?: error("Product not found.")
            dao.upsertDetail(
                CachedDetailEntity(
                    id = id,
                    payload = remote.json.encodeToString(ProductDetailDto.serializer(), dto),
                    cachedAt = System.currentTimeMillis(),
                ),
            )
            dto.toDomain(::urlFor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            cachedProduct(id) ?: throw error
        }
    }

    override suspend fun cachedProduct(id: String): ProductDetail? {
        val cached = dao.detail(id) ?: return null
        return runCatching {
            gateway?.json?.decodeFromString(ProductDetailDto.serializer(), cached.payload)
                ?.toDomain(::urlFor)
                ?.copy(offline = true)
        }.getOrNull()
    }

    override suspend fun toggleFavorite(productId: String): Boolean {
        val remote = gateway ?: error("Compat Checker is not configured.")
        val parameters = buildJsonObject { put("p_product_id", productId) }
        val result = remote.client.postgrest
            .rpc("toggle_favorite", parameters)
            .decodeAs<ToggleFavoriteDto>()
        return result.isFavorite
    }

    override suspend fun favorites(): List<ProductSummary> {
        val remote = gateway ?: error("Compat Checker is not configured.")
        val result = remote.client.postgrest
            .rpc("list_my_favorites")
            .decodeAs<FavoritesDto>()
        return result.items.map { it.toSummary(::urlFor) }
    }

    private suspend fun cachedSearch(
        query: String,
        categorySlug: String?,
        brandSlug: String?,
        limit: Int,
        offset: Int,
    ): SearchPage {
        val term = query.trim().lowercase()
        val matching = dao.allProducts()
            .asSequence()
            .filter { categorySlug == null || it.categorySlug == categorySlug }
            .filter { brandSlug == null || it.brandSlug == brandSlug }
            .filter {
                term.isBlank() ||
                    it.name.lowercase().contains(term) ||
                    it.brandName.lowercase().contains(term) ||
                    it.modelNumber?.lowercase()?.contains(term) == true
            }
            .sortedBy { it.name }
            .toList()
        val items = matching.drop(offset).take(limit).map { cached ->
            ProductSummary(
                id = cached.id,
                slug = "",
                name = cached.name,
                modelNumber = cached.modelNumber,
                brandName = cached.brandName,
                brandSlug = cached.brandSlug,
                categoryName = cached.categoryName,
                categorySlug = cached.categorySlug,
                summary = cached.summary,
                imageUrl = cached.imagePath,
                ruleCount = cached.ruleCount,
            )
        }
        if (items.isEmpty() && offset == 0) {
            throw OfflineCacheMissException()
        }
        return SearchPage(items = items, total = matching.size, offline = true)
    }

    private fun ProductSummary.toCache() = CachedProductEntity(
        id = id,
        name = name,
        brandName = brandName,
        brandSlug = brandSlug,
        categoryName = categoryName,
        categorySlug = categorySlug,
        modelNumber = modelNumber,
        summary = summary,
        imagePath = imageUrl,
        ruleCount = ruleCount,
        cachedAt = System.currentTimeMillis(),
    )

    private companion object {
        const val CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}

class OfflineCacheMissException(
    message: String = "You are offline and no saved catalog results are available yet.",
) : IllegalStateException(message)
