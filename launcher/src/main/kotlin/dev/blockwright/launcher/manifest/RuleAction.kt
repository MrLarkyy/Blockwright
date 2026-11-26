package dev.blockwright.launcher.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RuleAction {
    @SerialName("allow")
    ALLOW,

    @SerialName("disallow")
    DISALLOW,
}
