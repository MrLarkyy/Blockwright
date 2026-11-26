package dev.blockwright.serverrunner.fill

import kotlinx.serialization.Serializable

/** `GET /v3/projects/paper/versions/{version}` (only the fields the runner reads). */
@Serializable
data class FillVersionResponse(val version: Version, val builds: List<Int> = emptyList()) {
    @Serializable
    data class Version(val id: String, val java: Java? = null)

    @Serializable
    data class Java(val version: JavaVersion? = null)

    @Serializable
    data class JavaVersion(val minimum: Int)
}
