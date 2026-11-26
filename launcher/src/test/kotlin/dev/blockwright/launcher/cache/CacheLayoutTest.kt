package dev.blockwright.launcher.cache

import dev.blockwright.launcher.download.DownloadException
import org.junit.jupiter.api.Assertions.assertEquals
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
    @ValueSource(strings = ["../evil", "a/b", "a\\b", ".hidden", "", "1.21 4", "C:", "a12345678901234567890123456789012345678901234567890123456789012345"])
    fun rejectsBadVersionIds(id: String) {
        assertThrows<DownloadException> { layout.versionJson(id) }
        assertThrows<DownloadException> { layout.nativesDir(id) }
        assertThrows<DownloadException> { layout.assetIndex(id) }
    }

    @Test
    fun acceptsRealVersionIds() {
        for (id in listOf("1.21.4", "26.3", "26.4-pre-1", "26.4-snapshot-2", "1.7.10", "b1.7.3", "rd-132211")) {
            assertEquals(root.resolve("versions/$id/$id.json").normalize(), layout.versionJson(id))
            assertEquals(root.resolve("versions/$id/$id.jar").normalize(), layout.clientJar(id))
        }
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
