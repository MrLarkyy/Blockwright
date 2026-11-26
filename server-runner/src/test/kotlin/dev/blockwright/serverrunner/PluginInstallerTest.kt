package dev.blockwright.serverrunner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginInstallerTest {
    @TempDir
    lateinit var dir: Path

    private fun jar(name: String, entry: String = "plugin.yml", folder: String = "in"): Path {
        val file = Files.createDirectories(dir.resolve(folder)).resolve(name)
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry(entry))
            zip.write("name: Test\nmain: test.Main\nversion: 1\n".toByteArray())
            zip.closeEntry()
        }
        return file
    }

    private fun runDir(): Path = Files.createDirectories(dir.resolve("run"))

    @Test
    fun copiesPluginsIntoThePluginsDir() {
        val installed = PluginInstaller.install(listOf(jar("a.jar"), jar("b.jar", entry = "paper-plugin.yml")), runDir())

        assertEquals(listOf("a.jar", "b.jar"), installed.map { it.fileName.toString() })
        installed.forEach { assertEquals(runDir().resolve("plugins"), it.parent) }
    }

    @Test
    fun missingPluginIsRejected() {
        assertThrows(ServerRunnerException::class.java) { PluginInstaller.install(listOf(dir.resolve("missing.jar")), runDir()) }
    }

    @Test
    fun nonJarIsRejected() {
        val text = Files.writeString(dir.resolve("plugin.txt"), "x")

        assertThrows(ServerRunnerException::class.java) { PluginInstaller.install(listOf(text), runDir()) }
    }

    @Test
    fun jarWithoutDescriptorIsRejected() {
        assertThrows(ServerRunnerException::class.java) { PluginInstaller.install(listOf(jar("lib.jar", entry = "lib/A.class")), runDir()) }
    }

    @Test
    fun corruptJarIsRejected() {
        val corrupt = Files.writeString(dir.resolve("corrupt.jar"), "not a zip")

        assertThrows(ServerRunnerException::class.java) { PluginInstaller.install(listOf(corrupt), runDir()) }
    }

    @Test
    fun duplicateNamesAreRejectedBeforeAnythingIsCopied() {
        val first = jar("same.jar", folder = "one")
        val second = jar("Same.jar", folder = "two")

        assertThrows(ServerRunnerException::class.java) { PluginInstaller.install(listOf(first, second), runDir()) }
        assertFalse(Files.exists(runDir().resolve("plugins")))
    }
}
