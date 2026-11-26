package dev.blockwright.orchestrator.bench

import java.awt.image.BufferedImage
import kotlin.concurrent.thread

/** Stand-in client for the benchmark harness: speaks the line protocol without any Minecraft code. */
object StubClient {
    @Volatile
    private var loading = false

    // Held for the process lifetime so the touched pages stay resident.
    @Volatile
    private var memory = ByteArray(0)

    @Volatile
    private var sink = 0L

    @JvmStatic
    fun main(args: Array<String>) {
        val allocMib = args.indexOf("--alloc-mib").takeIf { it >= 0 }?.let { args[it + 1].toInt() } ?: 0
        memory = ByteArray(allocMib * 1024 * 1024)
        for (i in memory.indices step PAGE) memory[i] = 1
        if ("--hang" in args) Thread.sleep(Long.MAX_VALUE)
        startLoadThread()
        println("READY")
        System.out.flush()
        generateSequence(::readlnOrNull).forEach { line ->
            when (line) {
                "SHOT" -> reply(render())
                "LOAD on" -> loading = true
                "LOAD off" -> loading = false
                "QUIT" -> return
            }
        }
    }

    private fun startLoadThread() {
        thread(isDaemon = true, name = "stub-load") {
            while (true) {
                if (loading) sink += sink xor 31 else Thread.sleep(IDLE_POLL_MS)
            }
        }
    }

    private fun render(): String {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until SIZE) for (x in 0 until SIZE) image.setRGB(x, y, (x * 4) shl 16 or (y * 4))
        return "SHOT ok"
    }

    private fun reply(line: String) {
        println(line)
        System.out.flush()
    }

    private const val PAGE = 4096
    private const val SIZE = 64
    private const val IDLE_POLL_MS = 20L
}
