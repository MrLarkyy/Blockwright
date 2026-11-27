package dev.blockwright.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class AgentStatusTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun formatsTokenStateAndFieldsOnOneLine() {
        assertEquals(
            "BLOCKWRIGHT_AGENT $token state=joined video=dummy assetReads=0",
            AgentStatus.format("joined", mapOf("video" to "dummy", "assetReads" to 0), token),
        )
    }

    @Test
    fun withoutATokenTheLineCarriesAPlaceholder() {
        assertEquals("BLOCKWRIGHT_AGENT _ state=starting", AgentStatus.format("starting"))
    }

    @Test
    fun aServerProvidedReasonCannotForgeFieldsOrLines() {
        val line = AgentStatus.format("failed", mapOf("reason" to "You are\nnot state=joined\tok"), token)
        assertEquals("BLOCKWRIGHT_AGENT $token state=failed reason=You_are_not_state_joined_ok", line)
    }

    @Test
    fun valuesAreCappedAndNeverEmpty() {
        val line = AgentStatus.format("failed", mapOf("reason" to "x".repeat(500), "empty" to ""), token)
        assertTrue(line.contains("reason=" + "x".repeat(200) + " "), line)
        assertTrue(line.endsWith("empty=_"), line)
    }

    @Test
    fun emitWritesAndFlushesTheGivenStreamWithTheToken() {
        val bytes = ByteArrayOutputStream()
        AgentStatus(PrintStream(bytes, false, Charsets.UTF_8), token).emit("stopping", mapOf("reason" to "stdin-closed"))
        assertEquals("BLOCKWRIGHT_AGENT $token state=stopping reason=stdin-closed" + System.lineSeparator(), bytes.toString(Charsets.UTF_8))
    }
}
