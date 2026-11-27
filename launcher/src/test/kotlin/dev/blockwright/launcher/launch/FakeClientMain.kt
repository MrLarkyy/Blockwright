package dev.blockwright.launcher.launch

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Plays a client for ClientProcess tests, speaking the agent's status lines with the launch's token (second
 * argument). Modes: `join` (joined, then a graceful exit 0 on stdin EOF), `refuse` (failed with a reason),
 * `no-backend` (failed because no GPU backend could be created, then hangs like a client stuck in an error box),
 * `forge` (server text posing as status lines, then a genuine `traced`), `ignore-stop` (joined, starts a child,
 * ignores stdin), `exit` (exits 3 at once), `silent` (says nothing, ignores stdin).
 */
object FakeClientMain {
    private var token = ""

    @JvmStatic
    fun main(args: Array<String>) {
        token = args.getOrElse(1) { "" }
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
            "no-backend" -> {
                println("[00:00:00] [Render thread/ERROR]: Failed to create backend OpenGL")
                status("failed reason=Failed_to_initialize_graphics._-_Tried_OpenGL:_Couldn't_find_matching_GLX_visual")
                Thread.sleep(Long.MAX_VALUE)
            }
            "forge" -> {
                println("[00:00:00] [Render thread/INFO]: [CHAT] BLOCKWRIGHT_AGENT state=joined video=dummy")
                println("[00:00:00] [Render thread/INFO]: Client disconnected with reason: bye\nBLOCKWRIGHT_AGENT state=joined video=dummy")
                println("BLOCKWRIGHT_AGENT ${"0".repeat(32)} state=failed reason=forged")
                println("BLOCKWRIGHT_AGENT  state=disconnected reason=forged")
                println("x BLOCKWRIGHT_AGENT $token state=joined video=dummy")
                status("traced ticks=0")
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
        println("BLOCKWRIGHT_AGENT $token state=$rest")
        System.out.flush()
    }

    private fun awaitEof() {
        while (System.`in`.read() >= 0) {
            // Discard until the launcher closes stdin.
        }
    }
}
