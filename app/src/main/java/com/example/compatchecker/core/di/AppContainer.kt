package com.example.compatchecker.core.di

import android.content.Context
import com.example.compatchecker.core.preferences.UserPreferencesRepository
import com.example.compatchecker.data.local.CompatDatabase
import com.example.compatchecker.data.remote.SupabaseRemoteDataSource
import com.example.compatchecker.data.repository.CommunityRepository
import com.example.compatchecker.data.repository.CommunityRepositoryImpl
import com.example.compatchecker.data.repository.CompatibilityRepository
import com.example.compatchecker.data.repository.CompatibilityRepositoryImpl
import com.example.compatchecker.data.repository.FavoritesRepository
import com.example.compatchecker.data.repository.FavoritesRepositoryImpl
import com.example.compatchecker.data.repository.ProductRepository
import com.example.compatchecker.data.repository.ProductRepositoryImpl
import com.example.compatchecker.data.repository.RepairGuideRepository
import com.example.compatchecker.data.repository.RepairGuideRepositoryImpl
import com.example.compatchecker.data.repository.UserRepository
import com.example.compatchecker.data.repository.UserRepositoryImpl

class AppContainer(val context: Context) {

    val database: CompatDatabase by lazy {
        CompatDatabase.getInstance(context)
    }

    val userPreferences: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    val remoteDataSource: SupabaseRemoteDataSource by lazy {
        SupabaseRemoteDataSource()
    }

    val productRepository: ProductRepository by lazy {
        ProductRepositoryImpl(database, remoteDataSource, userPreferences)
    }

    val compatibilityRepository: CompatibilityRepository by lazy {
        CompatibilityRepositoryImpl(database, remoteDataSource)
    }

    val communityRepository: CommunityRepository by lazy {
        CommunityRepositoryImpl(database, remoteDataSource)
    }

    val favoritesRepository: FavoritesRepository by lazy {
        FavoritesRepositoryImpl(database)
    }

    val repairGuideRepository: RepairGuideRepository by lazy {
        RepairGuideRepositoryImpl(database)
    }

    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(remoteDataSource, userPreferences)
    }
}
