package com.example.compatchecker.data.repository

import com.example.compatchecker.data.local.CompatDatabase
import com.example.compatchecker.data.local.FavoriteEntity
import com.example.compatchecker.domain.model.Favorite
import com.example.compatchecker.domain.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface FavoritesRepository {
    fun getFavorites(): Flow<List<Favorite>>
    fun isFavorite(productId: String): Flow<Boolean>
    suspend fun toggleFavorite(product: Product): Boolean
    suspend fun removeFavorite(productId: String)
}

class FavoritesRepositoryImpl(
    private val database: CompatDatabase
) : FavoritesRepository {

    private val favoriteDao = database.favoriteDao()

    override fun getFavorites(): Flow<List<Favorite>> {
        return favoriteDao.getFavorites().map { entities -> entities.map { it.toDomain() } }
    }

    override fun isFavorite(productId: String): Flow<Boolean> {
        return favoriteDao.isFavorite(productId)
    }

    override suspend fun toggleFavorite(product: Product): Boolean {
        val already = favoriteDao.isFavorite(product.id).first()
        if (already) {
            favoriteDao.deleteFavorite(product.id)
            return false
        } else {
            val entity = FavoriteEntity(
                id = "fav_${product.id}",
                productId = product.id,
                productName = product.name,
                brand = product.brand,
                category = product.category.name,
                modelNumber = product.modelNumber,
                savedAt = System.currentTimeMillis()
            )
            favoriteDao.insertFavorite(entity)
            return true
        }
    }

    override suspend fun removeFavorite(productId: String) {
        favoriteDao.deleteFavorite(productId)
    }
}
