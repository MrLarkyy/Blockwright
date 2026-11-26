package dev.blockwright.serverrunner

import java.nio.file.Path

/** A verified Paper jar in the user cache; [buildDir] also holds the Paperclip bundler output. */
data class CachedPaperJar(val build: PaperBuild, val jar: Path, val buildDir: Path)
