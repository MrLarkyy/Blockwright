package dev.blockwright.launcher.cache

import dev.blockwright.launcher.download.DownloadException
import dev.blockwright.launcher.natives.NativesExtractor
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/**
 * The shared download cache, laid out like the vanilla launcher's game directory. Every name that comes from a
 * manifest is validated so no resolved path can leave [root] and no version can take the name of the staging or
 * backup directory the cache keeps next to another version.
 */
class CacheLayout(root: Path) {
    val root: Path = root.toAbsolutePath().normalize()

    fun manifest(): Path = root.resolve("manifest").resolve("version_manifest_v2.json")

    fun versionJson(id: String): Path = versionDir(id).resolve("$id.json")

    fun versionJsonSha1(id: String): Path = versionDir(id).resolve("$id.json.sha1")

    fun clientJar(id: String): Path = versionDir(id).resolve("$id.jar")

    fun library(path: String): Path = within(root.resolve("libraries"), safeRelativePath(path))

    fun assetsDir(): Path = root.resolve("assets")

    fun assetIndex(id: String): Path = assetsDir().resolve("indexes").resolve("${checkId(id)}.json")

    fun assetObject(hash: String): Path {
        if (!HASH.matches(hash)) throw DownloadException("invalid asset hash: ${printable(hash)}")
        return assetsDir().resolve("objects").resolve(hash.substring(0, 2)).resolve(hash)
    }

    fun nativesDir(id: String): Path = root.resolve("natives").resolve(checkId(id))

    /**
     * The lock file of a cache entry, in `<root>/locks`, never beside the payload. Named by a hash of the entry's
     * path below [root] (lowercase, as Windows folds case) so a manifest-controlled path cannot clash with another
     * file or directory there. Lock files are never deleted, like the entries they guard they only accumulate.
     */
    fun lockFile(target: Path): Path {
        val absolute = target.toAbsolutePath().normalize()
        if (!absolute.startsWith(root) || absolute == root) {
            throw DownloadException("cache entry outside the cache: ${printable(absolute.toString())}")
        }
        val key = root.relativize(absolute).joinToString("/").lowercase()
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return root.resolve("locks").resolve("${HexFormat.of().formatHex(digest)}.lock")
    }

    private fun versionDir(id: String): Path = root.resolve("versions").resolve(checkId(id))

    private fun checkId(id: String): String {
        val valid = id.length <= MAX_ID_LENGTH && ID.matches(id) && !isBookkeepingName(id)
        if (!valid) throw DownloadException("invalid version id: ${printable(id)}")
        return id
    }

    /**
     * Natives directories share their parent with `<id>.tmp-*` and `<id>.old-*` directories that are removed as
     * crash leftovers. Compared without case: file systems may fold it.
     */
    private fun isBookkeepingName(id: String): Boolean =
        id.contains(NativesExtractor.STAGING, ignoreCase = true) ||
            id.contains(NativesExtractor.BACKUP, ignoreCase = true)

    private fun safeRelativePath(path: String): String {
        val segments = path.split('/')
        val unsafe = path.isEmpty() ||
            path.startsWith("/") ||
            path.any { it == '\\' || it == ':' || it.isISOControl() } ||
            segments.any { it.isEmpty() || it == "." || it == ".." }
        if (unsafe) throw DownloadException("unsafe library path: ${printable(path)}")
        return path
    }

    private fun within(base: Path, relative: String): Path {
        val resolved = base.resolve(relative).normalize()
        if (!resolved.startsWith(base) || resolved == base) {
            throw DownloadException("library path escapes the cache: ${printable(relative)}")
        }
        return resolved
    }

    private fun printable(value: String): String =
        value.take(MAX_SHOWN).map { if (it.isISOControl()) '?' else it }.joinToString("")

    companion object {
        /**
         * Letters, digits and `._+-`, with single spaces inside (`1.14 Pre-Release 5`, `3D Shareware v1.34`). Both
         * ends are a letter or digit: Windows drops a trailing dot or space from a name, which would fold two ids
         * into one directory.
         */
        private val ID = Regex("[A-Za-z0-9](?:[A-Za-z0-9._+-]| (?! ))*(?<=[A-Za-z0-9])")
        private const val MAX_ID_LENGTH = 64
        private val HASH = Regex("[0-9a-f]{40}")
        private const val MAX_SHOWN = 120
        private const val APP = "blockwright"

        /**
         * `%LOCALAPPDATA%\blockwright\cache` on Windows, `~/Library/Caches/blockwright` on macOS, else
         * `$XDG_CACHE_HOME/blockwright` or `~/.cache/blockwright`.
         */
        fun defaultRoot(
            env: Map<String, String> = System.getenv(),
            osName: String = System.getProperty("os.name"),
            home: Path = Path.of(System.getProperty("user.home")),
        ): Path {
            val os = osName.lowercase()
            return when {
                os.startsWith("windows") ->
                    env["LOCALAPPDATA"]?.takeIf { it.isNotBlank() }
                        ?.let { Path.of(it, APP, "cache") }
                        ?: home.resolve("AppData").resolve("Local").resolve(APP).resolve("cache")
                os.startsWith("mac") -> home.resolve("Library").resolve("Caches").resolve(APP)
                else ->
                    env["XDG_CACHE_HOME"]?.takeIf { it.isNotBlank() && Path.of(it).isAbsolute }
                        ?.let { Path.of(it, APP) }
                        ?: home.resolve(".cache").resolve(APP)
            }
        }
    }
}
