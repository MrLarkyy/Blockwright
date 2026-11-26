package dev.blockwright.launcher.natives

import dev.blockwright.launcher.TestZips
import dev.blockwright.launcher.download.DownloadException
import dev.blockwright.launcher.download.Sha1
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class NativesExtractorTest {
    @TempDir
    lateinit var dir: Path

    private val nativesRoot by lazy { dir.resolve("cache/natives") }
    private val target by lazy { nativesRoot.resolve("1.8.9") }
    private val lib = "native-library".toByteArray()

    private fun jar(name: String, vararg entries: Pair<String, ByteArray>): NativeJar {
        val path = TestZips.write(dir.resolve("libs/$name"), *entries)
        return NativeJar(path, Sha1.of(path), listOf("META-INF/"))
    }

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
    fun unrecordedFileInTheDirectoryTriggersExtraction() {
        val jar = jar("lwjgl-natives.jar", "liblwjgl.so" to lib)
        NativesExtractor().extract(listOf(jar), target)
        Files.writeString(target.resolve("planted.so"), "not from the jar")
        NativesExtractor().extract(listOf(jar), target)
        assertFalse(Files.exists(target.resolve("planted.so")))
        assertArrayEquals(lib, Files.readAllBytes(target.resolve("liblwjgl.so")))
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
}
