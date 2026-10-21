package com.example.compatchecker.data.local

import androidx.room.TypeConverter
import com.example.compatchecker.domain.model.EvidenceSource
import com.example.compatchecker.domain.model.GuideStep
import com.example.compatchecker.domain.model.HardwareVariant
import com.example.compatchecker.domain.model.ProductSpecification
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromHardwareVariants(value: List<HardwareVariant>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toHardwareVariants(value: String?): List<HardwareVariant> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromProductSpecifications(value: List<ProductSpecification>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toProductSpecifications(value: String?): List<ProductSpecification> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromEvidenceSources(value: List<EvidenceSource>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toEvidenceSources(value: String?): List<EvidenceSource> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromGuideSteps(value: List<GuideStep>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toGuideSteps(value: String?): List<GuideStep> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
