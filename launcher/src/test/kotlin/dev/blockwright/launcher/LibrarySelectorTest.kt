package dev.blockwright.launcher

import dev.blockwright.launcher.download.DownloadException
import dev.blockwright.launcher.manifest.Artifact
import dev.blockwright.launcher.manifest.ExtractRules
import dev.blockwright.launcher.manifest.Library
import dev.blockwright.launcher.manifest.LibraryDownloads
import dev.blockwright.launcher.manifest.OsRule
import dev.blockwright.launcher.manifest.Rule
import dev.blockwright.launcher.manifest.RuleAction.ALLOW
import dev.blockwright.launcher.platform.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LibrarySelectorTest {
    private val linux = LibrarySelector(Platform("linux", "x86_64", "6.8"))
    private val windows = LibrarySelector(Platform("windows", "x86_64", "10.0"))
    private val windows32 = LibrarySelector(Platform("windows", "x86", "10.0"))

    private fun artifact(path: String) = Artifact(path, "0".repeat(40), 1, "https://libraries.minecraft.net/$path")

    private fun plain(path: String) = Library(path, LibraryDownloads(artifact = artifact(path)))

    private fun osOnly(path: String, os: String) =
        Library(path, LibraryDownloads(artifact = artifact(path)), rules = listOf(Rule(ALLOW, OsRule(name = os))))

    private val legacyLwjgl = Library(
        name = "org.lwjgl.lwjgl:lwjgl-platform:2.9.4",
        downloads = LibraryDownloads(
            classifiers = mapOf(
                "natives-windows-32" to artifact("lwjgl-platform-natives-windows-32.jar"),
                "natives-windows-64" to artifact("lwjgl-platform-natives-windows-64.jar"),
                "natives-linux" to artifact("lwjgl-platform-natives-linux.jar"),
            ),
        ),
        natives = mapOf("windows" to "natives-windows-\${arch}", "linux" to "natives-linux"),
        extract = ExtractRules(listOf("META-INF/")),
    )

    @Test
    fun selectsByOperatingSystemKeepingOrder() {
        val libraries = listOf(
            plain("a.jar"),
            osOnly("lwjgl-natives-windows.jar", "windows"),
            osOnly("lwjgl-natives-linux.jar", "linux"),
            osOnly("objc-bridge.jar", "osx"),
            plain("b.jar"),
        )
        assertEquals(listOf("a.jar", "lwjgl-natives-linux.jar", "b.jar"), linux.select(libraries).classpath.map { it.path })
        assertEquals(listOf("a.jar", "lwjgl-natives-windows.jar", "b.jar"), windows.select(libraries).classpath.map { it.path })
    }

    @Test
    fun modernNativesStayOnTheClasspathAndAreNotExtracted() {
        val selection = linux.select(listOf(osOnly("lwjgl-3.3.3-natives-linux.jar", "linux")))
        assertEquals(listOf("lwjgl-3.3.3-natives-linux.jar"), selection.classpath.map { it.path })
        assertEquals(emptyList<NativeArtifact>(), selection.natives)
    }

    @Test
    fun legacyNativesSubstituteTheArchitectureAndCarryExcludes() {
        val win64 = windows.select(listOf(legacyLwjgl)).natives.single()
        assertEquals("lwjgl-platform-natives-windows-64.jar", win64.artifact.path)
        assertEquals(listOf("META-INF/"), win64.excludes)
        assertEquals("lwjgl-platform-natives-windows-32.jar", windows32.select(listOf(legacyLwjgl)).natives.single().artifact.path)
        assertEquals("lwjgl-platform-natives-linux.jar", linux.select(listOf(legacyLwjgl)).natives.single().artifact.path)
        assertEquals(emptyList<Artifact>(), linux.select(listOf(legacyLwjgl)).classpath)
    }

    @Test
    fun legacyNativesWithoutAClassifierForTheOsAreSkipped() {
        val mac = LibrarySelector(Platform("osx", "arm64", "15.0"))
        assertEquals(LibrarySelection(emptyList(), emptyList()), mac.select(listOf(legacyLwjgl)))
    }

    @Test
    fun duplicatePathsAreKeptOnceAtTheFirstPosition() {
        val selection = linux.select(listOf(plain("a.jar"), plain("b.jar"), plain("a.jar")))
        assertEquals(listOf("a.jar", "b.jar"), selection.classpath.map { it.path })
    }

    @Test
    fun libraryWithoutDownloadsIsSkipped() {
        assertEquals(LibrarySelection(emptyList(), emptyList()), linux.select(listOf(Library("local:only:1"))))
    }

    @Test
    fun artifactWithoutPathIsAnError() {
        val noPath = Library("x:y:1", LibraryDownloads(artifact = Artifact(null, "0".repeat(40), 1, "https://libraries.minecraft.net/x")))
        assertThrows<DownloadException> { linux.select(listOf(noPath)) }
    }
}
