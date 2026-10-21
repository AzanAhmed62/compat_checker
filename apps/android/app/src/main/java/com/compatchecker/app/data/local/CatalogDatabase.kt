package com.compatchecker.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "cached_products")
data class CachedProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brandName: String,
    val categoryName: String,
    val categorySlug: String,
    val modelNumber: String?,
    val summary: String?,
    val imagePath: String?,
    val ruleCount: Int,
    val cachedAt: Long,
)

@Entity(tableName = "cached_details")
data class CachedDetailEntity(
    @PrimaryKey val id: String,
    val payload: String,
    val cachedAt: Long,
)

@Dao
interface CatalogDao {
    @Upsert
    suspend fun upsertProducts(products: List<CachedProductEntity>)

    @Upsert
    suspend fun upsertDetail(detail: CachedDetailEntity)

    @Query("SELECT * FROM cached_products ORDER BY name")
    suspend fun allProducts(): List<CachedProductEntity>

    @Query("SELECT * FROM cached_details WHERE id = :id LIMIT 1")
    suspend fun detail(id: String): CachedDetailEntity?

    @Query("DELETE FROM cached_products WHERE cachedAt < :cutoff")
    suspend fun deleteOldProducts(cutoff: Long)

    @Query("DELETE FROM cached_details WHERE cachedAt < :cutoff")
    suspend fun deleteOldDetails(cutoff: Long)
}

@Database(
    entities = [CachedProductEntity::class, CachedDetailEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CompatDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
