package dev.blockwright.serverrunner

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadinessDetectorTest {
    @Test
    fun plainReadyLineMatches() {
        assertTrue(ReadinessDetector.isReady("[12:00:00 INFO]: Done (7.123s)! For help, type \"help\""))
    }

    @Test
    fun ansiColouredReadyLineMatches() {
        assertTrue(ReadinessDetector.isReady("\u001B[32m[12:00:00 INFO]: \u001B[0mDone (\u001B[1m7.123s\u001B[0m)! For help, type \"help\"\u001B[m"))
    }

    @Test
    fun commaDecimalReadyLineMatches() {
        assertTrue(ReadinessDetector.isReady("[12:00:00 INFO]: Done (7,123s)! For help, type \"help\""))
    }

    @Test
    fun doneWithoutTheHelpSuffixDoesNotMatch() {
        assertFalse(ReadinessDetector.isReady("[12:00:00 INFO]: Done (7.123s)!"))
        assertFalse(ReadinessDetector.isReady("[12:00:00 INFO]: Done preparing level \"world\""))
    }
}
