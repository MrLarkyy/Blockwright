package dev.blockwright.serverrunner

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Runs Paperclip once per cached build in patch-only mode with its repository inside the build directory, so the
 * Mojang server jar Paperclip downloads and the patched jar stay in the user cache and are reused by every run.
 */
class BundlerPreparer(
    private val timeout: Duration = 5.minutes,
    private val command: (java: Path, jar: Path, bundlerDir: Path) -> List<String> = ::patchOnlyCommand,
) {
    /** Returns the bundler repository directory to pass as `-DbundlerRepoDir` when the server starts. */
    fun prepare(java: Path, cached: CachedPaperJar): Path = BuildDirLock.withLock(cached.buildDir) {
        val bundlerDir = bundlerDir(cached)
        val marker = bundlerDir.resolve(PREPARED)
        if (Files.exists(marker)) return@withLock bundlerDir
        Files.createDirectories(bundlerDir)
        val log = bundlerDir.resolve("prepare.log")
        val process = ProcessBuilder(command(java, cached.jar, bundlerDir))
            .directory(cached.buildDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start()
        process.outputStream.close()
        val finished = try {
            process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } finally {
            if (process.isAlive) ProcessTree.kill(process)
        }
        if (!finished) throw ServerStartException("Paperclip did not finish within $timeout; last output:\n${tail(log)}")
        val exit = process.exitValue()
        if (exit != 0) throw ServerStartException("Paperclip exited with $exit while patching ${cached.jar.fileName}; last output:\n${tail(log)}")
        Files.writeString(marker, "")
        bundlerDir
    }

    private fun bundlerDir(cached: CachedPaperJar): Path = cached.buildDir.resolve("bundler")

    private fun tail(log: Path): String =
        runCatching { Files.readAllBytes(log).toString(Charsets.UTF_8).lines().takeLast(TAIL_LINES).joinToString("\n") }.getOrDefault("(no output)")

    companion object {
        private const val PREPARED = ".prepared"
        private const val TAIL_LINES = 20

        fun patchOnlyCommand(java: Path, jar: Path, bundlerDir: Path): List<String> = listOf(
            java.toString(),
            "-Dpaperclip.patchonly=true",
            "-DbundlerRepoDir=$bundlerDir",
            "-jar",
            jar.toString(),
        )
    }
}
