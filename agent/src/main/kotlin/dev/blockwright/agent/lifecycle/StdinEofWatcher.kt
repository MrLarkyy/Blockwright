package dev.blockwright.agent.lifecycle

import java.io.IOException
import java.io.InputStream

/**
 * Calls [onEof] once stdin ends: the launcher closes the pipe to stop the client, and the OS closes it when the
 * launcher dies without running any hook. Whatever arrives on stdin is discarded.
 */
class StdinEofWatcher(private val input: InputStream, private val onEof: () -> Unit) {
    fun start(): Thread = Thread.ofPlatform().daemon().name("blockwright-stdin-watcher").start(::watch)

    private fun watch() {
        val buffer = ByteArray(BUFFER)
        try {
            while (input.read(buffer) >= 0) {
                // Discard: stdin is only a liveness signal.
            }
        } catch (_: IOException) {
            // A broken pipe means the launcher is gone, the same as EOF.
        }
        onEof()
    }

    private companion object {
        const val BUFFER = 256
    }
}
