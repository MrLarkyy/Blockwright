package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class OfflineUuidTest {
    @Test
    fun matchesTheUuidAnOfflineServerAssigns() {
        // Paper logged "UUID of player bwlogic is b926af23-4eb8-3065-a6f4-a39fc4697dab" for this name.
        assertEquals(UUID.fromString("b926af23-4eb8-3065-a6f4-a39fc4697dab"), OfflineUuid.of("bwlogic"))
        assertEquals("b926af234eb83065a6f4a39fc4697dab", OfflineUuid.undashed(OfflineUuid.of("bwlogic")))
    }

    @Test
    fun acceptsMinecraftNamesOnly() {
        for (name in listOf("abc", "Player_16_chars_", "bw1")) OfflineUuid.validate(name)
        for (name in listOf("ab", "seventeen_chars__", "with space", "dash-ed", "quote\"", "new\nline", "", "ünï")) {
            assertThrows<LaunchException>(name) { OfflineUuid.validate(name) }
        }
    }
}
