package com.compatchecker.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "cached_products")
data class CachedProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brandName: String,
    @ColumnInfo(defaultValue = "")
    val brandSlug: String,
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

@Entity(tableName = "cached_guides")
data class CachedGuideEntity(
    @PrimaryKey val id: String,
    val payload: String,
    val cachedAt: Long,
)

@Entity(tableName = "cached_guide_details")
data class CachedGuideDetailEntity(
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

    @Upsert
    suspend fun upsertGuides(guides: List<CachedGuideEntity>)

    @Upsert
    suspend fun upsertGuideDetail(detail: CachedGuideDetailEntity)

    @Query("SELECT * FROM cached_products ORDER BY name")
    suspend fun allProducts(): List<CachedProductEntity>

    @Query("SELECT * FROM cached_details WHERE id = :id LIMIT 1")
    suspend fun detail(id: String): CachedDetailEntity?

    @Query("SELECT * FROM cached_guides ORDER BY cachedAt DESC")
    suspend fun allGuides(): List<CachedGuideEntity>

    @Query("SELECT * FROM cached_guides WHERE id = :id LIMIT 1")
    suspend fun guide(id: String): CachedGuideEntity?

    @Query("SELECT * FROM cached_guide_details WHERE id = :id LIMIT 1")
    suspend fun guideDetail(id: String): CachedGuideDetailEntity?

    @Query("DELETE FROM cached_products WHERE cachedAt < :cutoff")
    suspend fun deleteOldProducts(cutoff: Long)

    @Query("DELETE FROM cached_details WHERE cachedAt < :cutoff")
    suspend fun deleteOldDetails(cutoff: Long)

    @Query("DELETE FROM cached_guides WHERE cachedAt < :cutoff")
    suspend fun deleteOldGuides(cutoff: Long)

    @Query("DELETE FROM cached_guide_details WHERE cachedAt < :cutoff")
    suspend fun deleteOldGuideDetails(cutoff: Long)
}

@Database(
    entities = [
        CachedProductEntity::class,
        CachedDetailEntity::class,
        CachedGuideEntity::class,
        CachedGuideDetailEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CompatDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        /**
         * Adds the brand slug used by catalog filters and the offline guide
         * cache. Existing cached rows are preserved; the brand slug is filled
         * from the next successful search or stays blank in saved results.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE cached_products ADD COLUMN brandSlug TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS cached_guides (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "payload TEXT NOT NULL, " +
                        "cachedAt INTEGER NOT NULL)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS cached_guide_details (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "payload TEXT NOT NULL, " +
                        "cachedAt INTEGER NOT NULL)",
                )
            }
        }
    }
}
