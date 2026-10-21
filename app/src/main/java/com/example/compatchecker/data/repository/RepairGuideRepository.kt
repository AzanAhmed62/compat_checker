package com.example.compatchecker.data.repository

import com.example.compatchecker.core.config.DataEnvelope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.core.config.SupabaseConfig
import com.example.compatchecker.data.local.CompatDatabase
import com.example.compatchecker.data.local.RepairGuideEntity
import com.example.compatchecker.data.remote.DemoCatalogDataSource
import com.example.compatchecker.domain.model.RepairGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface RepairGuideRepository {
    fun getAllGuides(): Flow<DataEnvelope<List<RepairGuide>>>
    fun getGuidesForProduct(productId: String): Flow<DataEnvelope<List<RepairGuide>>>
    fun getGuideById(guideId: String): Flow<RepairGuide?>
}

class RepairGuideRepositoryImpl(
    private val database: CompatDatabase
) : RepairGuideRepository {

    private val communityDao = database.communityDao()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            if (communityDao.getAllGuides().first().isEmpty()) {
                val entities = DemoCatalogDataSource.demoGuides.map { guide ->
                    RepairGuideEntity(
                        id = guide.id,
                        productId = guide.productId,
                        title = guide.title,
                        difficulty = guide.difficulty.name,
                        estimatedMinutes = guide.estimatedMinutes,
                        requiredTools = guide.requiredTools,
                        safetyWarnings = guide.safetyWarnings,
                        steps = guide.steps
                    )
                }
                communityDao.insertGuides(entities)
            }
        }
    }

    override fun getAllGuides(): Flow<DataEnvelope<List<RepairGuide>>> = flow {
        communityDao.getAllGuides().collect { entities ->
            val list = entities.map { it.toDomain() }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(list, sourceType))
        }
    }

    override fun getGuidesForProduct(productId: String): Flow<DataEnvelope<List<RepairGuide>>> = flow {
        communityDao.getGuidesForProduct(productId).collect { entities ->
            val list = entities.map { it.toDomain() }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(list, sourceType))
        }
    }

    override fun getGuideById(guideId: String): Flow<RepairGuide?> {
        return communityDao.getAllGuides().map { list ->
            list.find { it.id == guideId }?.toDomain()
        }
    }
}
