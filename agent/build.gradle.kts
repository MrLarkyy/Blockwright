plugins {
    id("blockwright.kotlin-module")
    alias(libs.plugins.fabric.loom)
}

// 26.x ships with Mojang's names: Loom compiles against the client in its Gradle cache, nothing is remapped and
// nothing from the client ends up in the agent jar (AgentJarContentTest).
dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    implementation(libs.fabric.loader)
    implementation(libs.fabric.language.kotlin)
    include(libs.fabric.language.kotlin)
}

tasks.processResources {
    val properties = mapOf("version" to project.version.toString(), "minecraft" to libs.versions.minecraft.get())
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}

tasks.test {
    val agentJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(agentJar)
    systemProperty("blockwright.minecraftVersion", libs.versions.minecraft.get())
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dblockwright.agentJar=${agentJar.get().asFile.absolutePath}") })
}
