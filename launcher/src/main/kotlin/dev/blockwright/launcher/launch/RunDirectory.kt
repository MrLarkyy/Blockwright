package dev.blockwright.launcher.launch

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/** Throwaway client directories; removal retries because Windows releases file handles of a killed JVM late. */
object RunDirectory {
    const val PREFIX = "blockwright-client-"

    fun create(): Path = Files.createTempDirectory(PREFIX)

    @OptIn(ExperimentalPathApi::class)
    fun delete(dir: Path, attempts: Int = 5, pauseMillis: Long = 200): Boolean {
        repeat(attempts) { attempt ->
            try {
                dir.deleteRecursively()
                return true
            } catch (_: IOException) {
                if (attempt < attempts - 1) Thread.sleep(pauseMillis)
            }
        }
        return !Files.exists(dir)
    }
}
