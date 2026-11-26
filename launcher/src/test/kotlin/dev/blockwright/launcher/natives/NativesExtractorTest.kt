package dev.blockwright.launcher.natives

import dev.blockwright.launcher.TestZips
import dev.blockwright.launcher.download.DownloadException
import dev.blockwright.launcher.download.Sha1
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.channels.FileChannel
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NativesExtractorTest {
    @TempDir
    lateinit var dir: Path

    private val nativesRoot by lazy { dir.resolve("cache/natives") }
    private val target by lazy { nativesRoot.resolve("1.8.9") }
    private val lib = "native-library".toByteArray()
    private val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

    private fun jar(name: String, vararg entries: Pair<String, ByteArray>): NativeJar {
        val path = TestZips.write(dir.resolve("libs/$name"), *entries)
        return NativeJar(path, Sha1.of(path), listOf("META-INF/"))
    }

    /** A library the game unpacks into the natives directory at runtime; the marker does not record it. */
    private fun gameWrites(name: String): Path {
        val file = target.resolve(name)
        Files.createDirectories(file.parent)
        return Files.write(file, RUNTIME_LIBRARY)
    }

    /**
     * Makes [file] undeletable while its directory can still be renamed, as a library does that some process
     * has loaded: no write permission on the directory (POSIX; the JDK refuses a plain move of such a directory,
     * so the extractor must move atomically) or a read-only file (Windows).
     */
    private fun protect(file: Path) {
        if (posix) Files.setPosixFilePermissions(file.parent, PosixFilePermissions.fromString("r-x------")) else file.toFile().setReadOnly()
    }

    private fun release(file: Path) {
        if (posix) Files.setPosixFilePermissions(file.parent, PosixFilePermissions.fromString("rwx------")) else file.toFile().setWritable(true)
    }

    private fun backups(): List<Path> =
        Files.list(nativesRoot).use { entries -> entries.filter { it.fileName.toString().startsWith("1.8.9.old-") }.toList() }

    /** Only the target (and its lock file) may remain: no staging or backup directory. */
    private fun assertNoLeftovers(allow: Set<String> = emptySet()) {
        val names = if (Files.exists(nativesRoot)) Files.list(nativesRoot).use { s -> s.map { it.fileName.toString() }.toList() } else emptyList()
        assertTrue(names.all { it == "1.8.9" || it == "1.8.9.lock" || it in allow }, names.toString())
    }

    @Test
    fun extractsEntriesExceptExcludes() {
        val jar = jar("lwjgl-natives.jar", "META-INF/MANIFEST.MF" to "x".toByteArray(), "liblwjgl.so" to lib, "sub/" to ByteArray(0), "sub/libopenal.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("sub/libopenal.so")))
        assertFalse(Files.exists(target.resolve("META-INF")))
        assertNoLeftovers()
    }

    @Test
    fun noJarsStillCreatesTheDirectory() {
        NativesExtractor().extract(emptyList(), target)
        assertTrue(Files.isDirectory(target))
    }

    @ParameterizedTest
    @ValueSource(strings = ["../evil.so", "sub/../../evil.so", "/abs.so", "C:/evil.dll", "..\\evil.dll", "a//b.so"])
    fun zipSlipEntryIsRejectedAndNothingIsWritten(name: String) {
        val jar = jar("evil.jar", "ok.so" to lib, name to lib)
        assertThrows<DownloadException> { NativesExtractor().extract(listOf(jar), target) }
        assertFalse(Files.exists(nativesRoot.resolve("evil.so")))
        assertFalse(Files.exists(dir.resolve("cache/evil.so")))
        assertFalse(Files.exists(target))
        assertNoLeftovers()
    }

    @Test
    fun compressedBombIsStoppedAtThePerEntryLimit() {
        val jar = jar("bomb.jar", "bomb.so" to ByteArray(1_000_000))
        assertTrue(Files.size(jar.path) < 10_000)
        val limits = ExtractionLimits(maxEntryBytes = 64 * 1024)
        assertThrows<DownloadException> { NativesExtractor(limits).extract(listOf(jar), target) }
        assertFalse(Files.exists(target))
        assertNoLeftovers()
    }

    @Test
    fun totalUncompressedSizeIsLimitedAcrossJars() {
        val a = jar("a.jar", "a.so" to ByteArray(600))
        val b = jar("b.jar", "b.so" to ByteArray(600))
        assertThrows<DownloadException> { NativesExtractor(ExtractionLimits(maxTotalBytes = 1000)).extract(listOf(a, b), target) }
        assertFalse(Files.exists(target))
        assertNoLeftovers()
    }

    @Test
    fun entryCountIsLimited() {
        val jar = jar("many.jar", *(1..5).map { "f$it.so" to lib }.toTypedArray())
        assertThrows<DownloadException> { NativesExtractor(ExtractionLimits(maxEntries = 3)).extract(listOf(jar), target) }
        assertFalse(Files.exists(target))
    }

    @Test
    fun rerunWithIntactFilesSkipsExtraction() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib, "sub/libopenal.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        Files.delete(jar.path)
        NativesExtractor().extract(listOf(jar), target)
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
    }

    @Test
    fun changedExtractedFileIsExtractedAgain() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        Files.write(target.resolve("liblwjgl.so"), lib.copyOf().also { it[0] = 'X'.code.toByte() })
        NativesExtractor().extract(listOf(jar), target)
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
        assertNoLeftovers()
    }

    @Test
    fun missingExtractedFileIsExtractedAgain() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib, "sub/libopenal.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        Files.delete(target.resolve("sub/libopenal.so"))
        NativesExtractor().extract(listOf(jar), target)
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("sub/libopenal.so")))
    }

    @Test
    fun filesTheGameAddsKeepTheDirectoryValid() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        val runtime = gameWrites("lwjgl/3.3.3/x64/lwjgl.dll")
        val loose = gameWrites("jna-1234.dll")
        // Without the jar a second extraction would fail, so passing proves the directory was reused.
        Files.delete(jar.path)

        NativesExtractor().extract(listOf(jar), target)

        assertArrayEquals(RUNTIME_LIBRARY, Files.readAllBytes(runtime))
        assertArrayEquals(RUNTIME_LIBRARY, Files.readAllBytes(loose))
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
        assertNoLeftovers()
    }

    @Test
    fun directoryInUseByARunningClientStaysInPlace() {
        NativesExtractor().extract(emptyList(), target)
        val loaded = gameWrites("netty/libnetty_transport.dll")

        // An open file stands for a loaded library: on Windows it makes replacing the directory fail.
        FileChannel.open(loaded, StandardOpenOption.READ).use {
            NativesExtractor().extract(emptyList(), target)
        }

        assertArrayEquals(RUNTIME_LIBRARY, Files.readAllBytes(loaded))
        assertNoLeftovers()
    }

    @Test
    fun changedExtractedFileIsExtractedAgainDespiteFilesTheGameAdded() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        val runtime = gameWrites("lwjgl/3.3.3/x64/lwjgl.dll")
        Files.writeString(target.resolve("liblwjgl.so"), "damaged")

        NativesExtractor().extract(listOf(jar), target)

        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
        assertFalse(Files.exists(runtime))
        assertNoLeftovers()
    }

    @Test
    fun markerEntryOutsideTheDirectoryTriggersExtraction() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        Files.write(nativesRoot.resolve("outside.so"), lib)
        val marker = target.resolve(".extracted")
        Files.writeString(marker, Files.readString(marker) + "\nfile ${Sha1.of(lib)} ${lib.size} ../outside.so")

        NativesExtractor().extract(listOf(jar), target)

        assertFalse(Files.readString(marker).contains("outside.so"))
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
    }

    @Test
    fun unreadableMarkerTriggersExtraction() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        val marker = target.resolve(".extracted")
        val written = Files.readString(marker)
        Files.write(marker, byteArrayOf(0xC3.toByte(), 0x28, 0xFF.toByte(), 0xFE.toByte()))

        NativesExtractor().extract(listOf(jar), target)

        assertEquals(written, Files.readString(marker))
        assertNoLeftovers()
    }

    @Test
    fun concurrentExtractionsOfADamagedDirectoryAllSucceed() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to ByteArray(256 * 1024) { it.toByte() }, "sub/libopenal.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        Files.delete(target.resolve("sub/libopenal.so"))
        val threads = 6
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)

        try {
            (1..threads).map {
                pool.submit<Path> {
                    barrier.await()
                    NativesExtractor().extract(listOf(jar), target)
                }
            }.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertArrayEquals(lib, Files.readAllBytes(target.resolve("sub/libopenal.so")))
        assertNoLeftovers()
    }

    @Test
    fun tamperedMarkerTriggersExtraction() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        Files.write(target.resolve("liblwjgl.so"), "evil".toByteArray())
        val marker = target.resolve(".extracted")
        Files.writeString(marker, Files.readString(marker).replace(Sha1.of(lib), Sha1.of("evil".toByteArray())))
        NativesExtractor().extract(listOf(jar), target)
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
    }

    @Test
    fun entryNamedLikeTheMarkerIsRejected() {
        assertThrows<DownloadException> { NativesExtractor().extract(listOf(jar("m.jar", ".extracted" to lib)), target) }
        assertFalse(Files.exists(target))
    }

    @Test
    fun leftoversOfACrashedRunAreRemoved() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        val staging = Files.createDirectories(nativesRoot.resolve("1.8.9.tmp-crashed/sub"))
        Files.writeString(staging.resolve("half.so"), "partial")
        Files.createDirectories(nativesRoot.resolve("1.8.9.old-crashed"))
        val otherVersion = Files.createDirectories(nativesRoot.resolve("1.8.8.tmp-other"))

        NativesExtractor().extract(listOf(jar), target)

        assertNoLeftovers(allow = setOf(otherVersion.fileName.toString()))
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
    }

    @Test
    fun leftoverThatCannotBeDeletedDoesNotFailExtraction() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        val held = Files.createDirectories(nativesRoot.resolve("1.8.9.old-crashed")).resolve("held.dll")
        Files.write(held, lib)
        protect(held)
        try {
            NativesExtractor().extract(listOf(jar), target)
            NativesExtractor().extract(listOf(jar), target)
            assumeTrue(Files.exists(held), "this file system deletes a protected file")
        } finally {
            release(held)
        }
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))

        NativesExtractor().extract(listOf(jar), target)

        assertNoLeftovers()
    }

    @Test
    fun backupThatCannotBeDeletedDoesNotFailTheReplacement() {
        NativesExtractor().extract(listOf(jar("old.jar", "old.so" to lib)), target)
        val fresh = jar("new.jar", "new.so" to lib)
        protect(target.resolve("old.so"))
        try {
            NativesExtractor().extract(listOf(fresh), target)
        } finally {
            if (Files.exists(target.resolve("old.so"))) release(target.resolve("old.so"))
            backups().forEach { release(it.resolve("old.so")) }
        }
        assertTrue(Files.exists(target.resolve("new.so")))
        assertFalse(Files.exists(target.resolve("old.so")))
        assumeTrue(backups().isNotEmpty(), "this file system deletes a protected file")

        NativesExtractor().extract(listOf(fresh), target)

        assertTrue(Files.exists(target.resolve("new.so")))
        assertNoLeftovers()
    }

    @Test
    fun changedJarsReplaceTheWholeDirectory() {
        NativesExtractor().extract(listOf(jar("old.jar", "old.so" to lib)), target)
        NativesExtractor().extract(listOf(jar("new.jar", "new.so" to lib)), target)
        assertTrue(Files.exists(target.resolve("new.so")))
        assertFalse(Files.exists(target.resolve("old.so")))
        assertNoLeftovers()
    }

    @Test
    fun failedReextractionKeepsThePreviousDirectory() {
        NativesExtractor().extract(listOf(jar("good.jar", "good.so" to lib)), target)
        assertThrows<DownloadException> { NativesExtractor().extract(listOf(jar("bad.jar", "../evil.so" to lib)), target) }
        assertTrue(Files.exists(target.resolve("good.so")))
        assertNoLeftovers()
    }

    private companion object {
        val RUNTIME_LIBRARY = "unpacked by the game".toByteArray()
    }
}
