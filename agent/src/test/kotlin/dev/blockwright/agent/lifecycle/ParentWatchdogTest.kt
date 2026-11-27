package dev.blockwright.agent.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ParentWatchdogTest {
    @Test
    fun firesOnItsOwnThreadWhenTheWatchedProcessExits() {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val parent = ProcessBuilder(java, "-version").redirectErrorStream(true).start()
        val fired = CountDownLatch(1)
        val firedOn = AtomicReference<String>()
        ParentWatchdog(parent.pid(), pollMillis = 20) {
            firedOn.set(Thread.currentThread().name)
            fired.countDown()
        }.start()
        parent.inputStream.readAllBytes()
        assertTrue(fired.await(10, TimeUnit.SECONDS))
        // Never the JDK's process reaper, whose small stack overflows on Fabric class loading.
        assertEquals(ParentWatchdog.THREAD_NAME, firedOn.get())
    }

    @Test
    fun doesNotFireWhileTheProcessLives() {
        val fired = CountDownLatch(1)
        // The test JVM's own parent (the Gradle worker host) outlives the test.
        ParentWatchdog(ProcessHandle.current().parent().get().pid()) { fired.countDown() }.start()
        assertFalse(fired.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun watchingItselfIsIgnored() {
        val fired = CountDownLatch(1)
        ParentWatchdog(ProcessHandle.current().pid()) { fired.countDown() }.start()
        assertFalse(fired.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun firesAtOnceForAProcessThatIsAlreadyGone() {
        val gone = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-version").start()
        gone.waitFor()
        var fired = 0
        ParentWatchdog(gone.pid()) { fired++ }.start()
        assertEquals(1, fired)
    }

    @Test
    fun parsesOnlyPositivePids() {
        assertEquals(42L, ParentWatchdog.parsePid(" 42 "))
        assertNull(ParentWatchdog.parsePid(null))
        assertNull(ParentWatchdog.parsePid("0"))
        assertNull(ParentWatchdog.parsePid("-1"))
        assertNull(ParentWatchdog.parsePid("abc"))
    }
}
