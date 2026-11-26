package dev.blockwright.launcher

import dev.blockwright.launcher.download.Sha1
import java.net.URI

/**
 * Serves the synthetic fixture version `1.0-test` through a [FakeTransport]. Fixture templates reference payloads
 * as `{{sha1:key}}` and `{{size:key}}`, so hashes always match the fake bytes.
 */
class FakeMojang(val transport: FakeTransport = FakeTransport()) {
    val payloads = LinkedHashMap<String, ByteArray>()
    val urls = LinkedHashMap<String, URI>()

    init {
        serve("client", "https://piston-data.mojang.com/v1/objects/fixture/client.jar", "fake client jar".toByteArray())
        serve("core", "https://libraries.minecraft.net/test/core/1.0/core-1.0.jar", "fake core library".toByteArray())
        serve("renderLinux", "https://libraries.minecraft.net/test/render/1.0/render-1.0-natives-linux.jar", "fake modern natives".toByteArray())
        serve(
            "legacyLinux",
            "https://libraries.minecraft.net/test/legacy/2.9/legacy-2.9-natives-linux.jar",
            TestZips.bytes("META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n".toByteArray(), "liblegacy.so" to LEGACY_NATIVE),
        )
        serve("util", "https://libraries.minecraft.net/test/util/1.0/util-1.0.jar", "fake util library".toByteArray())
        serveAsset("assetA", """{"test.key": "value"}""".toByteArray())
        serveAsset("assetB", ByteArray(3000) { (it % 7).toByte() })
        serve("assetIndex", ASSET_INDEX_URL, render("asset-index.json"))
        serve("version", VERSION_URL, render("version.json"))
        transport.put(VersionResolver.MANIFEST_URI, render("manifest.json"))
    }

    fun sha1(key: String): String = Sha1.of(payloads.getValue(key))

    fun serve(key: String, url: String, body: ByteArray) {
        payloads[key] = body
        urls[key] = transport.put(url, body)
    }

    private fun serveAsset(key: String, body: ByteArray) {
        val hash = Sha1.of(body)
        serve(key, "https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash", body)
    }

    private fun render(fixture: String): ByteArray {
        val template = FakeMojang::class.java.getResource("/fixtures/$fixture")!!.readText()
        return PLACEHOLDER.replace(template) { match ->
            val body = payloads.getValue(match.groupValues[2])
            if (match.groupValues[1] == "sha1") Sha1.of(body) else body.size.toString()
        }.toByteArray()
    }

    companion object {
        const val VERSION_ID = "1.0-test"
        const val VERSION_URL = "https://piston-meta.mojang.com/v1/packages/fixture/1.0-test.json"
        const val ASSET_INDEX_URL = "https://piston-meta.mojang.com/v1/packages/fixture/test-1.json"
        val LEGACY_NATIVE = "legacy native code".toByteArray()
        private val PLACEHOLDER = Regex("\\{\\{(sha1|size):(\\w+)}}")
    }
}
