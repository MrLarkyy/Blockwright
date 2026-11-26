package dev.blockwright.launcher.download

import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/** Run in a child JVM: prints `locked` when another process holds the OS lock on the file, else `free`. */
object LockProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        FileChannel.open(Path.of(args[0]), CREATE, WRITE).use { channel ->
            val lock = channel.tryLock()
            println(if (lock == null) "locked" else "free")
            lock?.release()
        }
    }
}
