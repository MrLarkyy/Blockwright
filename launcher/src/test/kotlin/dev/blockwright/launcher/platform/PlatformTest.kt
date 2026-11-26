package dev.blockwright.launcher.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PlatformTest {
    @ParameterizedTest
    @CsvSource(
        "Windows 11, amd64, windows, x86_64, 64",
        "Windows 10, x86, windows, x86, 32",
        "Mac OS X, aarch64, osx, arm64, 64",
        "Mac OS X, x86_64, osx, x86_64, 64",
        "Linux, aarch64, linux, arm64, 64",
        "Linux, i686, linux, x86, 32",
        "FreeBSD, riscv64, freebsd, riscv64, 64",
    )
    fun mapsJvmPropertiesToRuleNames(osName: String, osArch: String, os: String, arch: String, bitness: String) {
        val platform = Platform.of(osName, osArch, "1.0")
        assertEquals(os, platform.osName)
        assertEquals(arch, platform.arch)
        assertEquals(bitness, platform.bitness)
        assertEquals("1.0", platform.osVersion)
    }

    @Test
    fun currentReadsTheRunningJvm() {
        val current = Platform.current()
        assertEquals(System.getProperty("os.version"), current.osVersion)
        assertEquals(Platform.of(System.getProperty("os.name"), System.getProperty("os.arch"), "x").osName, current.osName)
    }
}
