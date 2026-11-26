package dev.blockwright.serverrunner

/** Resolves a Paper build: the given [build], or the newest stable one when it is null. */
fun interface PaperBuildSource {
    fun resolve(version: String, build: Int?): PaperBuild
}
