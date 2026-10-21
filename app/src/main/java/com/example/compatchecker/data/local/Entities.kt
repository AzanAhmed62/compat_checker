package com.example.compatchecker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.compatchecker.domain.model.Comment
import com.example.compatchecker.domain.model.CommentType
import com.example.compatchecker.domain.model.CompatibilityRule
import com.example.compatchecker.domain.model.CompatibilityStatus
import com.example.compatchecker.domain.model.ConfidenceLevel
import com.example.compatchecker.domain.model.Contribution
import com.example.compatchecker.domain.model.ContributionStatus
import com.example.compatchecker.domain.model.EvidenceSource
import com.example.compatchecker.domain.model.Favorite
import com.example.compatchecker.domain.model.GuideDifficulty
import com.example.compatchecker.domain.model.GuideStep
import com.example.compatchecker.domain.model.HardwareVariant
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.ProductCategory
import com.example.compatchecker.domain.model.ProductRequest
import com.example.compatchecker.domain.model.ProductSpecification
import com.example.compatchecker.domain.model.RepairGuide
import com.example.compatchecker.domain.model.RequestStatus
import com.example.compatchecker.domain.model.RiskLevel

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String,
    val modelNumber: String,
    val category: String,
    val releaseYear: Int,
    val formFactor: String,
    val connectorType: String,
    val imageUrl: String,
    val summary: String,
    val technicalNotes: String,
    val aliases: List<String>,
    val variants: List<HardwareVariant>,
    val specifications: List<ProductSpecification>,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Product {
        return Product(
            id = id,
            name = name,
            brand = brand,
            modelNumber = modelNumber,
            category = try { ProductCategory.valueOf(category) } catch (e: Exception) { ProductCategory.OTHER },
            releaseYear = releaseYear,
            formFactor = formFactor,
            connectorType = connectorType,
            imageUrl = imageUrl,
            summary = summary,
            technicalNotes = technicalNotes,
            aliases = aliases,
            variants = variants,
            specifications = specifications
        )
    }

    companion object {
        fun fromDomain(domain: Product): ProductEntity {
            return ProductEntity(
                id = domain.id,
                name = domain.name,
                brand = domain.brand,
                modelNumber = domain.modelNumber,
                category = domain.category.name,
                releaseYear = domain.releaseYear,
                formFactor = domain.formFactor,
                connectorType = domain.connectorType,
                imageUrl = domain.imageUrl,
                summary = domain.summary,
                technicalNotes = domain.technicalNotes,
                aliases = domain.aliases,
                variants = domain.variants,
                specifications = domain.specifications
            )
        }
    }
}

@Entity(tableName = "compatibility_rules")
data class CompatibilityRuleEntity(
    @PrimaryKey val id: String,
    val sourceProductId: String,
    val targetProductId: String,
    val sourceProductName: String,
    val targetProductName: String,
    val status: String,
    val confidence: String,
    val summary: String,
    val technicalDetails: String,
    val requiredModifications: String,
    val riskLevel: String,
    val evidenceSources: List<EvidenceSource>,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): CompatibilityRule {
        return CompatibilityRule(
            id = id,
            sourceProductId = sourceProductId,
            targetProductId = targetProductId,
            sourceProductName = sourceProductName,
            targetProductName = targetProductName,
            status = try { CompatibilityStatus.valueOf(status) } catch (e: Exception) { CompatibilityStatus.UNVERIFIED },
            confidence = try { ConfidenceLevel.valueOf(confidence) } catch (e: Exception) { ConfidenceLevel.LOW_EXPERIMENTAL },
            summary = summary,
            technicalDetails = technicalDetails,
            requiredModifications = requiredModifications,
            riskLevel = try { RiskLevel.valueOf(riskLevel) } catch (e: Exception) { RiskLevel.NONE },
            evidenceSources = evidenceSources
        )
    }

    companion object {
        fun fromDomain(domain: CompatibilityRule): CompatibilityRuleEntity {
            return CompatibilityRuleEntity(
                id = domain.id,
                sourceProductId = domain.sourceProductId,
                targetProductId = domain.targetProductId,
                sourceProductName = domain.sourceProductName,
                targetProductName = domain.targetProductName,
                status = domain.status.name,
                confidence = domain.confidence.name,
                summary = domain.summary,
                technicalDetails = domain.technicalDetails,
                requiredModifications = domain.requiredModifications,
                riskLevel = domain.riskLevel.name,
                evidenceSources = domain.evidenceSources
            )
        }
    }
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val brand: String,
    val category: String,
    val modelNumber: String,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Favorite {
        return Favorite(
            id = id,
            productId = productId,
            productName = productName,
            brand = brand,
            category = try { ProductCategory.valueOf(category) } catch (e: Exception) { ProductCategory.OTHER },
            modelNumber = modelNumber,
            savedAt = savedAt
        )
    }
}

@Entity(tableName = "product_requests")
data class ProductRequestEntity(
    @PrimaryKey val id: String,
    val targetDeviceName: String,
    val targetModelNumber: String,
    val componentType: String,
    val notes: String,
    val votesCount: Int,
    val hasUserVoted: Boolean,
    val status: String,
    val createdAt: Long
) {
    fun toDomain(): ProductRequest {
        return ProductRequest(
            id = id,
            targetDeviceName = targetDeviceName,
            targetModelNumber = targetModelNumber,
            componentType = try { ProductCategory.valueOf(componentType) } catch (e: Exception) { ProductCategory.OTHER },
            notes = notes,
            votesCount = votesCount,
            hasUserVoted = hasUserVoted,
            status = try { RequestStatus.valueOf(status) } catch (e: Exception) { RequestStatus.OPEN_VOTING },
            createdAt = createdAt
        )
    }
}

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val authorName: String,
    val authorRole: String,
    val content: String,
    val type: String,
    val upvotes: Int,
    val hasUserUpvoted: Boolean,
    val createdAt: Long
) {
    fun toDomain(): Comment {
        return Comment(
            id = id,
            productId = productId,
            authorName = authorName,
            authorRole = authorRole,
            content = content,
            type = try { CommentType.valueOf(type) } catch (e: Exception) { CommentType.GENERAL },
            upvotes = upvotes,
            hasUserUpvoted = hasUserUpvoted,
            createdAt = createdAt
        )
    }
}

@Entity(tableName = "repair_guides")
data class RepairGuideEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val title: String,
    val difficulty: String,
    val estimatedMinutes: Int,
    val requiredTools: List<String>,
    val safetyWarnings: List<String>,
    val steps: List<GuideStep>
) {
    fun toDomain(): RepairGuide {
        return RepairGuide(
            id = id,
            productId = productId,
            title = title,
            difficulty = try { GuideDifficulty.valueOf(difficulty) } catch (e: Exception) { GuideDifficulty.INTERMEDIATE },
            estimatedMinutes = estimatedMinutes,
            requiredTools = requiredTools,
            safetyWarnings = safetyWarnings,
            steps = steps
        )
    }
}

@Entity(tableName = "contributions")
data class ContributionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceProductName: String,
    val targetProductName: String,
    val proposedStatus: String,
    val evidenceUrl: String,
    val technicalNotes: String,
    val submitterEmail: String,
    val status: String,
    val submittedAt: Long
) {
    fun toDomain(): Contribution {
        return Contribution(
            id = id,
            title = title,
            sourceProductName = sourceProductName,
            targetProductName = targetProductName,
            proposedStatus = try { CompatibilityStatus.valueOf(proposedStatus) } catch (e: Exception) { CompatibilityStatus.UNVERIFIED },
            evidenceUrl = evidenceUrl,
            technicalNotes = technicalNotes,
            submitterEmail = submitterEmail,
            status = try { ContributionStatus.valueOf(status) } catch (e: Exception) { ContributionStatus.PENDING_REVIEW },
            submittedAt = submittedAt
        )
    }
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
