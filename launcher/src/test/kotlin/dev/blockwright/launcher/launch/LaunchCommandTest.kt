package dev.blockwright.launcher.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class LaunchCommandTest {
    @TempDir lateinit var dir: Path

    @Test
    fun argumentsSurviveARealJavaArgfileRoundTrip() {
        val tricky = listOf(
            "C:\\Program Files\\Blockwright\\cache\\a b.jar",
            "path with \"quotes\" and 'single'",
            "trailing backslash\\",
            "#not-a-comment",
            "",
            "C:\\Users\\Tomáš\\cache",
        )
        val arguments = listOf("-cp", System.getProperty("java.class.path"), PrintArgsMain::class.java.name) + tricky
        val command = LaunchCommand.write(JavaRuntime.current().executable, dir.resolve("client.args"), arguments)
        assertEquals(2, command.size)

        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        // A redirected child writes stdout in the native encoding, like the argument file is read.
        val output = process.inputStream.readAllBytes().toString(java.nio.charset.Charset.forName(System.getProperty("native.encoding")))
        process.waitFor(30, TimeUnit.SECONDS)

        assertEquals(0, process.exitValue(), output)
        assertEquals(tricky.map { "<$it>" }, output.lines().filter { it.startsWith("<") })
    }

    @Test
    fun aMultiLineArgumentIsRefused() {
        assertThrows<IllegalArgumentException> { LaunchCommand.quote("a\nb") }
        assertThrows<IllegalArgumentException> { LaunchCommand.quote("a\rb") }
    }
}
