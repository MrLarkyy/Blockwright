package dev.blockwright.orchestrator.bench

import kotlinx.serialization.Serializable

@Serializable
data class BudgetsFile(val schemaVersion: Int, val profiles: Map<String, ProfileBudget>)
