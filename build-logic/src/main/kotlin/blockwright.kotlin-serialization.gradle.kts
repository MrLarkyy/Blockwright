import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("blockwright.kotlin-module")
    kotlin("plugin.serialization")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(libs.findLibrary("kotlinx-serialization-json").get())
}
