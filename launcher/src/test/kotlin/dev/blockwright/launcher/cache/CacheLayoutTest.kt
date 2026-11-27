package dev.blockwright.launcher.cache

import dev.blockwright.launcher.download.DownloadException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class CacheLayoutTest {
    @TempDir
    lateinit var root: Path

    private val layout by lazy { CacheLayout(root) }

    @Test
    fun acceptsAMavenPathInsideTheLibrariesDirectory() {
        val path = layout.library("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar")
        assertEquals(root.resolve("libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar").normalize(), path)
    }

    @ParameterizedTest
    @ValueSource(strings = ["../x.jar", "a/../../x.jar", "/abs.jar", "C:/x.jar", "a\\..\\x.jar", "//server/share/x.jar", "a//b.jar", "./a.jar", "", "a/\u0000.jar"])
    fun rejectsLibraryPathsThatCouldEscape(path: String) {
        assertThrows<DownloadException> { layout.library(path) }
        assertEquals(0L, Files.list(root).use { it.count() })
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "../evil", "a/b", "a\\b", ".hidden", "", "C:", "a12345678901234567890123456789012345678901234567890123456789012345",
            "1.21  4", " 1.21", "1.21 ", "1.21.", "1.21-", "1.21\t4", "1.21 4", "1 /../x", "1.14 Pre-Release 5/..", "1 .. 2/x",
        ],
    )
    fun rejectsBadVersionIds(id: String) {
        assertThrows<DownloadException> { layout.versionJson(id) }
        assertThrows<DownloadException> { layout.nativesDir(id) }
        assertThrows<DownloadException> { layout.assetIndex(id) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1.8.9.tmp-0b0e5a3c", "1.8.tmp-", "1.8.Tmp-x.1", "1.8.9.old-crashed", "a.OLD-1"])
    fun rejectsVersionIdsNamedLikeANativesStagingOrBackupDirectory(id: String) {
        assertThrows<DownloadException> { layout.nativesDir(id) }
        assertThrows<DownloadException> { layout.versionJson(id) }
        assertThrows<DownloadException> { layout.clientJar(id) }
        assertThrows<DownloadException> { layout.assetIndex(id) }
    }

    @Test
    fun lockFilesLiveInTheLocksDirectoryNamedByTheEntryPath() {
        val jar = layout.library("a/b/c-1.0.jar")
        val lock = layout.lockFile(jar)
        assertEquals(root.resolve("locks"), lock.parent)
        assertTrue(Regex("[0-9a-f]{64}\\.lock").matches(lock.fileName.toString()), lock.toString())
        assertEquals(lock, layout.lockFile(jar))
        assertEquals(lock, layout.lockFile(layout.library("A/B/C-1.0.JAR")))
        assertNotEquals(lock, layout.lockFile(layout.library("a/b/c-1.1.jar")))
    }

    @Test
    fun refusesALockForAnEntryOutsideTheCache() {
        assertThrows<DownloadException> { layout.lockFile(root.resolveSibling("elsewhere.jar")) }
        assertThrows<DownloadException> { layout.lockFile(root) }
    }

    @Test
    fun acceptsVersionIdsThatOnlyResembleThoseNames() {
        for (id in listOf("lock", "1.8.lock", "1.8-lock", "1.8.locked", "1.8.tmp", "tmp-1", "1.8.old", "old-1", "1.8.0ld-1")) {
            assertEquals(root.resolve("natives/$id").normalize(), layout.nativesDir(id))
        }
    }

    @Test
    fun acceptsRealVersionIds() {
        for (id in listOf("1.21.4", "26.3", "26.4-pre-1", "26.4-snapshot-2", "1.7.10", "b1.7.3", "rd-132211")) {
            assertEquals(root.resolve("versions/$id/$id.json").normalize(), layout.versionJson(id))
            assertEquals(root.resolve("versions/$id/$id.jar").normalize(), layout.clientJar(id))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1.14 Pre-Release 5", "1.14.2 Pre-Release 4", "3D Shareware v1.34", "a b", "1 2 3"])
    fun acceptsVersionIdsWithSingleSpacesAndKeepsThemInsideTheCache(id: String) {
        val json = layout.versionJson(id)
        assertEquals(root.resolve("versions").resolve(id).resolve("$id.json"), json)
        assertEquals(root.resolve("versions").resolve(id).resolve("$id.jar"), layout.clientJar(id))
        assertEquals(root.resolve("natives").resolve(id), layout.nativesDir(id))
        assertEquals(json.normalize(), json)
        assertTrue(json.startsWith(root))

        Files.createDirectories(json.parent)
        Files.writeString(json, "{}")

        // The name on disk is the id, unchanged: the file system folded or trimmed nothing.
        val onDisk = Files.list(root.resolve("versions")).use { entries -> entries.map { it.fileName.toString() }.toList() }
        assertEquals(listOf(id), onDisk)
    }

    @ParameterizedTest
    @ValueSource(strings = ["../../0123456789012345678901234567890123456789", "ABCDEF0123456789ABCDEF0123456789ABCDEF01", "abc", "0123456789abcdef0123456789abcdef012345678","0123456789abcdef0123456789abcdef0123456g"])
    fun rejectsBadAssetHashes(hash: String) {
        assertThrows<DownloadException> { layout.assetObject(hash) }
    }

    @Test
    fun assetObjectsAreShardedByTheirFirstTwoHexDigits() {
        val hash = "ab0123456789abcdef0123456789abcdef012345"
        assertEquals(root.resolve("assets/objects/ab/$hash").normalize(), layout.assetObject(hash))
    }

    @Test
    fun defaultRootFollowsPlatformConventions() {
        val home = Path.of("/home/u")
        assertEquals(Path.of("C:/Users/u/AppData/Local", "blockwright", "cache"), CacheLayout.defaultRoot(mapOf("LOCALAPPDATA" to "C:/Users/u/AppData/Local"), "Windows 11", home))
        assertEquals(home.resolve("Library/Caches/blockwright"), CacheLayout.defaultRoot(emptyMap(), "Mac OS X", home))
        val xdg = root.resolve("xdg")
        assertEquals(xdg.resolve("blockwright"), CacheLayout.defaultRoot(mapOf("XDG_CACHE_HOME" to xdg.toString()), "Linux", home))
        assertEquals(home.resolve(".cache/blockwright"), CacheLayout.defaultRoot(mapOf("XDG_CACHE_HOME" to "relative"), "Linux", home))
        assertEquals(home.resolve(".cache/blockwright"), CacheLayout.defaultRoot(emptyMap(), "Linux", home))
    }

    @Test
    fun defaultRootOnThisMachineIsAbsoluteAndOutsideTheWorkingDirectory() {
        val defaultRoot = CacheLayout.defaultRoot()
        assertTrue(defaultRoot.isAbsolute)
        assertTrue(!defaultRoot.startsWith(Path.of("").toAbsolutePath()))
    }
}
