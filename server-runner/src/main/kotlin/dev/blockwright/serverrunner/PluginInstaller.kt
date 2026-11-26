package dev.blockwright.serverrunner

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Copies the plugin jars under test into `plugins/`, after checking every one of them. */
object PluginInstaller {
    private val DESCRIPTORS = setOf("plugin.yml", "paper-plugin.yml")

    /** Checks every plugin without copying anything; [install] runs the same checks. */
    fun check(plugins: List<Path>) {
        plugins.forEach(::requirePlugin)
        val duplicates = plugins.groupBy { it.fileName.toString().lowercase() }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) throw ServerRunnerException("plugin file names must be unique: ${duplicates.sorted()}")
    }

    fun install(plugins: List<Path>, runDir: Path): List<Path> {
        check(plugins)
        val pluginsDir = Files.createDirectories(runDir.resolve("plugins"))
        return plugins.map { Files.copy(it, pluginsDir.resolve(it.fileName.toString())) }
    }

    private fun requirePlugin(plugin: Path) {
        if (!Files.isRegularFile(plugin)) throw ServerRunnerException("plugin $plugin is not a file")
        if (!plugin.fileName.toString().endsWith(".jar", ignoreCase = true)) throw ServerRunnerException("plugin $plugin is not a .jar")
        val hasDescriptor = try {
            ZipFile(plugin.toFile()).use { zip -> DESCRIPTORS.any { zip.getEntry(it) != null } }
        } catch (e: ZipException) {
            throw ServerRunnerException("plugin $plugin is not a jar archive", e)
        } catch (e: IOException) {
            throw ServerRunnerException("plugin $plugin cannot be read", e)
        }
        if (!hasDescriptor) throw ServerRunnerException("plugin $plugin has neither plugin.yml nor paper-plugin.yml")
    }
}
