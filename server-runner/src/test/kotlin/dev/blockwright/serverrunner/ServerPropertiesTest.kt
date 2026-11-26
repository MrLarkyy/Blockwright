package dev.blockwright.serverrunner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class ServerPropertiesTest {
    private fun load(text: String) = Properties().apply { load(StringReader(text)) }

    @Test
    fun offlineLocalhostDefaultsArePresent() {
        val properties = load(ServerProperties.render(25601))

        assertEquals("false", properties.getProperty("online-mode"))
        assertEquals("127.0.0.1", properties.getProperty("server-ip"))
        assertEquals("25601", properties.getProperty("server-port"))
        assertEquals("false", properties.getProperty("enable-rcon"))
        assertEquals("false", properties.getProperty("enable-query"))
        assertEquals("false", properties.getProperty("enforce-secure-profile"))
        assertEquals("127.0.0.1", properties.getProperty("management-server-host"))
    }

    @Test
    fun protectedKeysCannotBeOverridden() {
        listOf("online-mode", "server-ip", "server-port", "enable-rcon", "management-server-host").forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { ServerProperties.render(25601, mapOf(key to "true")) }
        }
    }

    @Test
    fun extraOverridesAreKept() {
        val properties = load(ServerProperties.render(25601, mapOf("level-type" to "minecraft:flat", "motd" to "plugin test")))

        assertEquals("minecraft:flat", properties.getProperty("level-type"))
        assertEquals("plugin test", properties.getProperty("motd"))
    }

    @Test
    fun valuesCannotSmuggleAProtectedKey() {
        assertThrows(IllegalArgumentException::class.java) { ServerProperties.render(25601, mapOf("motd" to "x\nonline-mode=true")) }
        assertThrows(IllegalArgumentException::class.java) { ServerProperties.render(25601, mapOf("online-mode\nmotd" to "x")) }

        // A trailing backslash would join the next line in java.util.Properties if it were not escaped.
        val properties = load(ServerProperties.render(25601, mapOf("motd" to "x\\")))

        assertEquals("x\\", properties.getProperty("motd"))
        assertEquals("false", properties.getProperty("online-mode"))
    }

    @Test
    fun writesServerPropertiesIntoTheRunDir(@TempDir runDir: Path) {
        ServerProperties.write(runDir, 25601)

        assertEquals("false", load(Files.readString(runDir.resolve("server.properties"))).getProperty("online-mode"))
    }
}
