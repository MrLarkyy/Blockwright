package dev.blockwright.serverrunner

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Paper jars under `<root>/paper/<version>/<build>/<fileName>` with their `build.json`. A jar enters the cache
 * only after its size and sha256 match the Fill metadata, and is re-hashed on every use.
 */
class PaperJarCache(
    root: Path,
    private val http: BoundedHttpClient,
    private val downloadTimeout: Duration = 10.minutes,
) {
    private val paperRoot = root.toAbsolutePath().normalize().resolve("paper")
    private val json = Json { ignoreUnknownKeys = true }

    fun obtain(source: PaperBuildSource, version: String, build: Int?): CachedPaperJar {
        PaperBuild.requireVersion(version)
        if (build != null) {
            PaperBuild.requireBuild(build)
            val dir = buildDir(version, build)
            return BuildDirLock.withLock(dir) {
                readCached(dir, version, build) ?: store(dir, source.resolve(version, build))
            }
        }
        val resolved = source.resolve(version, null)
        val dir = buildDir(version, resolved.build)
        return BuildDirLock.withLock(dir) {
            readCached(dir, version, resolved.build)?.takeIf { it.build.sha256 == resolved.sha256 } ?: store(dir, resolved)
        }
    }

    private fun buildDir(version: String, build: Int): Path {
        val dir = paperRoot.resolve(version).resolve(build.toString()).normalize()
        check(dir.startsWith(paperRoot)) { "cache path escapes $paperRoot: $dir" }
        return dir
    }

    private fun readCached(dir: Path, version: String, build: Int): CachedPaperJar? {
        val metadata = dir.resolve(METADATA)
        if (!Files.isRegularFile(metadata)) return null
        val cached = try {
            json.decodeFromString<PaperBuild>(Files.readString(metadata))
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (cached.version != version || cached.build != build || !PaperBuild.isJarFileName(cached.fileName)) return null
        val jar = dir.resolve(cached.fileName)
        if (!Files.isRegularFile(jar) || Files.size(jar) != cached.size || sha256(jar) != cached.sha256) return null
        return CachedPaperJar(cached, jar, dir)
    }

    private fun store(dir: Path, build: PaperBuild): CachedPaperJar {
        if (!PaperBuild.isJarFileName(build.fileName) || !PaperBuild.isSha256(build.sha256) || build.size !in 1..PaperBuild.MAX_JAR_BYTES) {
            throw PaperDownloadException("refusing invalid metadata for Paper ${build.version} build ${build.build}")
        }
        Files.createDirectories(dir)
        val jar = dir.resolve(build.fileName)
        val part = dir.resolve(build.fileName + ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = DigestOutputStream(Files.newOutputStream(part), digest).use { out ->
                http.download(URI(build.url), build.size, downloadTimeout, out)
            }
            if (copied != build.size) throw PaperDownloadException("Paper ${build.version} build ${build.build}: got $copied bytes, Fill declared ${build.size}")
            val actual = digest.digest().toHex()
            if (actual != build.sha256) throw PaperDownloadException("Paper ${build.version} build ${build.build}: sha256 $actual does not match Fill's ${build.sha256}")
            move(part, jar)
        } finally {
            Files.deleteIfExists(part)
        }
        val metadataPart = dir.resolve("$METADATA.part")
        Files.writeString(metadataPart, json.encodeToString(PaperBuild.serializer(), build))
        move(metadataPart, dir.resolve(METADATA))
        return CachedPaperJar(build, jar, dir)
    }

    private fun move(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        } catch (_: IOException) {
            return ""
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = HexFormat.of().formatHex(this)

    private companion object {
        const val METADATA = "build.json"
    }
}
