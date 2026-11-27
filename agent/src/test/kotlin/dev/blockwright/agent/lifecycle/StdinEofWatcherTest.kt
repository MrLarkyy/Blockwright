package dev.blockwright.agent.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StdinEofWatcherTest {
    @Test
    fun firesOnceWhenTheWriterClosesAndNotBefore() {
        val writer = PipedOutputStream()
        val reader = PipedInputStream(writer)
        val fired = AtomicInteger()
        val done = CountDownLatch(1)
        StdinEofWatcher(reader) { fired.incrementAndGet(); done.countDown() }.start()

        writer.write("anything\n".toByteArray())
        writer.flush()
        assertFalse(done.await(200, TimeUnit.MILLISECONDS), "fired while stdin was open")

        writer.close()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, fired.get())
    }

    @Test
    fun aBrokenPipeCountsAsEof() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("pipe broken")
        }
        val done = CountDownLatch(1)
        StdinEofWatcher(broken) { done.countDown() }.start()
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }
}
