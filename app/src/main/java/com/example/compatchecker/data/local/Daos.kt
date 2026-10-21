package com.example.compatchecker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun getProductById(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductByIdDirect(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR modelNumber LIKE '%' || :query || '%' OR aliases LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE category = :category ORDER BY name ASC")
    fun getProductsByCategory(category: String): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun clearAll()
}

@Dao
interface CompatibilityDao {
    @Query("SELECT * FROM compatibility_rules WHERE sourceProductId = :productId OR targetProductId = :productId")
    fun getRulesForProduct(productId: String): Flow<List<CompatibilityRuleEntity>>

    @Query("SELECT * FROM compatibility_rules WHERE (sourceProductId = :sourceId AND targetProductId = :targetId) OR (sourceProductId = :targetId AND targetProductId = :sourceId) LIMIT 1")
    fun getRuleBetween(sourceId: String, targetId: String): Flow<CompatibilityRuleEntity?>

    @Query("SELECT * FROM compatibility_rules WHERE (sourceProductId = :sourceId AND targetProductId = :targetId) OR (sourceProductId = :targetId AND targetProductId = :sourceId) LIMIT 1")
    suspend fun getRuleBetweenDirect(sourceId: String, targetId: String): CompatibilityRuleEntity?

    @Query("SELECT * FROM compatibility_rules")
    fun getAllRules(): Flow<List<CompatibilityRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<CompatibilityRuleEntity>)

    @Query("DELETE FROM compatibility_rules")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE productId = :productId)")
    fun isFavorite(productId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE productId = :productId")
    suspend fun deleteFavorite(productId: String)
}

@Dao
interface CommunityDao {
    @Query("SELECT * FROM product_requests ORDER BY votesCount DESC, createdAt DESC")
    fun getRequests(): Flow<List<ProductRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: ProductRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<ProductRequestEntity>)

    @Query("UPDATE product_requests SET votesCount = :votes, hasUserVoted = :hasVoted WHERE id = :id")
    suspend fun updateVote(id: String, votes: Int, hasVoted: Boolean)

    @Query("SELECT * FROM comments WHERE productId = :productId ORDER BY createdAt DESC")
    fun getCommentsForProduct(productId: String): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    @Query("UPDATE comments SET upvotes = :votes, hasUserUpvoted = :hasUpvoted WHERE id = :id")
    suspend fun updateCommentUpvote(id: String, votes: Int, hasUpvoted: Boolean)

    @Query("SELECT * FROM contributions ORDER BY submittedAt DESC")
    fun getContributions(): Flow<List<ContributionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(contribution: ContributionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributions(contributions: List<ContributionEntity>)

    @Query("SELECT * FROM repair_guides WHERE productId = :productId")
    fun getGuidesForProduct(productId: String): Flow<List<RepairGuideEntity>>

    @Query("SELECT * FROM repair_guides")
    fun getAllGuides(): Flow<List<RepairGuideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuides(guides: List<RepairGuideEntity>)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
