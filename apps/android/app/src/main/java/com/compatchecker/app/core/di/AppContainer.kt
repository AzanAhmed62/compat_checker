package com.compatchecker.app.core.di

import android.content.Context
import androidx.room.Room
import com.compatchecker.app.core.config.AppConfig
import com.compatchecker.app.data.local.CompatDatabase
import com.compatchecker.app.data.local.PreferencesRepositoryImpl
import com.compatchecker.app.data.remote.SupabaseGateway
import com.compatchecker.app.data.repository.AuthRepositoryImpl
import com.compatchecker.app.data.repository.CatalogRepositoryImpl
import com.compatchecker.app.data.repository.CommunityRepositoryImpl
import com.compatchecker.app.data.repository.CompatibilityRepositoryImpl
import com.compatchecker.app.data.repository.GuideRepositoryImpl
import com.compatchecker.app.data.repository.ProfileRepositoryImpl
import com.compatchecker.app.data.repository.SocialRepositoryImpl
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.CommunityRepository
import com.compatchecker.app.domain.repository.CompatibilityRepository
import com.compatchecker.app.domain.repository.GuideRepository
import com.compatchecker.app.domain.repository.PreferencesRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.domain.repository.SocialRepository

interface AppContainer {
    val config: AppConfig
    val authRepository: AuthRepository
    val catalogRepository: CatalogRepository
    val compatibilityRepository: CompatibilityRepository
    val guideRepository: GuideRepository
    val communityRepository: CommunityRepository
    val profileRepository: ProfileRepository
    val socialRepository: SocialRepository
    val preferencesRepository: PreferencesRepository
}

class AppContainerImpl(context: Context) : AppContainer {
    override val config: AppConfig = AppConfig.fromBuildConfig()

    private val gateway: SupabaseGateway? =
        if (config.isUsable) SupabaseGateway(config) else null

    private val database: CompatDatabase by lazy {
        Room.databaseBuilder(context, CompatDatabase::class.java, "compat-checker.db")
            .addMigrations(CompatDatabase.MIGRATION_1_2)
            .build()
    }

    override val authRepository: AuthRepository by lazy { AuthRepositoryImpl(gateway) }
    override val catalogRepository: CatalogRepository by lazy {
        CatalogRepositoryImpl(gateway, database.catalogDao())
    }
    override val compatibilityRepository: CompatibilityRepository by lazy {
        CompatibilityRepositoryImpl(gateway)
    }
    override val guideRepository: GuideRepository by lazy {
        GuideRepositoryImpl(gateway, database.catalogDao())
    }
    override val communityRepository: CommunityRepository by lazy {
        CommunityRepositoryImpl(gateway)
    }
    override val profileRepository: ProfileRepository by lazy { ProfileRepositoryImpl(gateway) }
    override val socialRepository: SocialRepository by lazy { SocialRepositoryImpl(gateway) }
    override val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepositoryImpl(context.applicationContext)
    }
}
