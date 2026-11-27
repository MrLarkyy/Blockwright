package dev.blockwright.launcher.launch

import dev.blockwright.launcher.ResolvedVersion
import dev.blockwright.launcher.manifest.ManifestJson
import dev.blockwright.launcher.manifest.VersionDetails
import java.nio.file.Files
import java.nio.file.Path

/** Synthetic launch inputs: our own fixture version JSON and hand-built agent jars. */
object LaunchFixtures {
    fun details(): VersionDetails {
        val text = LaunchFixtures::class.java.getResourceAsStream("/fixtures/launch-version.json")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        return ManifestJson.json.decodeFromString(VersionDetails.serializer(), text)
    }

    fun version(root: Path, details: VersionDetails = details()): ResolvedVersion = ResolvedVersion(
        id = details.id,
        mainClass = details.mainClass,
        clientJar = root.resolve("versions/client.jar"),
        classpath = listOf(root.resolve("libraries/a.jar"), root.resolve("versions/client.jar")),
        nativesDir = root.resolve("natives"),
        assetsDir = null,
        assetIndexId = null,
        javaMajor = details.javaVersion?.majorVersion,
        details = details,
    )

    fun agentJar(dir: Path, minecraft: String = "1.0-launch", id: String = "blockwright-agent", manifest: String? = null): Path {
        val jar = dir.resolve("agent-$minecraft-$id.jar")
        val json = manifest ?: """{"schemaVersion": 1, "id": "$id", "version": "0.1.0", "depends": {"minecraft": "$minecraft"}}"""
        java.util.zip.ZipOutputStream(Files.newOutputStream(jar)).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("fabric.mod.json"))
            zip.write(json.toByteArray())
            zip.closeEntry()
        }
        return jar
    }
}
