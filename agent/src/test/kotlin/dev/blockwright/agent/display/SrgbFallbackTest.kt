package dev.blockwright.agent.display

import dev.blockwright.agent.AgentStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class SrgbFallbackTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val bytes = ByteArrayOutputStream()
    private val status = AgentStatus(PrintStream(bytes, true, Charsets.UTF_8), token)
    private val attributes = mutableListOf<Pair<Int, Int>>()
    private val created = mutableListOf<Long>()

    private fun fallback(window: Long, status: AgentStatus? = this.status) = SrgbFallback(
        srgbAttribute = SRGB,
        openGlWindowFlag = OPENGL,
        setAttribute = { attribute, value -> attributes += attribute to value; true },
        createWindow = { _, _, _, flags -> created += flags; window },
        status = { status },
    )

    private fun lines() = bytes.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }

    @Test
    fun retriesOnceWithoutSrgbAsAnOpenGlWindowAndReportsItOnce() {
        val fallback = fallback(window = 42L)

        assertEquals(42L, fallback.retryWithoutSrgb("Minecraft", 320, 480, HIDDEN))
        assertEquals(42L, fallback.retryWithoutSrgb("Minecraft", 854, 480, 0L))

        assertEquals(listOf(SRGB to 0, SRGB to 0), attributes)
        assertEquals(listOf(HIDDEN or OPENGL, OPENGL), created)
        val alert = lines().single()
        assertTrue(alert.startsWith("BLOCKWRIGHT_AGENT $token state=alert reason=no_sRGB-capable_OpenGL_framebuffer"), alert)
    }

    @Test
    fun aWindowThatStillFailsKeepsMinecraftsFailureSilently() {
        assertEquals(SrgbFallback.NO_WINDOW, fallback(window = 0L).retryWithoutSrgb("Minecraft", 320, 480, 0L))
        assertEquals(emptyList<String>(), lines())
    }

    @Test
    fun aClientTheLauncherDidNotStartIsLeftAlone() {
        assertEquals(SrgbFallback.NO_WINDOW, fallback(window = 42L, status = null).retryWithoutSrgb("Minecraft", 320, 480, 0L))
        assertEquals(emptyList<Pair<Int, Int>>(), attributes)
        assertEquals(emptyList<Long>(), created)
    }

    private companion object {
        const val SRGB = 22
        const val OPENGL = 0x2L
        const val HIDDEN = 0x8L
    }
}
