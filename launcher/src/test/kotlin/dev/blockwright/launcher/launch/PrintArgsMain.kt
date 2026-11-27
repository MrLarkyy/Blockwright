package dev.blockwright.launcher.launch

/** Prints each argument on its own line between markers; LaunchCommandTest checks the argfile round trip with it. */
object PrintArgsMain {
    @JvmStatic
    fun main(args: Array<String>) {
        args.forEach { println("<$it>") }
    }
}
