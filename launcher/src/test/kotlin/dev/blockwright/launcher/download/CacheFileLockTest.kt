package dev.blockwright.launcher.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CacheFileLockTest {
    @TempDir
    lateinit var dir: Path

    private val lockFile by lazy { dir.resolve("locks/a.lock") }

    @Test
    fun anotherProcessCannotTakeTheLockWhileItIsHeld() {
        val whileHeld = CacheFileLock.withLock(lockFile) { probeFromAnotherProcess() }
        assertEquals("locked", whileHeld)
        assertEquals("free", probeFromAnotherProcess())
    }

    @Test
    fun anotherChannelInThisJvmCannotTakeTheLockWhileItIsHeld() {
        val acquired = CacheFileLock.withLock(lockFile) {
            FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
                try {
                    channel.tryLock()?.also { it.release() } != null
                } catch (_: OverlappingFileLockException) {
                    false
                }
            }
        }
        assertTrue(!acquired)
    }

    @Test
    fun threadsHoldTheLockOneAtATime() {
        val inside = AtomicInteger()
        val maxInside = AtomicInteger()
        val start = CountDownLatch(1)
        val workers = (1..4).map {
            thread {
                start.await()
                repeat(20) {
                    CacheFileLock.withLock(lockFile) {
                        maxInside.accumulateAndGet(inside.incrementAndGet(), ::maxOf)
                        Thread.sleep(1)
                        inside.decrementAndGet()
                    }
                }
            }
        }
        start.countDown()
        workers.forEach { it.join(TimeUnit.SECONDS.toMillis(30)) }
        assertEquals(1, maxInside.get())
    }

    private fun probeFromAnotherProcess(): String {
        val java = ProcessHandle.current().info().command().orElseThrow()
        val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), LockProbe::class.java.name, lockFile.toString())
            .redirectErrorStream(true)
            .start()
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "lock probe did not finish")
            return process.inputStream.bufferedReader().readText().trim()
        } finally {
            process.destroyForcibly()
        }
    }
}
