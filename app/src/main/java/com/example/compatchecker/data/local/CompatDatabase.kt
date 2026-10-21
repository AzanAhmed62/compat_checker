package com.example.compatchecker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProductEntity::class,
        CompatibilityRuleEntity::class,
        FavoriteEntity::class,
        ProductRequestEntity::class,
        CommentEntity::class,
        RepairGuideEntity::class,
        ContributionEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CompatDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun compatibilityDao(): CompatibilityDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun communityDao(): CommunityDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: CompatDatabase? = null

        fun getInstance(context: Context): CompatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CompatDatabase::class.java,
                    "compat_checker.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
