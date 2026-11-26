package dev.blockwright.launcher.manifest

import dev.blockwright.launcher.download.DownloadException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path

/** Decodes Mojang JSON; unknown keys are ignored so new upstream fields never break resolution. */
object ManifestJson {
    val json: Json = Json { ignoreUnknownKeys = true }

    fun <T> decode(deserializer: DeserializationStrategy<T>, file: Path): T =
        try {
            json.decodeFromString(deserializer, Files.readString(file))
        } catch (e: SerializationException) {
            throw DownloadException("malformed JSON in $file: ${e.message}", e)
        } catch (e: CharacterCodingException) {
            throw DownloadException("JSON in $file is not UTF-8", e)
        } catch (e: IllegalArgumentException) {
            throw DownloadException("malformed JSON in $file: ${e.message}", e)
        }
}
