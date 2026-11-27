package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LaunchProfileTest {
    @Test
    fun logicNeverNeedsADisplay() {
        assertFalse(LaunchProfile.LOGIC.needsDisplay)
        assertTrue(LaunchProfile.REFERENCE.needsDisplay)
        assertTrue("-Djava.awt.headless=true" in LaunchProfile.LOGIC.jvmFlags)
    }

    @Test
    fun logicRunsSmallAndBothProfilesShareTheHeap() {
        for (flag in listOf("-Xmx1G", "-XX:+UseSerialGC", "-XX:+UseCompactObjectHeaders", "-XX:-UsePerfData", "-Xss1m")) {
            assertTrue(flag in LaunchProfile.LOGIC.jvmFlags, flag)
            assertTrue(flag in LaunchProfile.REFERENCE.jvmFlags, flag)
        }
        assertEquals("logic", LaunchProfile.LOGIC.agentProfile)
        assertEquals("reference", LaunchProfile.REFERENCE.agentProfile)
    }

    @Test
    fun noProfileChangesTheJdkProcessReaper() {
        LaunchProfile.entries.forEach { profile -> assertFalse(profile.jvmFlags.any { "processReaper" in it }, profile.name) }
    }
}
