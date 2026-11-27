import java.security.MessageDigest

plugins {
    id("blockwright.kotlin-serialization")
}

// The Fabric loader runtime a client launches with: the loader and the libraries of its fabric-loader-<v>.json
// ("common"). Pinned here, downloaded and verified by the launcher at runtime, never bundled.
val fabricRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    fabricRuntime(libs.fabric.loader)
    fabricRuntime(libs.sponge.mixin)
    fabricRuntime(libs.asm)
    fabricRuntime(libs.asm.analysis)
    fabricRuntime(libs.asm.commons)
    fabricRuntime(libs.asm.tree)
    fabricRuntime(libs.asm.util)
    testImplementation(project(":server-runner"))
}

abstract class GenerateFabricRuntime : DefaultTask() {
    @get:Input abstract val loaderVersion: Property<String>

    @get:Input abstract val coordinates: ListProperty<String>

    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val jars: ListProperty<RegularFile>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        val entries = coordinates.get().zip(jars.get().map { it.asFile }).sortedBy { it.first }.joinToString(",\n") { (coordinate, jar) ->
            val sha1 = MessageDigest.getInstance("SHA-1").digest(jar.readBytes()).joinToString("") { "%02x".format(it) }
            val repository = if (coordinate.startsWith("net.fabricmc:")) "https://maven.fabricmc.net/" else "https://repo.maven.apache.org/maven2/"
            """    {"coordinates": "$coordinate", "sha1": "$sha1", "size": ${jar.length()}, "repository": "$repository"}"""
        }
        val target = outputDir.get().file("dev/blockwright/launcher/launch/fabric/fabric-runtime.json").asFile
        target.parentFile.mkdirs()
        target.writeText("{\n  \"loaderVersion\": \"${loaderVersion.get()}\",\n  \"artifacts\": [\n$entries\n  ]\n}\n")
    }
}

val generateFabricRuntime by tasks.registering(GenerateFabricRuntime::class) {
    val artifacts = fabricRuntime.incoming.artifacts.resolvedArtifacts
    loaderVersion.set(libs.versions.fabric.loader)
    coordinates.set(artifacts.map { set -> set.map { (it.id.componentIdentifier as ModuleComponentIdentifier).let { id -> "${id.group}:${id.module}:${id.version}" } } })
    jars.set(artifacts.map { set -> set.map { layout.projectDirectory.file(it.file.absolutePath) } })
    outputDir.set(layout.buildDirectory.dir("generated/fabric-runtime"))
}

sourceSets.main {
    resources.srcDir(generateFabricRuntime)
}

// The real-client integration tests need the agent jar and, for parity, may be told that a skip is a failure.
tasks.named<Test>("integrationTest") {
    val agentJar = project(":agent").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(agentJar)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dblockwright.agentJar=${agentJar.get().asFile.absolutePath}") })
    providers.gradleProperty("blockwright.requireParity").orNull?.let { systemProperty("blockwright.requireParity", it) }
    providers.gradleProperty("blockwright.it.keepRunDir").orNull?.let { systemProperty("blockwright.it.keepRunDir", it) }
    systemProperty("blockwright.minecraftVersion", libs.versions.minecraft.get())
}
