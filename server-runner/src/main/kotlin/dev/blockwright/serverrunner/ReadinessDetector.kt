package dev.blockwright.serverrunner

/** Recognises Paper's "server is ready" console line. */
object ReadinessDetector {
    private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    private val READY = Regex("""Done \([0-9.,]+s\)! For help, type "help"""")

    fun isReady(line: String): Boolean = READY.containsMatchIn(ANSI.replace(line, ""))
}
