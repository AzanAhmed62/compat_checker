package com.example.compatchecker.data.repository

import com.example.compatchecker.core.config.DataEnvelope
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.core.config.SupabaseConfig
import com.example.compatchecker.data.local.CommentEntity
import com.example.compatchecker.data.local.CompatDatabase
import com.example.compatchecker.data.local.ContributionEntity
import com.example.compatchecker.data.local.ProductRequestEntity
import com.example.compatchecker.data.remote.DemoCatalogDataSource
import com.example.compatchecker.data.remote.SupabaseRemoteDataSource
import com.example.compatchecker.domain.model.Comment
import com.example.compatchecker.domain.model.Contribution
import com.example.compatchecker.domain.model.ProductRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

interface CommunityRepository {
    fun getProductRequests(): Flow<DataEnvelope<List<ProductRequest>>>
    suspend fun voteRequest(requestId: String, currentlyVoted: Boolean)
    suspend fun submitProductRequest(request: ProductRequest): Boolean

    fun getCommentsForProduct(productId: String): Flow<DataEnvelope<List<Comment>>>
    suspend fun submitComment(comment: Comment): Boolean
    suspend fun upvoteComment(commentId: String, currentUpvoted: Boolean)

    fun getContributions(): Flow<DataEnvelope<List<Contribution>>>
    suspend fun submitContribution(contribution: Contribution): Boolean
}

class CommunityRepositoryImpl(
    private val database: CompatDatabase,
    private val remoteDataSource: SupabaseRemoteDataSource
) : CommunityRepository {

    private val communityDao = database.communityDao()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            if (communityDao.getRequests().first().isEmpty()) {
                val requests = DemoCatalogDataSource.demoRequests.map {
                    ProductRequestEntity(
                        id = it.id,
                        targetDeviceName = it.targetDeviceName,
                        targetModelNumber = it.targetModelNumber,
                        componentType = it.componentType.name,
                        notes = it.notes,
                        votesCount = it.votesCount,
                        hasUserVoted = it.hasUserVoted,
                        status = it.status.name,
                        createdAt = it.createdAt
                    )
                }
                communityDao.insertRequests(requests)
            }

            // Seed demo comments
            DemoCatalogDataSource.demoComments.forEach { comment ->
                communityDao.insertComment(
                    CommentEntity(
                        id = comment.id,
                        productId = comment.productId,
                        authorName = comment.authorName,
                        authorRole = comment.authorRole,
                        content = comment.content,
                        type = comment.type.name,
                        upvotes = comment.upvotes,
                        hasUserUpvoted = comment.hasUserUpvoted,
                        createdAt = comment.createdAt
                    )
                )
            }

            // Seed demo contributions
            DemoCatalogDataSource.demoContributions.forEach { c ->
                communityDao.insertContribution(
                    ContributionEntity(
                        id = c.id,
                        title = c.title,
                        sourceProductName = c.sourceProductName,
                        targetProductName = c.targetProductName,
                        proposedStatus = c.proposedStatus.name,
                        evidenceUrl = c.evidenceUrl,
                        technicalNotes = c.technicalNotes,
                        submitterEmail = c.submitterEmail,
                        status = c.status.name,
                        submittedAt = c.submittedAt
                    )
                )
            }
        }
    }

    override fun getProductRequests(): Flow<DataEnvelope<List<ProductRequest>>> = flow {
        communityDao.getRequests().collect { entities ->
            val list = entities.map { it.toDomain() }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(list, sourceType))
        }
    }

    override suspend fun voteRequest(requestId: String, currentlyVoted: Boolean) {
        val requests = communityDao.getRequests().first()
        val target = requests.find { it.id == requestId } ?: return
        val newCount = if (currentlyVoted) target.votesCount - 1 else target.votesCount + 1
        communityDao.updateVote(requestId, newCount.coerceAtLeast(0), !currentlyVoted)
    }

    override suspend fun submitProductRequest(request: ProductRequest): Boolean {
        val entity = ProductRequestEntity(
            id = request.id,
            targetDeviceName = request.targetDeviceName,
            targetModelNumber = request.targetModelNumber,
            componentType = request.componentType.name,
            notes = request.notes,
            votesCount = request.votesCount,
            hasUserVoted = request.hasUserVoted,
            status = request.status.name,
            createdAt = request.createdAt
        )
        communityDao.insertRequest(entity)
        if (SupabaseConfig.isConfigured) {
            remoteDataSource.submitProductRequest(request)
        }
        return true
    }

    override fun getCommentsForProduct(productId: String): Flow<DataEnvelope<List<Comment>>> = flow {
        communityDao.getCommentsForProduct(productId).collect { entities ->
            val list = entities.map { it.toDomain() }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(list, sourceType))
        }
    }

    override suspend fun submitComment(comment: Comment): Boolean {
        val entity = CommentEntity(
            id = comment.id,
            productId = comment.productId,
            authorName = comment.authorName,
            authorRole = comment.authorRole,
            content = comment.content,
            type = comment.type.name,
            upvotes = comment.upvotes,
            hasUserUpvoted = comment.hasUserUpvoted,
            createdAt = comment.createdAt
        )
        communityDao.insertComment(entity)
        if (SupabaseConfig.isConfigured) {
            remoteDataSource.submitComment(comment)
        }
        return true
    }

    override suspend fun upvoteComment(commentId: String, currentUpvoted: Boolean) {
        // Query through DAO or update
        val target = communityDao.getCommentsForProduct("").first().find { it.id == commentId }
        if (target != null) {
            val newUpvotes = if (currentUpvoted) target.upvotes - 1 else target.upvotes + 1
            communityDao.updateCommentUpvote(commentId, newUpvotes.coerceAtLeast(0), !currentUpvoted)
        }
    }

    override fun getContributions(): Flow<DataEnvelope<List<Contribution>>> = flow {
        communityDao.getContributions().collect { entities ->
            val list = entities.map { it.toDomain() }
            val sourceType = if (SupabaseConfig.isConfigured) DataSourceType.CACHED_OFFLINE else DataSourceType.DEMONSTRATION
            emit(DataEnvelope(list, sourceType))
        }
    }

    override suspend fun submitContribution(contribution: Contribution): Boolean {
        val entity = ContributionEntity(
            id = contribution.id,
            title = contribution.title,
            sourceProductName = contribution.sourceProductName,
            targetProductName = contribution.targetProductName,
            proposedStatus = contribution.proposedStatus.name,
            evidenceUrl = contribution.evidenceUrl,
            technicalNotes = contribution.technicalNotes,
            submitterEmail = contribution.submitterEmail,
            status = contribution.status.name,
            submittedAt = contribution.submittedAt
        )
        communityDao.insertContribution(entity)
        if (SupabaseConfig.isConfigured) {
            remoteDataSource.submitContribution(contribution)
        }
        return true
    }
}
