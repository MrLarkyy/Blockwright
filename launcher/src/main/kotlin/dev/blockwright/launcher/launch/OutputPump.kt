package dev.blockwright.launcher.launch

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration

/**
 * Drains the client's merged stdout and stderr (a full pipe would block the client), appends every line to
 * [logFile] and hands it to the listeners; keeps the last [tailLines] lines for error messages. A line longer than
 * [maxLineChars] is cut there (the rest is skipped, not buffered), so server text in the console cannot grow the
 * launcher's heap: memory stays within [tailLines] times [maxLineChars].
 */
class OutputPump(
    private val input: InputStream,
    private val logFile: Path,
    private val tailLines: Int = 50,
    private val maxLineChars: Int = 8192,
) {
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val tail = ArrayDeque<String>()
    private val thread = Thread(::pump, "blockwright-client-console").apply { isDaemon = true }

    fun addListener(listener: (String) -> Unit) {
        listeners += listener
    }

    fun start() {
        Files.createDirectories(logFile.toAbsolutePath().parent)
        thread.start()
    }

    fun tail(): List<String> = synchronized(tail) { tail.toList() }

    /** Waits for the output to reach end of stream; true when it did. */
    fun join(timeout: Duration): Boolean {
        thread.join(timeout.inWholeMilliseconds.coerceAtLeast(1))
        return !thread.isAlive
    }

    private fun pump() {
        // A log that cannot be written must not stop the draining.
        var log = runCatching { Files.newBufferedWriter(logFile) }.getOrNull()
        try {
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = readLine(reader) ?: break
                    log = log?.takeIf { append(it, line) }
                    remember(line)
                    listeners.forEach { listener -> runCatching { listener(line) } }
                }
            }
        } catch (_: IOException) {
            // The stream closes when the process is killed; whatever was read is already in the log.
        } finally {
            runCatching { log?.close() }
        }
    }

    /** Line ends as in [BufferedReader.readLine] (LF, CR or CR LF), but at most [maxLineChars] are kept. */
    private fun readLine(reader: BufferedReader): String? {
        val line = StringBuilder()
        var skipped = 0L
        var read = false
        while (true) {
            val c = reader.read()
            if (c < 0) return if (read) cut(line, skipped) else null
            read = true
            when {
                c == LF -> return cut(line, skipped)
                c == CR -> {
                    reader.mark(1)
                    if (reader.read() != LF) reader.reset()
                    return cut(line, skipped)
                }
                line.length < maxLineChars -> line.append(c.toChar())
                else -> skipped++
            }
        }
    }

    private fun cut(line: StringBuilder, skipped: Long): String =
        if (skipped == 0L) line.toString() else line.append(" [").append(skipped).append(" more chars cut]").toString()

    private fun append(log: BufferedWriter, line: String): Boolean = try {
        log.write(line)
        log.newLine()
        log.flush()
        true
    } catch (_: IOException) {
        runCatching { log.close() }
        false
    }

    private fun remember(line: String) = synchronized(tail) {
        tail.addLast(line)
        while (tail.size > tailLines) tail.removeFirst()
    }

    private companion object {
        const val LF = '\n'.code
        const val CR = '\r'.code
    }
}
