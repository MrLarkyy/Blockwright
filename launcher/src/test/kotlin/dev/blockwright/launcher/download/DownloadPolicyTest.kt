package dev.blockwright.launcher.download

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

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
    }
}
