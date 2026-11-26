package dev.blockwright.serverrunner

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ServerRunConfigTest {
    private fun config(
        version: String = "26.3",
        build: Int? = null,
        maxHeap: String = "1G",
        port: Int? = null,
        overrides: Map<String, String> = emptyMap(),
    ) = ServerRunConfig(version, build, cacheRoot = Path.of("cache"), artifactDir = Path.of("artefacts"), maxHeap = maxHeap, port = port, propertyOverrides = overrides)

    @Test
    fun eulaIsNotAcceptedByDefault() {
        assertFalse(config().acceptEula)
    }

    @Test
    fun invalidValuesAreRejected() {
        listOf<() -> Unit>(
            { config(version = "../26.3") },
            { config(version = "..") },
            { config(version = "x".repeat(33)) },
            { config(build = 0) },
            { config(maxHeap = "1G -Dcom.mojang.eula.agree=true") },
            { config(port = 70000) },
            { config(overrides = mapOf("online-mode" to "true")) },
        ).forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { invalid() } }
    }
}
