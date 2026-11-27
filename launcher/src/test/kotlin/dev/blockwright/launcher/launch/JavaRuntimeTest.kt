package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JavaRuntimeTest {
    @TempDir lateinit var dir: Path

    private fun runtime(release: String?): Path {
        val home = Files.createDirectories(dir.resolve("jdk"))
        Files.createDirectories(home.resolve("bin"))
        release?.let { Files.writeString(home.resolve("release"), it) }
        return JavaRuntime.executableIn(home)
    }

    @Test
    fun readsTheFeatureVersionFromTheReleaseFile() {
        assertEquals(25, JavaRuntime.of(runtime("IMPLEMENTOR=\"x\"\nJAVA_VERSION=\"25.0.1\"\n")).featureVersion)
        assertEquals(21, JavaRuntime.of(runtime("JAVA_VERSION=\"21\"\n")).featureVersion)
    }

    @Test
    fun refusesAnOlderJvmBeforeAnythingStarts() {
        val old = JavaRuntime.of(runtime("JAVA_VERSION=\"21.0.8\"\n"))
        val error = assertThrows<LaunchException> { old.requireAtLeast(25) }
        assertTrue("needs Java 25" in error.message!!, error.message)
        old.requireAtLeast(21)
        old.requireAtLeast(null)
    }

    @Test
    fun refusesARuntimeWithoutAVersion() {
        assertThrows<LaunchException> { JavaRuntime.of(runtime(null)) }
        assertThrows<LaunchException> { JavaRuntime.of(runtime("IMPLEMENTOR=\"x\"\n")) }
    }

    @Test
    fun theCurrentRuntimeIsTheProjectToolchain() {
        val current = JavaRuntime.current()
        assertEquals(Runtime.version().feature(), current.featureVersion)
        assertTrue(Files.isRegularFile(current.executable))
    }
}
