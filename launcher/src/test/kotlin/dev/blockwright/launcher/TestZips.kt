package dev.blockwright.launcher

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds synthetic zip archives; entry names are written verbatim, including hostile ones. */
object TestZips {
    fun bytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun write(target: Path, vararg entries: Pair<String, ByteArray>): Path {
        Files.createDirectories(target.parent)
        Files.write(target, bytes(*entries))
        return target
    }
}
