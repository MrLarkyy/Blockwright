pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Fabric Loom (agent) adds its own repositories for the Minecraft client and libraries it compiles against
    // (Gradle cache only, never packaged); every other module declares none and uses the ones below.
    repositoriesMode = RepositoriesMode.PREFER_PROJECT
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }
}

// PREFER_PROJECT would let any project bring its own repositories; only Loom in :agent may (FAIL_ON_PROJECT_REPOS
// for everything else). Fails every build, `check` and the compile included, at configuration time.
gradle.lifecycle.afterProject {
    if (path != ":agent" && repositories.isNotEmpty()) {
        throw GradleException(
            "$path declares repositories ${repositories.names}; only :agent (Fabric Loom) may, every other project " +
                "uses the ones in settings.gradle.kts",
        )
    }
}

rootProject.name = "blockwright"

include("protocol", "launcher", "agent", "orchestrator", "mcp", "cli", "server-runner")
