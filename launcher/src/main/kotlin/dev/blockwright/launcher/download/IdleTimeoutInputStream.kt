package dev.blockwright.launcher.download

import java.io.FilterInputStream
import java.io.InputStream
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Fails a read that stays blocked without a byte for [idleTimeout]. `java.net.http` has no read timeout once headers have
 * arrived, so a watchdog closes the stream and interrupts the reader blocked inside [read]; the interrupt is only
 * delivered while the reader is in [read] and is cleared before the timeout surfaces.
 */
internal class IdleTimeoutInputStream(
    input: InputStream,
    private val idleTimeout: Duration,
) : FilterInputStream(input) {
    private val guard = Any()
    private var readStarted = System.nanoTime()
    private var reader: Thread? = null
    private var timedOut = false
    private val check: ScheduledFuture<*>

    init {
        val period = maxOf(idleTimeout.toMillis() / CHECKS_PER_TIMEOUT, MIN_PERIOD_MS)
        check = WATCHDOG.scheduleAtFixedRate(::expireIfIdle, period, period, TimeUnit.MILLISECONDS)
    }

    override fun read(): Int = guarded { super.read() }

    override fun read(b: ByteArray, off: Int, len: Int): Int = guarded { super.read(b, off, len) }

    override fun close() {
        check.cancel(false)
        super.close()
    }

    private fun <T> guarded(block: () -> T): T {
        synchronized(guard) {
            if (timedOut) throw timeout()
            reader = Thread.currentThread()
            readStarted = System.nanoTime()
        }
        try {
            val result = block()
            synchronized(guard) { if (timedOut) throw timeout() }
            return result
        } catch (e: HttpTimeoutException) {
            throw e
        } catch (e: Exception) {
            synchronized(guard) { if (timedOut) throw timeout().apply { initCause(e) } }
            throw e
        } finally {
            synchronized(guard) {
                reader = null
                if (timedOut) Thread.interrupted()
            }
        }
    }

    private fun expireIfIdle() {
        synchronized(guard) {
            val blocked = reader ?: return
            if (timedOut || System.nanoTime() - readStarted < idleTimeout.toNanos()) return
            timedOut = true
            blocked.interrupt()
        }
        check.cancel(false)
        runCatching { `in`.close() }
    }

    private fun timeout() = HttpTimeoutException("no data received for $idleTimeout")

    private companion object {
        const val CHECKS_PER_TIMEOUT = 4
        const val MIN_PERIOD_MS = 10L

        val WATCHDOG: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "blockwright-download-watchdog").apply { isDaemon = true }
        }
    }
}
