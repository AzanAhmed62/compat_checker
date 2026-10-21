package com.example.compatchecker.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProductCategory(val displayName: String, val iconName: String) {
    SMARTPHONE("Smartphones", "smartphone"),
    REPLACEMENT_DISPLAY("Displays & Panels", "tv"),
    BATTERY("Batteries & Power", "battery_charging_full"),
    CAMERA_MODULE("Camera Sensors & Lenses", "photo_camera"),
    CHARGING_PORT_FLEX("Charging Ports & Flex", "cable"),
    LOGIC_BOARD("Motherboards & Sub-boards", "memory"),
    BACK_HOUSING("Rear Glass & Housings", "dashboard"),
    ACCESSORY("Accessories & Cases", "extension"),
    TOOL_OR_CONSUMABLE("Repair Tools & Adhesives", "build"),
    OTHER("Other Components", "devices_other")
}

@Serializable
data class HardwareVariant(
    val variantCode: String,
    val region: String,
    val notes: String = "",
    val fpcConnectorVersion: String = ""
)

@Serializable
data class ProductSpecification(
    val label: String,
    val value: String,
    val unit: String = "",
    val isCriticalForFit: Boolean = false
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val brand: String,
    val modelNumber: String,
    val category: ProductCategory,
    val releaseYear: Int,
    val formFactor: String,
    val connectorType: String,
    val imageUrl: String = "",
    val summary: String,
    val technicalNotes: String,
    val aliases: List<String> = emptyList(),
    val variants: List<HardwareVariant> = emptyList(),
    val specifications: List<ProductSpecification> = emptyList()
)
