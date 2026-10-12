package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreMetric
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsStoreTest {
    @TempDir
    lateinit var dir: Path

    private fun store() = SettingsStore(dir.resolve("nested").resolve("settings.json"))

    @Test
    fun `missing file gives the defaults without creating anything`() {
        val s = store().load()
        assertEquals(AppSettings(), s)
        assertNull(s.dpi)
        assertEquals(45, s.roundSeconds)
        assertTrue(Files.notExists(dir.resolve("nested")))
    }

    @Test
    fun `settings round trip and parent directories are created`() {
        val store = store()
        val settings = AppSettings(
            language = "de", dpi = 1600.0, gameId = "valorant", inGameSensitivity = 0.35, aimStyle = AimStyle.MIXED,
            padWidthCm = 45.0, padHeightCm = 40.0, roundSeconds = 60,
            metricWeights = mapOf("ACCURACY" to 50.0, "TIME_TO_HIT" to 50.0),
        )
        store.save(settings)
        assertEquals(settings, store.load())
    }

    @Test
    fun `unknown keys from a newer version are ignored`() {
        val file = dir.resolve("s.json")
        Files.writeString(file, """{"version":1,"dpi":800.0,"somethingNew":{"a":1}}""")
        assertEquals(800.0, SettingsStore(file).load().dpi)
    }

    @Test
    fun `corrupt json is reported and not overwritten`() {
        val file = dir.resolve("s.json")
        Files.writeString(file, "{ not json")
        assertFailsWith<SettingsException> { SettingsStore(file).load() }
        assertEquals("{ not json", Files.readString(file))
    }

    @Test
    fun `invalid values are rejected on load and on save`() {
        val file = dir.resolve("s.json")
        Files.writeString(file, """{"dpi":-5.0}""")
        assertFailsWith<SettingsException> { SettingsStore(file).load() }
        assertFailsWith<SettingsException> { store().save(AppSettings(roundSeconds = 0)) }
        assertFailsWith<SettingsException> { store().save(AppSettings(metricWeights = mapOf("NOPE" to 1.0))) }
    }

    @Test
    fun `metric weights convert to core weights`() {
        val w: MetricWeights = AppSettings(metricWeights = mapOf("ACCURACY" to 3.0, "CONSISTENCY" to 1.0)).toMetricWeights()
        assertEquals(setOf(ScoreMetric.ACCURACY, ScoreMetric.CONSISTENCY), w.values.keys)
        assertEquals(MetricWeights.DEFAULT, AppSettings().toMetricWeights())
    }

    @Test
    fun `saving replaces the file without leaving temp files`() {
        val store = store()
        store.save(AppSettings(dpi = 800.0))
        store.save(AppSettings(dpi = 1600.0))
        assertEquals(1600.0, store.load().dpi)
        Files.list(dir.resolve("nested")).use { files -> assertEquals(listOf("settings.json"), files.map { it.fileName.toString() }.toList()) }
    }
}
