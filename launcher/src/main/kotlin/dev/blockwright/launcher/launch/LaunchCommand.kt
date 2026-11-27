package dev.blockwright.launcher.launch

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/**
 * The client command line: `java @<argfile>`, with every JVM option, the main class and the game arguments in the
 * argument file. The classpath alone exceeds the Windows command line limit. Each argument is double-quoted with
 * backslashes and quotes escaped (the JDK argument file syntax), so spaces and quotes in paths survive.
 */
object LaunchCommand {
    fun write(java: Path, argFile: Path, arguments: List<String>): List<String> {
        // The java launcher reads argument files in the platform's native encoding (an ANSI code page on Windows).
        Files.writeString(argFile, arguments.joinToString("\n", postfix = "\n") { quote(it) }, nativeCharset())
        return listOf(java.toString(), "@$argFile")
    }

    private fun nativeCharset(): Charset =
        System.getProperty("native.encoding")?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charset.defaultCharset()

    fun quote(argument: String): String {
        require(argument.none { it == '\n' || it == '\r' || it == '\u0000' }) { "a launch argument must be a single line" }
        return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
