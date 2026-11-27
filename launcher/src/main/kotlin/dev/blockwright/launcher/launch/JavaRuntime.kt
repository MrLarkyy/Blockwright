package dev.blockwright.launcher.launch

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The JVM a client runs on: [executable] and its feature version, read from the runtime's `release` file so no
 * process has to start. A client needs at least the version its version JSON names.
 */
data class JavaRuntime(val executable: Path, val featureVersion: Int) {
    fun requireAtLeast(major: Int?) {
        if (major != null && featureVersion < major) {
            throw LaunchException("the client needs Java $major or newer, $executable is Java $featureVersion")
        }
    }

    companion object {
        private val JAVA_VERSION = Regex("""^JAVA_VERSION="(\d+)""", RegexOption.MULTILINE)

        fun current(): JavaRuntime {
            val home = Path.of(System.getProperty("java.home"))
            return JavaRuntime(executableIn(home), Runtime.version().feature())
        }

        /** [executable] is `<java home>/bin/java[.exe]`. */
        fun of(executable: Path): JavaRuntime {
            val release = executable.toAbsolutePath().parent?.parent?.resolve("release")
                ?: throw LaunchException("not a Java executable inside a runtime: $executable")
            val text = try {
                Files.readString(release)
            } catch (e: IOException) {
                throw LaunchException("cannot read the Java version of $executable ($release): ${e.message}", e)
            }
            val version = JAVA_VERSION.find(text)?.groupValues?.get(1)?.toInt()
                ?: throw LaunchException("no JAVA_VERSION in $release")
            return JavaRuntime(executable, version)
        }

        fun executableIn(home: Path): Path =
            home.resolve("bin").resolve(if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java")
    }
}
