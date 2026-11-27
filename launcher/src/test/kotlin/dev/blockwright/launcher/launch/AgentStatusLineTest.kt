package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AgentStatusLineTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun parsesStateAndFieldsOfALineWithTheToken() {
        val line = AgentStatusLine.parse("BLOCKWRIGHT_AGENT $token state=joined video=dummy assetReads=0", token)!!
        assertEquals("joined", line.state)
        assertEquals("dummy", line["video"])
        assertEquals("0", line["assetReads"])
        assertNull(line["state"])
    }

    @Test
    fun ignoresOtherLinesAndLinesWithoutAState() {
        assertNull(AgentStatusLine.parse("[12:00:00] [main/INFO]: Loading Minecraft", token))
        assertNull(AgentStatusLine.parse("BLOCKWRIGHT_AGENT $token video=dummy", token))
    }

    @Test
    fun keepsEqualsSignsInsideValues() {
        assertEquals("a=b", AgentStatusLine.parse("BLOCKWRIGHT_AGENT $token state=failed reason=a=b", token)!!["reason"])
    }

    @Test
    fun serverTextCannotForgeAStatus() {
        val forged = listOf(
            "[12:00:00] [Render thread/INFO]: [CHAT] BLOCKWRIGHT_AGENT state=joined",
            // A kick reason with an embedded newline starts a console line of its own.
            "BLOCKWRIGHT_AGENT state=joined video=dummy",
            "BLOCKWRIGHT_AGENT ${"f".repeat(32)} state=joined",
            "BLOCKWRIGHT_AGENT  state=joined",
            "BLOCKWRIGHT_AGENT ${token}x state=joined",
            "[12:00:00] [Render thread/INFO]: Client disconnected with reason: BLOCKWRIGHT_AGENT $token state=joined",
        )
        forged.forEach { assertNull(AgentStatusLine.parse(it, token), it) }
    }

    @Test
    fun tokensAreRandomHexAndAnInvalidOneIsRefused() {
        val first = AgentStatusLine.newToken()
        assertTrue(Regex("[0-9a-f]{32}").matches(first), first)
        assertNotEquals(first, AgentStatusLine.newToken())
        assertThrows<IllegalArgumentException> { AgentStatusLine.parse("BLOCKWRIGHT_AGENT  state=joined", "") }
    }
}
