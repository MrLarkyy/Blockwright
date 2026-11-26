package dev.blockwright.serverrunner

import java.nio.file.Files
import java.nio.file.Path

/** The run directory's `eula.txt`; written only for a run whose config carries the user's explicit acceptance. */
object EulaFile {
    fun write(runDir: Path) {
        Files.writeString(runDir.resolve("eula.txt"), "# accepted explicitly by the user for this Blockwright run\neula=true\n")
    }
}
