package dev.blockwright.launcher.launch.fabric

import kotlinx.serialization.Serializable
import java.net.URI

/**
 * One jar of the Fabric loader runtime, pinned at build time: Maven [coordinates] (`group:name:version`), the
 * [sha1] and [size] of the jar Gradle resolved, and the [repository] it is downloaded from at runtime.
 */
@Serializable
data class FabricArtifact(
    val coordinates: String,
    val sha1: String,
    val size: Long,
    val repository: String,
) {
    /** The Maven layout path, e.g. `net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar`. */
    val path: String
        get() {
            val (group, name, version) = parts()
            return "${group.replace('.', '/')}/$name/$version/$name-$version.jar"
        }

    val uri: URI get() = URI(repository.trimEnd('/') + "/" + path)

    private fun parts(): List<String> {
        val parts = coordinates.split(':')
        require(parts.size == 3 && parts.all { COORDINATE.matches(it) }) { "invalid coordinates: $coordinates" }
        return parts
    }

    private companion object {
        val COORDINATE = Regex("[A-Za-z0-9._+-]+")
    }
}
