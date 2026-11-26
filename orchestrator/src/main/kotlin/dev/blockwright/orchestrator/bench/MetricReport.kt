package dev.blockwright.orchestrator.bench

import kotlinx.serialization.Serializable

@Serializable
data class MetricReport(
    val metric: String,
    val unit: String,
    val status: String,
    val samples: List<Double>,
    val aggregate: Double?,
    val budget: Double?,
    val reason: String?,
)
