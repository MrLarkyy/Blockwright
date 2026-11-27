package dev.blockwright.launcher.launch

import java.util.UUID

/**
 * The identity an offline-mode server gives a player: the name-based UUID of `OfflinePlayer:<name>`, the same one
 * the server computes. Names are restricted to what Minecraft accepts (3-16 letters, digits, underscore), which
 * also keeps them safe inside a command line.
 */
object OfflineUuid {
    private val NAME = Regex("[A-Za-z0-9_]{3,16}")

    fun of(username: String): UUID {
        validate(username)
        return UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
    }

    fun validate(username: String) {
        if (!NAME.matches(username)) {
            val shown = username.take(MAX_SHOWN).map { if (it.isISOControl()) '?' else it }.joinToString("")
            throw LaunchException("invalid username '$shown': 3-16 letters, digits or underscores")
        }
    }

    /** The undashed form launchers pass in `--uuid`. */
    fun undashed(uuid: UUID): String = uuid.toString().replace("-", "")

    private const val MAX_SHOWN = 40
}
