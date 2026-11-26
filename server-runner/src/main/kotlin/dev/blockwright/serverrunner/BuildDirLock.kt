package dev.blockwright.serverrunner

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Serialises work on one cache build directory across threads and processes. The in-JVM lock comes first
 * because a second FileChannel.lock on the same file inside one JVM throws OverlappingFileLockException;
 * re-entry from the holding thread skips the file lock for the same reason.
 */
object BuildDirLock {
    private val locks = ConcurrentHashMap<Path, ReentrantLock>()

    fun <T> withLock(dir: Path, block: () -> T): T {
        val key = dir.toAbsolutePath().normalize()
        val lock = locks.computeIfAbsent(key) { ReentrantLock() }
        return lock.withLock {
            if (lock.holdCount > 1) return@withLock block()
            Files.createDirectories(key)
            FileChannel.open(key.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { block() }
            }
        }
    }
}
