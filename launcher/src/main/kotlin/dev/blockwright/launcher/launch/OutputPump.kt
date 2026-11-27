package dev.blockwright.launcher.launch

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
 * [logFile] and hands it to the listeners; keeps the last [tailLines] lines for error messages.
 */
class OutputPump(
    private val input: InputStream,
    private val logFile: Path,
    private val tailLines: Int = 50,
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
                    val line = reader.readLine() ?: break
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
}
