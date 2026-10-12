package de.yuuto.aimtune.app

import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.math.Vec3
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.Target
import de.yuuto.aimtune.core.scenario.TargetEvent
import de.yuuto.aimtune.storage.Database
import de.yuuto.aimtune.storage.RoundRepository
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundProcessorTest {
    @TempDir
    lateinit var dir: Path

    private val ms = 1_000_000L

    private fun recording(seed: Long) = RoundRecording(
        "flick", seed, 800.0, 40.0, 0, 5_000 * ms,
        listOf(MouseEvent(100 * ms, 700, 0)),
        listOf(TargetEvent.Spawned(0, Target(0, Vec3(0.0, 0.0, -10.0), 0.25)), TargetEvent.Hit(300 * ms, 0)),
    )

    @Test
    fun `rounds are analysed and stored in one session`() {
        val file = dir.resolve("a.db")
        val processor = RoundProcessor(Database.open(file), dpi = 800.0)
        processor.submit(recording(1), startedAtMs = 10, blind = false)
        processor.submit(recording(2), startedAtMs = 20, blind = false)
        processor.close()

        val report = assertIs<RoundReport.Done>(processor.report)
        assertNull(report.saveError)
        assertEquals(1, report.metrics.hits)

        Database.open(file).use { db ->
            val repo = RoundRepository(db)
            assertEquals(1, repo.sessions().size)
            assertEquals(listOf(1L, 2L), repo.rounds().map { it.seed })
        }
    }

    @Test
    fun `without a database the round is still analysed`() {
        val processor = RoundProcessor(database = null, dpi = 800.0)
        processor.submit(recording(1), startedAtMs = 10, blind = false)
        processor.close()
        val report = assertIs<RoundReport.Done>(processor.report)
        assertTrue(report.saveError != null)
        assertEquals(1, report.metrics.hits)
    }
}
