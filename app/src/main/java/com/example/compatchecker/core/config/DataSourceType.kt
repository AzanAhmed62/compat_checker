package com.example.compatchecker.core.config

enum class DataSourceType(val label: String, val isDemonstration: Boolean) {
    DEMONSTRATION(
        label = "Demonstration Benchmark",
        isDemonstration = true
    ),
    CACHED_OFFLINE(
        label = "Offline Local Cache",
        isDemonstration = false
    ),
    LIVE_SUPABASE(
        label = "Live Supabase Backend",
        isDemonstration = false
    )
}

data class DataEnvelope<T>(
    val data: T,
    val sourceType: DataSourceType,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
    val notice: String? = null
)
