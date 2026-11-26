package dev.blockwright.serverrunner

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [PaperServerRunner] wired to test doubles: a local HTTP server serving synthetic jar bytes, a counting build
 * source, a fake Paperclip, [FakePaperServer] instead of Paper and a recording EULA writer that never writes the
 * accepting file.
 */
class RunnerFixture(private val temp: Path, mode: String = "ready") : AutoCloseable {
    val server = FixtureHttpServer()
    val resolves = AtomicInteger()
    val runDirsCreated = CopyOnWriteArrayList<Path>()
    val eulaWrites = CopyOnWriteArrayList<Path>()
    val launcher = FakeServers.Launcher(mode)
    private val started = CopyOnWriteArrayList<ServerProcess>()
    private val jarBytes = "synthetic paper jar".toByteArray()

    val cacheRoot: Path = temp.resolve("cache")
    val artifactDir: Path = temp.resolve("artefacts")

    init {
        server.bytes("/paper.jar", jarBytes)
    }

    private val source = PaperBuildSource { version, build ->
        resolves.incrementAndGet()
        val sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jarBytes))
        PaperBuild(version, build ?: 262, "STABLE", "paper-$version.jar", sha, jarBytes.size.toLong(), server.uri("/paper.jar").toString(), 25)
    }

    val runner = PaperServerRunner(
        http = server.http,
        source = source,
        preparer = BundlerPreparer { _, _, _ -> FakeServers.command("prepare-ok") },
        launcher = launcher,
        eulaWriter = { eulaWrites.add(it) },
        runDirs = { Files.createDirectories(temp.resolve("run-${runDirsCreated.size}")).also { runDirsCreated.add(it) } },
    )

    fun config(acceptEula: Boolean, plugins: List<Path> = emptyList()) = ServerRunConfig(
        version = "26.3",
        build = 262,
        plugins = plugins,
        cacheRoot = cacheRoot,
        artifactDir = artifactDir,
        acceptEula = acceptEula,
    )

    fun start(config: ServerRunConfig): ServerProcess = runner.start(config).also { started += it }

    override fun close() {
        started.forEach { it.stop() }
        server.close()
    }
}
