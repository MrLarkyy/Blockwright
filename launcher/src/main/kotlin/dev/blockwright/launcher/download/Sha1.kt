package dev.blockwright.launcher.download

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

object Sha1 {
    fun digest(): MessageDigest = MessageDigest.getInstance("SHA-1")

    fun hex(digest: MessageDigest): String = HexFormat.of().formatHex(digest.digest())

    fun of(bytes: ByteArray): String = hex(digest().apply { update(bytes) })

    fun of(file: Path): String {
        val digest = digest()
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest)
    }

    fun matches(file: Path, sha1: String?, size: Long?): Boolean {
        if (!Files.isRegularFile(file)) return false
        if (size != null && Files.size(file) != size) return false
        return sha1 == null || of(file).equals(sha1, ignoreCase = true)
    }

    private const val BUFFER = 64 * 1024
}
