package com.example.compatchecker.data.remote

import android.util.Log
import com.example.compatchecker.core.config.SupabaseConfig
import com.example.compatchecker.domain.model.Comment
import com.example.compatchecker.domain.model.CompatibilityRule
import com.example.compatchecker.domain.model.Contribution
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.ProductRequest
import com.example.compatchecker.domain.model.RepairGuide
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseRemoteDataSource {

    private val tag = "SupabaseRemoteDataSource"

    val isAvailable: Boolean
        get() = SupabaseConfig.isConfigured && SupabaseConfig.client != null

    suspend fun fetchProducts(): List<Product>? = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext null
        try {
            client.postgrest["products"].select().decodeList<Product>()
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch products from Supabase: ${e.message}")
            null
        }
    }

    suspend fun fetchCompatibilityRules(): List<CompatibilityRule>? = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext null
        try {
            client.postgrest["compatibility_rules"].select().decodeList<CompatibilityRule>()
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch rules from Supabase: ${e.message}")
            null
        }
    }

    suspend fun fetchProductRequests(): List<ProductRequest>? = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext null
        try {
            client.postgrest["product_requests"].select().decodeList<ProductRequest>()
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch product requests from Supabase: ${e.message}")
            null
        }
    }

    suspend fun submitProductRequest(request: ProductRequest): Boolean = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext false
        try {
            client.postgrest["product_requests"].insert(request)
            true
        } catch (e: Exception) {
            Log.w(tag, "Failed to insert request into Supabase: ${e.message}")
            false
        }
    }

    suspend fun fetchComments(productId: String): List<Comment>? = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext null
        try {
            client.postgrest["comments"].select {
                filter { eq("productId", productId) }
            }.decodeList<Comment>()
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch comments from Supabase: ${e.message}")
            null
        }
    }

    suspend fun submitComment(comment: Comment): Boolean = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext false
        try {
            client.postgrest["comments"].insert(comment)
            true
        } catch (e: Exception) {
            Log.w(tag, "Failed to insert comment into Supabase: ${e.message}")
            false
        }
    }

    suspend fun fetchRepairGuides(): List<RepairGuide>? = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext null
        try {
            client.postgrest["repair_guides"].select().decodeList<RepairGuide>()
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch repair guides from Supabase: ${e.message}")
            null
        }
    }

    suspend fun submitContribution(contribution: Contribution): Boolean = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext false
        try {
            client.postgrest["contributions"].insert(contribution)
            true
        } catch (e: Exception) {
            Log.w(tag, "Failed to insert contribution into Supabase: ${e.message}")
            false
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            val userId = client.auth.currentUserOrNull()?.id ?: "authenticated_user"
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        val client = SupabaseConfig.client ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
            }
            val userId = client.auth.currentUserOrNull()?.id ?: "new_user"
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            SupabaseConfig.client?.auth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error signing out: ${e.message}")
        }
    }
}
