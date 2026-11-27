package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

class OutputPumpTest {
    @TempDir lateinit var dir: Path

    private fun pump(text: String, tailLines: Int = 50, maxLineChars: Int = 16): Pair<OutputPump, List<String>> {
        val seen = CopyOnWriteArrayList<String>()
        val pump = OutputPump(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), dir.resolve("console.log"), tailLines, maxLineChars)
        pump.addListener { seen += it }
        pump.start()
        assertTrue(pump.join(10.seconds))
        return pump to seen
    }

    @Test
    fun splitsLinesLikeReadLine() {
        val (pump, seen) = pump("a\nb\r\nc\rd")
        assertEquals(listOf("a", "b", "c", "d"), seen)
        assertEquals(listOf("a", "b", "c", "d"), pump.tail())
        assertEquals(listOf("a", "b", "c", "d"), Files.readAllLines(dir.resolve("console.log")))
    }

    @Test
    fun aHugeLineIsCutAndTheNextLineSurvives() {
        val (pump, seen) = pump("x".repeat(1_000_000) + "\nnext line\n")
        val cut = "x".repeat(16) + " [" + (1_000_000 - 16) + " more chars cut]"
        assertEquals(listOf(cut, "next line"), seen)
        assertEquals(listOf(cut, "next line"), Files.readAllLines(dir.resolve("console.log")))
        assertEquals(listOf(cut, "next line"), pump.tail())
    }

    @Test
    fun theTailKeepsOnlyTheLastLinesAndEachIsBounded() {
        val (pump, _) = pump((1..10).joinToString("\n") { "line $it " + "y".repeat(100) }, tailLines = 3)
        val tail = pump.tail()
        assertEquals(3, tail.size)
        assertTrue(tail.first().startsWith("line 8 "), tail.first())
        tail.forEach { assertTrue(it.length < 16 + 40, it) }
    }
}
