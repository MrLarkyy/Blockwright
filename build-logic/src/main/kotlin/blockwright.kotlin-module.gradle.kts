import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
}

group = "dev.blockwright"
version = "0.1.0-SNAPSHOT"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

// Tests tagged "integration" reach real external services (network); the default build stays offline.
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs the tests tagged \"integration\" (network access)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
    // Modules without integration tests must not fail the task; real network results are never cached.
    failOnNoDiscoveredTests = false
    outputs.upToDateWhen { false }
    outputs.doNotCacheIf("integration tests depend on external services") { true }
    providers.systemProperty("blockwright.it.cacheDir").orNull?.let { systemProperty("blockwright.it.cacheDir", it) }
    // Accepting the Minecraft EULA is the user's explicit choice per run (-Pblockwright.acceptEula=true), never a default.
    providers.gradleProperty("blockwright.acceptEula").orNull?.let { systemProperty("blockwright.acceptEula", it) }
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    }
}
