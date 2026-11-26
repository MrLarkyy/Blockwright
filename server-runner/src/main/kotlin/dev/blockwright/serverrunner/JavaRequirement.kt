package dev.blockwright.serverrunner

import java.nio.file.Path

/** The Java a Paper build needs versus the runtime that will start it. */
object JavaRequirement {
    fun currentJava(): Path {
        val name = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        return Path.of(System.getProperty("java.home"), "bin", name)
    }

    /** A caller-provided Java is not probed (that would mean starting it); only the current runtime is checked. */
    fun check(build: PaperBuild, customJava: Path?, runtimeFeature: Int = Runtime.version().feature()) {
        if (customJava != null) return
        if (runtimeFeature < build.javaMinimum) {
            throw ServerStartException(
                "Paper ${build.version} build ${build.build} needs Java ${build.javaMinimum}, the runner runs on Java $runtimeFeature; " +
                    "set ServerRunConfig.javaExecutable to a Java ${build.javaMinimum}+",
            )
        }
    }
}
