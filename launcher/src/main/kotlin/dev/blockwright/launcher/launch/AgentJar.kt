package dev.blockwright.launcher.launch

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * The Blockwright agent mod: its `fabric.mod.json` names the one Minecraft version it was built against. Checked
 * before launch, so a client of another version fails here with a clear message instead of deep inside Fabric.
 */
data class AgentJar(val path: Path, val version: String, val minecraftVersion: String) {
    fun requireMinecraft(versionId: String) {
        if (minecraftVersion != versionId) {
            throw LaunchException("agent jar $path is built for Minecraft $minecraftVersion, not $versionId")
        }
    }

    companion object {
        const val MOD_ID = "blockwright-agent"
        private const val MANIFEST = "fabric.mod.json"
        const val MAX_MANIFEST_BYTES = 64 * 1024

        fun read(path: Path): AgentJar {
            if (!Files.isRegularFile(path)) throw LaunchException("agent jar not found: $path")
            val manifest = try {
                ZipFile(path.toFile()).use { zip -> manifestText(zip, path) }
            } catch (e: ZipException) {
                throw LaunchException("agent jar $path is not a jar: ${e.message}", e)
            } catch (e: IOException) {
                throw LaunchException("cannot read agent jar $path: ${e.message}", e)
            }
            return parse(path, manifest)
        }

        private fun manifestText(zip: ZipFile, path: Path): String {
            val entry = zip.getEntry(MANIFEST) ?: throw LaunchException("agent jar $path has no $MANIFEST")
            val bytes = zip.getInputStream(entry).use { it.readNBytes(MAX_MANIFEST_BYTES + 1) }
            if (bytes.size > MAX_MANIFEST_BYTES) throw LaunchException("$MANIFEST in $path exceeds $MAX_MANIFEST_BYTES bytes")
            return bytes.toString(Charsets.UTF_8)
        }

        private fun parse(path: Path, text: String): AgentJar {
            val json = try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: SerializationException) {
                throw LaunchException("$MANIFEST in $path is invalid: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw LaunchException("$MANIFEST in $path is not an object", e)
            }
            if (json.string("id") != MOD_ID) throw LaunchException("$path is not the Blockwright agent (id ${json.string("id")})")
            val minecraft = (json["depends"] as? JsonObject)?.string("minecraft")
                ?: throw LaunchException("$MANIFEST in $path names no Minecraft version")
            return AgentJar(path, json.string("version") ?: "unknown", minecraft)
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}
