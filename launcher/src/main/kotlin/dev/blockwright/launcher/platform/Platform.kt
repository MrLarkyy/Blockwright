package dev.blockwright.launcher.platform

/**
 * The host as version JSON rules see it: [osName] is `windows`, `osx` or `linux`, [arch] is `x86_64`, `arm64` or
 * `x86` (32-bit); anything else passes through lower-cased and simply matches no rule.
 */
data class Platform(
    val osName: String,
    val arch: String,
    val osVersion: String,
) {
    /** The `${arch}` substitution of legacy natives classifiers: the JVM bitness. */
    val bitness: String get() = if (arch == X86) "32" else "64"

    companion object {
        const val WINDOWS = "windows"
        const val OSX = "osx"
        const val LINUX = "linux"
        const val X86_64 = "x86_64"
        const val ARM64 = "arm64"
        const val X86 = "x86"

        fun current(): Platform =
            of(System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version"))

        fun of(osName: String, osArch: String, osVersion: String): Platform =
            Platform(mapOs(osName), mapArch(osArch), osVersion)

        private fun mapOs(osName: String): String {
            val name = osName.lowercase()
            return when {
                name.startsWith("windows") -> WINDOWS
                name.startsWith("mac") || name.startsWith("darwin") -> OSX
                name.startsWith("linux") -> LINUX
                else -> name
            }
        }

        private fun mapArch(osArch: String): String =
            when (val arch = osArch.lowercase()) {
                "amd64", "x86_64", "x64" -> X86_64
                "aarch64", "arm64" -> ARM64
                "x86", "i386", "i486", "i586", "i686" -> X86
                else -> arch
            }
    }
}
