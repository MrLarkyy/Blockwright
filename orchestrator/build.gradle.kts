plugins {
    id("blockwright.kotlin-serialization")
    `java-test-fixtures`
}

dependencies {
    implementation(project(":launcher"))
    implementation(project(":server-runner"))
}

val benchMain = "dev.blockwright.orchestrator.bench.BenchMain"

// A multi-process timing run: gated by its own CI job, never part of `check`. The gate logic itself is unit-tested.
tasks.register<JavaExec>("bench") {
    description = "Runs the benchmark harness against perf/budgets.json and writes the report."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val sourceSets = project.extensions.getByType<SourceSetContainer>()
    classpath = sourceSets["main"].runtimeClasspath + sourceSets["testFixtures"].runtimeClasspath
    mainClass.set(benchMain)
    val budgets = providers.gradleProperty("bench.budgets")
        .orElse(rootProject.layout.projectDirectory.file("perf/budgets.json").asFile.absolutePath)
    val reportDir = layout.buildDirectory.dir("reports/bench")
    val strict = providers.gradleProperty("bench.strict").map { it.toBoolean() }.orElse(false)
    val profile = providers.gradleProperty("bench.profile")
    argumentProviders.add(CommandLineArgumentProvider {
        buildList {
            addAll(listOf("--budgets", budgets.get(), "--report-dir", reportDir.get().asFile.absolutePath))
            if (strict.get()) add("--strict")
            profile.orNull?.let { addAll(listOf("--profile", it)) }
        }
    })
    // Real-client profiles: the agent jar, the client cache and, only by the user's explicit choice, the EULA.
    val agentJar = project(":agent").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(agentJar)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dblockwright.agentJar=${agentJar.get().asFile.absolutePath}") })
    providers.gradleProperty("blockwright.acceptEula").orNull?.let { systemProperty("blockwright.acceptEula", it) }
    providers.systemProperty("blockwright.it.cacheDir").orNull?.let { systemProperty("blockwright.it.cacheDir", it) }
    outputs.upToDateWhen { false }
}
