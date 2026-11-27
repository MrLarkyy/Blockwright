package dev.blockwright.orchestrator.bench

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BudgetsLoaderTest {
    private fun profile(body: String) = """{"schemaVersion":1,"profiles":{"p":$body}}"""

    private fun failure(text: String) = assertThrows<BenchConfigException> { BudgetsLoader.parse(text) }.message!!

    @Test
    fun parsesAValidFile() {
        val file = BudgetsLoader.parse(profile("""{"required":true,"clients":2,"warmRuns":1,"budgets":{"pssMiB":10}}"""))
        val p = file.profiles.getValue("p")
        assertEquals(2, p.clients)
        assertEquals(10.0, p.budgets["pssMiB"])
    }

    @Test
    fun unknownMetricKeyIsNamed() {
        assertTrue("coldStartMS" in failure(profile("""{"budgets":{"coldStartMS":10}}""")))
    }

    @Test
    fun nonPositiveBudgetIsNamed() {
        assertTrue("pssMiB" in failure(profile("""{"budgets":{"pssMiB":0}}""")))
    }

    @Test
    fun zeroClientsIsRejected() {
        assertTrue("clients" in failure(profile("""{"clients":0}""")))
    }

    @Test
    fun zeroScreenshotsSkipsTheMetricButNegativeIsRejected() {
        assertEquals(0, BudgetsLoader.parse(profile("""{"screenshots":0}""")).profiles.getValue("p").screenshots)
        assertTrue("screenshots" in failure(profile("""{"screenshots":-1}""")))
    }

    @Test
    fun wrongSchemaVersionIsRejected() {
        assertTrue("schemaVersion" in failure("""{"schemaVersion":2,"profiles":{}}"""))
    }

    @Test
    fun malformedJsonIsAConfigError() {
        failure("{not json")
    }

    @Test
    fun warmBudgetWithoutAWarmSampleNamesTheKey() {
        val message = failure(profile("""{"clients":1,"warmRuns":0,"budgets":{"warmStartMs":10}}"""))
        assertTrue("warmStartMs" in message)
    }

    @Test
    fun missingFileIsAConfigError() {
        assertThrows<BenchConfigException> { BudgetsLoader.load(Path.of("does-not-exist.json")) }
    }

    @Test
    fun theVersionedRepositoryFileLoads() {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null && !dir.resolve("perf/budgets.json").toFile().isFile) dir = dir.parent
        val file = BudgetsLoader.load(requireNotNull(dir) { "perf/budgets.json not found" }.resolve("perf/budgets.json"))
        assertTrue(file.profiles.getValue("stub").required)
        val logic = file.profiles.getValue("logic")
        assertEquals(false, logic.required)
        assertEquals(0, logic.screenshots)
        assertTrue(listOf("coldStartMs", "warmStartMs", "pssMiB").all { it in logic.budgets }, logic.budgets.toString())
    }
}
