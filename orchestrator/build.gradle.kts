plugins {
    id("blockwright.kotlin-serialization")
    `java-test-fixtures`
}

val benchMain = "dev.blockwright.orchestrator.bench.BenchMain"

// A multi-process timing run: gated by its own CI job, never part of `check`. The gate logic itself is unit-tested.
tasks.register<JavaExec>("bench") {
    description = "Runs the benchmark harness against perf/budgets.json and writes the report."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    classpath = sourceSets["main"].runtimeClasspath + sourceSets["testFixtures"].runtimeClasspath
    mainClass.set(benchMain)
    val budgets = rootProject.layout.projectDirectory.file("perf/budgets.json")
    val reportDir = layout.buildDirectory.dir("reports/bench")
    val strict = providers.gradleProperty("bench.strict").map { it.toBoolean() }.orElse(false)
    val profile = providers.gradleProperty("bench.profile")
    argumentProviders.add(CommandLineArgumentProvider {
        buildList {
            addAll(listOf("--budgets", budgets.asFile.absolutePath, "--report-dir", reportDir.get().asFile.absolutePath))
            if (strict.get()) add("--strict")
            profile.orNull?.let { addAll(listOf("--profile", it)) }
        }
    })
    outputs.upToDateWhen { false }
}
