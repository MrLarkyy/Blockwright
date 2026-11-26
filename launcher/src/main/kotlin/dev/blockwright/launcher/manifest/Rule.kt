package dev.blockwright.launcher.manifest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Rule(
    val action: RuleAction,
    val os: OsRule? = null,
    val features: JsonObject? = null,
)
