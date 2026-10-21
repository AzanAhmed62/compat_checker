package com.example.compatchecker.data.repository

import com.example.compatchecker.core.config.DataEnvelope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.core.config.SupabaseConfig
import com.example.compatchecker.data.local.CompatDatabase
import com.example.compatchecker.data.local.CompatibilityRuleEntity
import com.example.compatchecker.data.remote.DemoCatalogDataSource
import com.example.compatchecker.data.remote.SupabaseRemoteDataSource
import com.example.compatchecker.domain.model.CompatibilityRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

interface CompatibilityRepository {
    fun getRulesForProduct(productId: String): Flow<DataEnvelope<List<CompatibilityRule>>>
    fun checkCompatibility(sourceProductId: String, targetProductId: String): Flow<DataEnvelope<CompatibilityRule?>>
    fun getAllRules(): Flow<DataEnvelope<List<CompatibilityRule>>>
    suspend fun refreshRules(): Boolean
}

class CompatibilityRepositoryImpl(
    private val database: CompatDatabase,
    private val remoteDataSource: SupabaseRemoteDataSource
) : CompatibilityRepository {

    private val compatibilityDao = database.compatibilityDao()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val count = compatibilityDao.getAllRules().first().size
            if (count == 0) {
                val demoEntities = DemoCatalogDataSource.demoRules.map { CompatibilityRuleEntity.fromDomain(it) }
                compatibilityDao.insertRules(demoEntities)
            }
        }
    }

    override fun getRulesForProduct(productId: String): Flow<DataEnvelope<List<CompatibilityRule>>> = flow {
        compatibilityDao.getRulesForProduct(productId).collect { entities ->
            val domainRules = entities.map { it.toDomain() }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(domainRules, sourceType))
        }
    }

    override fun checkCompatibility(
        sourceProductId: String,
        targetProductId: String
    ): Flow<DataEnvelope<CompatibilityRule?>> = flow {
        compatibilityDao.getRuleBetween(sourceProductId, targetProductId).collect { entity ->
            val rule = entity?.toDomain()
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(rule, sourceType))
        }
    }

    override fun getAllRules(): Flow<DataEnvelope<List<CompatibilityRule>>> = flow {
        compatibilityDao.getAllRules().collect { entities ->
            val rules = entities.map { it.toDomain() }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(rules, sourceType))
        }
    }

    override suspend fun refreshRules(): Boolean {
        if (!SupabaseConfig.isConfigured) {
            val demoEntities = DemoCatalogDataSource.demoRules.map { CompatibilityRuleEntity.fromDomain(it) }
            compatibilityDao.insertRules(demoEntities)
            return true
        }
        val remote = remoteDataSource.fetchCompatibilityRules()
        return if (!remote.isNullOrEmpty()) {
            compatibilityDao.insertRules(remote.map { CompatibilityRuleEntity.fromDomain(it) })
            true
        } else {
            false
        }
    }
}
