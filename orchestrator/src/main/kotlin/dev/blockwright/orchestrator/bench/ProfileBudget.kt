package dev.blockwright.orchestrator.bench

import kotlinx.serialization.Serializable

@Serializable
data class ProfileBudget(
    val required: Boolean = false,
    val clients: Int = 1,
    val warmRuns: Int = 0,
    val idleSeconds: Int = 2,
    val activeSeconds: Int = 2,
    val screenshots: Int = 5,
    val startTimeoutSeconds: Int = 60,
    val budgets: Map<String, Double> = emptyMap(),
)
