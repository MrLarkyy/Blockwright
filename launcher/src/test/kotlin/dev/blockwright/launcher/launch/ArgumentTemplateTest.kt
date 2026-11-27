package dev.blockwright.launcher.launch

import dev.blockwright.launcher.platform.Platform
import dev.blockwright.launcher.platform.RuleEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArgumentTemplateTest {
    private val linux = ArgumentTemplate(RuleEvaluator(Platform("linux", "x86_64", "6.8")))
    private val details = LaunchFixtures.details()
    private val values = mapOf(
        "auth_player_name" to "bwlogic", "version_name" to "1.0-launch", "game_directory" to "/run/game",
        "assets_root" to "/run/assets", "assets_index_name" to "7", "auth_uuid" to "u", "auth_access_token" to "0",
        "clientid" to "0", "auth_xuid" to "0", "version_type" to "blockwright", "quickPlayMultiplayer" to "127.0.0.1:25565",
        "natives_directory" to "/natives", "launcher_name" to "blockwright", "launcher_version" to "0.1.0", "classpath" to "a.jar:b.jar",
    )
    private val quickPlay = mapOf("is_quick_play_multiplayer" to true)

    @Test
    fun gameArgumentsIncludeQuickPlayButNeverDemoResolutionOrQuickPlayLog() {
        val game = linux.expand(details, ArgumentTemplate.Section.GAME, values, quickPlay)
        assertEquals(listOf("--quickPlayMultiplayer", "127.0.0.1:25565"), game.takeLast(2))
        assertEquals("bwlogic", game[game.indexOf("--username") + 1])
        assertEquals("0", game[game.indexOf("--accessToken") + 1])
        assertFalse("--demo" in game)
        assertFalse("--width" in game)
        assertFalse("--quickPlayPath" in game)
    }

    @Test
    fun jvmArgumentsFollowOsRulesAndSubstitutePlaceholders() {
        val jvm = linux.expand(details, ArgumentTemplate.Section.JVM, values, quickPlay)
        assertEquals(listOf("-Djava.library.path=/natives/java", "-Dminecraft.launcher.brand=blockwright", "-Dminecraft.launcher.version=0.1.0", "-cp", "a.jar:b.jar"), jvm)
        val windows = ArgumentTemplate(RuleEvaluator(Platform("windows", "x86_64", "10.0"))).expand(details, ArgumentTemplate.Section.JVM, values, quickPlay)
        assertEquals("-XX:HeapDumpPath=test.heapdump", windows.first())
        val mac = ArgumentTemplate(RuleEvaluator(Platform("osx", "arm64", "15"))).expand(details, ArgumentTemplate.Section.JVM, values, quickPlay)
        assertEquals("-XstartOnFirstThread", mac.first())
    }

    @Test
    fun anUnknownPlaceholderFails() {
        val error = assertThrows<LaunchException> { linux.expand(details, ArgumentTemplate.Section.GAME, values - "auth_xuid", quickPlay) }
        assertTrue("\${auth_xuid}" in error.message!!, error.message)
    }

    @Test
    fun aPlaceholderOfAnExcludedArgumentIsNotNeeded() {
        val game = linux.expand(details, ArgumentTemplate.Section.GAME, values - "quickPlayMultiplayer", emptyMap())
        assertFalse("--quickPlayMultiplayer" in game)
    }

    @Test
    fun legacyVersionsAreRefused() {
        val legacy = details.copy(arguments = null, minecraftArguments = "--username \${auth_player_name}")
        val error = assertThrows<LaunchException> { linux.expand(legacy, ArgumentTemplate.Section.GAME, values, quickPlay) }
        assertTrue("unsupported version" in error.message!!, error.message)
        assertThrows<LaunchException> { linux.expand(details.copy(arguments = null), ArgumentTemplate.Section.GAME, values, quickPlay) }
    }
}
