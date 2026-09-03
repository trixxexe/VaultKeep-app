package com.example.data

data class IntegrityCheckItem(
    val id: String,
    val title: String,
    val description: String,
    val isPassed: Boolean,
    val details: String
)

data class VaultIntegrityReport(
    val timestamp: Long = System.currentTimeMillis(),
    val isAllPassed: Boolean,
    val items: List<IntegrityCheckItem>,
    val failureRecommendation: String? = null
)
