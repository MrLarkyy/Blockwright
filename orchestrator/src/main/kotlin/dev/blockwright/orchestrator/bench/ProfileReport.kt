package dev.blockwright.orchestrator.bench

import kotlinx.serialization.Serializable

@Serializable
data class ProfileReport(
    val name: String,
    val required: Boolean,
    val targetAvailable: Boolean,
    val clients: Int,
    val warmRuns: Int,
    val idleSeconds: Int,
    val activeSeconds: Int,
    val screenshots: Int,
    val metrics: List<MetricReport>,
)
