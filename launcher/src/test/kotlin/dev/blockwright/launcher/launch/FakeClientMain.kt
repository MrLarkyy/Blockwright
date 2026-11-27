package dev.blockwright.launcher.launch

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Plays a client for ClientProcess tests, speaking the agent's status lines. Modes: `join` (joined, then a graceful
 * exit 0 on stdin EOF), `refuse` (failed with a reason), `ignore-stop` (joined, starts a child, ignores stdin),
 * `exit` (exits 3 at once), `silent` (says nothing, ignores stdin).
 */
object FakeClientMain {
    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "join" -> {
                status("joined video=dummy")
                awaitEof()
                status("stopping reason=stdin-closed")
                exitProcess(0)
            }
            "refuse" -> {
                status("failed reason=You_are_not_whitelisted_on_this_server!")
                awaitEof()
                exitProcess(0)
            }
            "ignore-stop" -> {
                val argFile = Path.of("child.args").toAbsolutePath()
                val arguments = listOf("-cp", System.getProperty("java.class.path"), FakeClientMain::class.java.name, "silent")
                val child = ProcessBuilder(LaunchCommand.write(JavaRuntime.current().executable, argFile, arguments)).start()
                println("CHILD ${child.pid()}")
                status("joined video=dummy")
                Thread.sleep(Long.MAX_VALUE)
            }
            "exit" -> {
                println("crashing on purpose")
                exitProcess(3)
            }
            else -> Thread.sleep(Long.MAX_VALUE)
        }
    }

    private fun status(rest: String) {
        println("[00:00:00] [Render thread/INFO]: BLOCKWRIGHT_AGENT state=$rest")
        System.out.flush()
    }

    private fun awaitEof() {
        while (System.`in`.read() >= 0) {
            // Discard until the launcher closes stdin.
        }
    }
}
