package dev.blockwright.serverrunner

import java.nio.file.Files
import java.nio.file.Path

/** Writes `server.properties` for an offline, localhost-only test server. */
object ServerProperties {
    /**
     * Keys the runner owns: a test server never runs online, never binds beyond localhost (game port and the
     * management server alike), never opens RCON.
     */
    val PROTECTED_KEYS = setOf("online-mode", "server-ip", "server-port", "enable-rcon", "management-server-host")

    private val KEY = Regex("[a-z0-9][a-z0-9._-]{0,63}")

    fun requireValidOverrides(overrides: Map<String, String>) {
        overrides.forEach { (key, value) ->
            require(KEY.matches(key)) { "invalid server.properties key \"${key.take(64)}\"" }
            require(key !in PROTECTED_KEYS) { "server.properties key \"$key\" is controlled by the runner and cannot be overridden" }
            require(value.none { it == '\n' || it == '\r' || it == '\u0000' }) { "server.properties value of \"$key\" contains a line break" }
        }
    }

    fun render(port: Int, overrides: Map<String, String> = emptyMap()): String {
        requireValidOverrides(overrides)
        val values = linkedMapOf(
            "motd" to "Blockwright test server",
            "enable-query" to "false",
            "enforce-secure-profile" to "false",
        )
        values.putAll(overrides)
        values["online-mode"] = "false"
        values["server-ip"] = "127.0.0.1"
        values["server-port"] = port.toString()
        values["enable-rcon"] = "false"
        values["management-server-host"] = "127.0.0.1"
        return values.entries.joinToString("") { (key, value) -> "$key=${escape(value)}\n" }
    }

    fun write(runDir: Path, port: Int, overrides: Map<String, String> = emptyMap()): Path =
        Files.writeString(runDir.resolve("server.properties"), render(port, overrides))

    // java.util.Properties treats a backslash as an escape (a trailing one joins the next line).
    private fun escape(value: String): String = value.replace("\\", "\\\\")
}
