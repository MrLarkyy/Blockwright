package dev.blockwright.serverrunner

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Stands in for a Paper server in tests (no Paper, no Mojang code, no EULA). Every mode exits on its own within
 * [MAX_LIFETIME_MS] so a test bug cannot leave it running for long.
 */
object FakePaperServer {
    const val READY_LINE = "[12:00:00 INFO]: Done (1.234s)! For help, type \"help\""
    private const val MAX_LIFETIME_MS = 120_000L

    @JvmStatic
    fun main(args: Array<String>) {
        when (val mode = args.firstOrNull()) {
            "ready" -> serve(echo = false)
            "echo" -> serve(echo = true)
            "ignore-stop" -> {
                announceReady()
                Thread.sleep(MAX_LIFETIME_MS)
            }
            "spawn-child" -> {
                val child = ProcessBuilder(FakeServers.command("sleep")).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                say("child ${child.pid()}")
                serve(echo = false)
            }
            "sleep" -> Thread.sleep(MAX_LIFETIME_MS)
            "exit-early" -> {
                say("[12:00:00 ERROR]: FAILED TO BIND TO PORT!")
                exitProcess(3)
            }
            "never-ready" -> {
                say("[12:00:00 INFO]: Loading libraries, please wait...")
                Thread.sleep(MAX_LIFETIME_MS)
            }
            "prepare-ok" -> say("Applying patches")
            "prepare-fail" -> {
                say("Applying patches")
                say("patch failed: boom")
                exitProcess(1)
            }
            else -> {
                say("unknown mode $mode")
                exitProcess(2)
            }
        }
        exitProcess(0)
    }

    private fun serve(echo: Boolean) {
        announceReady()
        val reader = System.`in`.bufferedReader()
        while (true) {
            val line = reader.readLine() ?: return
            if (line == "stop") {
                say("[12:00:01 INFO]: Stopping server")
                return
            }
            if (echo) say("echo: $line")
        }
    }

    private fun announceReady() {
        val logs = Files.createDirectories(Path.of("logs"))
        Files.writeString(logs.resolve("latest.log"), "$READY_LINE\n")
        say("[12:00:00 INFO]: Starting minecraft server version fake")
        say(READY_LINE)
    }

    private fun say(line: String) {
        println(line)
        System.out.flush()
    }
}
