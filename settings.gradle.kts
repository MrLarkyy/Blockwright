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

rootProject.name = "blockwright"

include("protocol", "launcher", "agent", "orchestrator", "mcp", "cli", "server-runner")
