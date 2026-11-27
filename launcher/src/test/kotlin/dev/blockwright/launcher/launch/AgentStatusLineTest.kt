package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AgentStatusLineTest {
    @Test
    fun parsesStateAndFieldsAnywhereInTheLine() {
        val line = AgentStatusLine.parse("[12:00:00] [Render thread/INFO]: [STDOUT]: BLOCKWRIGHT_AGENT state=joined video=dummy assetReads=0")!!
        assertEquals("joined", line.state)
        assertEquals("dummy", line["video"])
        assertEquals("0", line["assetReads"])
        assertNull(line["state"])
    }

    @Test
    fun ignoresOtherLinesAndLinesWithoutAState() {
        assertNull(AgentStatusLine.parse("[12:00:00] [main/INFO]: Loading Minecraft"))
        assertNull(AgentStatusLine.parse("BLOCKWRIGHT_AGENT video=dummy"))
    }

    @Test
    fun keepsEqualsSignsInsideValues() {
        assertEquals("a=b", AgentStatusLine.parse("BLOCKWRIGHT_AGENT state=failed reason=a=b")!!["reason"])
    }
}
