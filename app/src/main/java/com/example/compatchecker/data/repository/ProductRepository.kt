package com.example.compatchecker.data.repository

import com.example.compatchecker.core.config.DataEnvelope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.core.config.SupabaseConfig
import com.example.compatchecker.core.preferences.UserPreferencesRepository
import com.example.compatchecker.data.local.CompatDatabase
import com.example.compatchecker.data.local.ProductEntity
import com.example.compatchecker.data.local.SearchHistoryEntity
import com.example.compatchecker.data.remote.DemoCatalogDataSource
import com.example.compatchecker.data.remote.SupabaseRemoteDataSource
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.ProductCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface ProductRepository {
    fun getProducts(): Flow<DataEnvelope<List<Product>>>
    fun getProductById(id: String): Flow<DataEnvelope<Product?>>
    fun searchProducts(query: String, category: ProductCategory? = null, brand: String? = null): Flow<DataEnvelope<List<Product>>>
    suspend fun refreshCatalog(): Boolean
    fun getRecentSearches(): Flow<List<String>>
    suspend fun addSearchQuery(query: String)
    suspend fun clearSearchHistory()
}

class ProductRepositoryImpl(
    private val database: CompatDatabase,
    private val remoteDataSource: SupabaseRemoteDataSource,
    private val userPreferences: UserPreferencesRepository
) : ProductRepository {

    private val productDao = database.productDao()
    private val searchHistoryDao = database.searchHistoryDao()

    init {
        // Pre-populate Room cache with verified demonstration catalog on first run
        CoroutineScope(Dispatchers.IO).launch {
            val localCount = productDao.getAllProducts().first().size
            if (localCount == 0) {
                val initialEntities = DemoCatalogDataSource.demoProducts.map { ProductEntity.fromDomain(it) }
                productDao.insertProducts(initialEntities)
            }
        }
    }

    override fun getProducts(): Flow<DataEnvelope<List<Product>>> = flow {
        val prefs = userPreferences.userPreferencesFlow.first()
        val isForceOffline = prefs.forceOfflineMode

        if (!isForceOffline && SupabaseConfig.isConfigured) {
            val remote = remoteDataSource.fetchProducts()
            if (!remote.isNullOrEmpty()) {
                productDao.insertProducts(remote.map { ProductEntity.fromDomain(it) })
                emit(DataEnvelope(remote, DataSourceType.LIVE_SUPABASE))
                return@flow
            }
        }

        // Return from local Room database
        productDao.getAllProducts().collect { entities ->
            val domainList = entities.map { it.toDomain() }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(domainList, sourceType))
        }
    }

    override fun getProductById(id: String): Flow<DataEnvelope<Product?>> = flow {
        productDao.getProductById(id).collect { entity ->
            val product = entity?.toDomain()
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(product, sourceType))
        }
    }

    override fun searchProducts(
        query: String,
        category: ProductCategory?,
        brand: String?
    ): Flow<DataEnvelope<List<Product>>> = flow {
        val cleanQuery = query.trim().lowercase()
        productDao.getAllProducts().collect { entities ->
            val filtered = entities.map { it.toDomain() }.filter { prod ->
                val matchesQuery = cleanQuery.isEmpty() ||
                        prod.name.lowercase().contains(cleanQuery) ||
                        prod.modelNumber.lowercase().contains(cleanQuery) ||
                        prod.brand.lowercase().contains(cleanQuery) ||
                        prod.aliases.any { it.lowercase().contains(cleanQuery) }

                val matchesCategory = category == null || prod.category == category
                val matchesBrand = brand.isNullOrBlank() || prod.brand.equals(brand, ignoreCase = true)

                matchesQuery && matchesCategory && matchesBrand
            }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(filtered, sourceType))
        }
    }

    override suspend fun refreshCatalog(): Boolean {
        if (!SupabaseConfig.isConfigured) {
            // Re-seed demo data in local database
            val initialEntities = DemoCatalogDataSource.demoProducts.map { ProductEntity.fromDomain(it) }
            productDao.insertProducts(initialEntities)
            return true
        }
        val remote = remoteDataSource.fetchProducts()
        return if (!remote.isNullOrEmpty()) {
            productDao.insertProducts(remote.map { ProductEntity.fromDomain(it) })
            true
        } else {
            false
        }
    }

    override fun getRecentSearches(): Flow<List<String>> {
        return searchHistoryDao.getRecentSearches().map { list -> list.map { it.query } }
    }

    override suspend fun addSearchQuery(query: String) {
        if (query.isNotBlank()) {
            searchHistoryDao.insertSearch(SearchHistoryEntity(query = query.trim()))
        }
    }

    override suspend fun clearSearchHistory() {
        searchHistoryDao.clearSearchHistory()
    }
}
