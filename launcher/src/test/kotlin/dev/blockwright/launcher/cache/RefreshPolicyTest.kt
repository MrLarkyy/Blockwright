package dev.blockwright.launcher.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class RefreshPolicyTest {
    @Test
    fun neverIsNeverDue() {
        assertFalse(RefreshPolicy.NEVER.isDue(Duration.ofDays(36500)))
    }

    @Test
    fun alwaysIsAlwaysDue() {
        assertTrue(RefreshPolicy.ALWAYS.isDue(Duration.ZERO))
    }

    @Test
    fun afterIsDueFromItsMaxAgeOn() {
        val policy = RefreshPolicy.after(Duration.ofHours(1))
        assertFalse(policy.isDue(Duration.ofMinutes(59)))
        assertTrue(policy.isDue(Duration.ofHours(1)))
        assertTrue(policy.isDue(Duration.ofHours(2)))
    }

    @Test
    fun rejectsANegativeMaxAge() {
        assertThrows<IllegalArgumentException> { RefreshPolicy.after(Duration.ofSeconds(-1)) }
    }

    @Test
    fun defaultsToTwentyFourHours() {
        assertEquals(Duration.ofHours(24), RefreshPolicy.DEFAULT.maxAge)
    }
}
