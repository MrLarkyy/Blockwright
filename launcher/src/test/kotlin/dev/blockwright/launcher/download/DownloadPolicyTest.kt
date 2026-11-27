package dev.blockwright.launcher.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.time.Duration

class DownloadPolicyTest {
    private val policy = DownloadPolicy()

    @Test
    fun permitsHttpsOnMojangSubdomains() {
        for (url in listOf(
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
            "https://piston-data.mojang.com/v1/objects/x/client.jar",
            "https://libraries.minecraft.net/a/b.jar",
            "https://resources.download.minecraft.net/ab/abcd",
            "https://LIBRARIES.MINECRAFT.NET/a.jar",
        )) {
            assertTrue(policy.permits(URI(url)), url)
        }
    }

    @Test
    fun permitsExactlyTheFabricMavenAndMavenCentralForTheLoaderRuntime() {
        assertTrue(policy.permits(URI("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar")))
        assertTrue(policy.permits(URI("https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar")))
        for (url in listOf(
            "http://maven.fabricmc.net/a.jar",
            "https://evil.maven.fabricmc.net/a.jar",
            "https://maven.fabricmc.net.example/a.jar",
            "https://fabricmc.net/a.jar",
            "https://central.repo.maven.apache.org/a.jar",
        )) {
            assertFalse(policy.permits(URI(url)), url)
        }
    }

    @Test
    fun refusesOtherSchemesHostsAndCredentials() {
        for (url in listOf(
            "http://libraries.minecraft.net/a.jar",
            "ftp://libraries.minecraft.net/a.jar",
            "https://mojang.com.evil.example/a.jar",
            "https://notmojang.com/a.jar",
            "https://minecraft.net.example/a.jar",
            "https://u:p@libraries.minecraft.net/a.jar",
            "/relative/a.jar",
        )) {
            assertFalse(policy.permits(URI(url)), url)
        }
    }

    @Test
    fun plainHttpNeedsAnExplicitOptOutAndExactHostsMatchExactly() {
        val local = DownloadPolicy(allowedHosts = listOf("127.0.0.1"), httpsOnly = false)
        assertTrue(local.permits(URI("http://127.0.0.1:8080/a")))
        assertFalse(local.permits(URI("http://127.0.0.2/a")))
    }

    @Test
    fun rejectsNonsensicalLimits() {
        assertThrows<IllegalArgumentException> { DownloadPolicy(attempts = 0) }
        assertThrows<IllegalArgumentException> { DownloadPolicy(parallelism = 0) }
        assertThrows<IllegalArgumentException> { DownloadPolicy(retryDelay = Duration.ofSeconds(10), maxRetryDelay = Duration.ofSeconds(1)) }
        assertThrows<IllegalArgumentException> { DownloadPolicy(maxRedirects = -1) }
    }

    @Test
    fun retryDelayDoublesUpToTheMaximum() {
        val delays = (1..8).map { policy.retryDelay(it).toMillis() }
        assertEquals(listOf(500L, 1000L, 2000L, 4000L, 8000L, 8000L, 8000L, 8000L), delays)
        assertEquals(Duration.ofSeconds(8), policy.retryDelay(Int.MAX_VALUE))
        assertEquals(Duration.ZERO, DownloadPolicy(retryDelay = Duration.ZERO).retryDelay(3))
    }
}
