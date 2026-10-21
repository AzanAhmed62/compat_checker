package com.compatchecker.app.data.repository

import com.compatchecker.app.data.local.CachedDetailEntity
import com.compatchecker.app.data.local.CachedProductEntity
import com.compatchecker.app.data.local.CatalogDao
import com.compatchecker.app.data.remote.CheckedRuleDto
import com.compatchecker.app.data.remote.CompatibilityResultDto
import com.compatchecker.app.data.remote.EvidenceDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepositoryOfflineTest {
    private fun cached(
        id: String = "p1",
        name: String = "Aurora X1 Display",
        brand: String = "Aurora",
        categorySlug: String = "displays",
    ) = CachedProductEntity(
        id = id,
        name = name,
        brandName = brand,
        categoryName = categorySlug.replaceFirstChar { it.uppercase() },
        categorySlug = categorySlug,
        modelNumber = "AX1",
        summary = "Saved catalog record",
        imagePath = null,
        ruleCount = 1,
        cachedAt = System.currentTimeMillis(),
    )

    @Test
    fun `offline search returns saved products and marks result offline`() = runTest {
        val dao = FakeCatalogDao(listOf(cached(), cached(id = "p2", name = "Aurora X1 Battery")))
        val repository = CatalogRepositoryImpl(gateway = null, dao = dao)

        val page = repository.search("aurora", null, limit = 20, offset = 0)

        assertTrue(page.offline)
        assertEquals(2, page.total)
        assertEquals(2, page.items.size)
    }

    @Test
    fun `offline search applies category and term filters`() = runTest {
        val dao = FakeCatalogDao(
            listOf(
                cached(),
                cached(id = "p2", name = "Aurora X1 Battery", categorySlug = "batteries"),
            ),
        )
        val repository = CatalogRepositoryImpl(gateway = null, dao = dao)

        val page = repository.search("display", "displays", limit = 20, offset = 0)

        assertEquals(1, page.items.size)
        assertEquals("p1", page.items.single().id)
        assertFalse(page.items.single().name.contains("Battery"))
    }

    @Test
    fun `offline search without cache reports a cache miss`() = runTest {
        val repository = CatalogRepositoryImpl(gateway = null, dao = FakeCatalogDao(emptyList()))

        val failure = runCatching {
            repository.search("anything", null, limit = 20, offset = 0)
        }.exceptionOrNull()

        assertTrue(failure is OfflineCacheMissException)
    }

    private class FakeCatalogDao(
        private val products: List<CachedProductEntity>,
        private val details: MutableMap<String, CachedDetailEntity> = mutableMapOf(),
    ) : CatalogDao {
        override suspend fun upsertProducts(products: List<CachedProductEntity>) = Unit
        override suspend fun upsertDetail(detail: CachedDetailEntity) {
            details[detail.id] = detail
        }
        override suspend fun allProducts(): List<CachedProductEntity> = products
        override suspend fun detail(id: String): CachedDetailEntity? = details[id]
        override suspend fun deleteOldProducts(cutoff: Long) = Unit
        override suspend fun deleteOldDetails(cutoff: Long) = Unit
    }
}

class CompatibilityMappingTest {
    @Test
    fun `incompatibility DTO maps outcome evidence and vote aggregates`() {
        val dto = CompatibilityResultDto(
            known = true,
            outcome = "incompatible",
            ruleCount = 1,
            hasIncompatibility = true,
            checkedAt = "2026-10-21T10:00:00Z",
            rules = listOf(
                CheckedRuleDto(
                    id = "r1",
                    relationship = "not_compatible_with",
                    verification = "official",
                    confidence = 98,
                    subjectProductId = "a",
                    subjectProductName = "A",
                    objectProductId = "b",
                    objectProductName = "B",
                    confirmCount = 4,
                    disputeCount = 1,
                    evidence = listOf(
                        EvidenceDto(
                            id = "e1",
                            title = "Service bulletin",
                            sourceUrl = "https://evidence.local/bulletin",
                            evidenceType = "service_bulletin",
                        ),
                    ),
                ),
            ),
        )

        val result = dto.toDomain()

        assertEquals("incompatible", result.outcome)
        assertTrue(result.hasIncompatibility)
        assertEquals(1, result.rules.size)
        assertEquals("not_compatible_with", result.rules.single().relationship)
        assertEquals(1, result.rules.single().evidence.size)
        assertEquals(4, result.rules.single().confirmCount)
        assertEquals(1, result.rules.single().disputeCount)
    }

    @Test
    fun `unknown DTO stays unknown with no rules`() {
        val result = CompatibilityResultDto(known = false, outcome = "unknown").toDomain()

        assertFalse(result.known)
        assertEquals("unknown", result.outcome)
        assertTrue(result.rules.isEmpty())
    }
}
