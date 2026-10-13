package de.yuuto.aimtune.app.harness

import de.yuuto.aimtune.app.Phase
import de.yuuto.aimtune.app.Trainer
import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.scenario.TargetEvent
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.ManualClock
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import de.yuuto.aimtune.games.GameProfiles
import de.yuuto.aimtune.input.ReplayMouseSource
import de.yuuto.aimtune.input.SimulatedMouseSource
import de.yuuto.aimtune.storage.RoundLogFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val NANOS_PER_MS = 1_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val ROUND_SECONDS = 20
private const val CLOCK_ORIGIN = 5 * NANOS_PER_SECOND

/** Runs the real [Trainer] with the harness sources on a virtual clock. */
class TrainerHarnessTest {
    private val dpi = 800.0
    private val cm360 = 45.0
    private val player = SimulatedPlayer()

    private fun config(seed: Long) = TrainerConfig(
        dpi = dpi, cm360 = cm360, profile = checkNotNull(GameProfiles.load().find("valorant")), roundSeconds = ROUND_SECONDS, seed = seed,
    )

    private fun playWithSimulatedSource(seed: Long, kind: ScenarioKind = ScenarioKind.FLICK): RoundRecording {
        val clock = ManualClock(CLOCK_ORIGIN)
        var finished: RoundRecording? = null
        lateinit var trainer: Trainer
        val source = SimulatedMouseSource(player, dpi, cm360, seed, clock, kind) { trainer.activeTargets }
        trainer = Trainer(config(seed).copy(scenario = kind), source) { finished = it }
        source.start()
        source.beginRound()
        trainer.startRound()
        while (trainer.phase == Phase.RUNNING) {
            clock.advance(NANOS_PER_MS)
            source.tick()
            trainer.update()
            if (trainer.phase == Phase.RUNNING && source.consumeClick()) trainer.fire()
        }
        return assertNotNull(finished)
    }

    private fun replay(recording: RoundRecording, seed: Long): RoundRecording {
        val clock = ManualClock(CLOCK_ORIGIN)
        var finished: RoundRecording? = null
        val source = ReplayMouseSource(recording, clock)
        val trainer = Trainer(config(seed), source) { finished = it }
        source.start()
        source.anchor()
        trainer.startRound()
        while (trainer.phase == Phase.RUNNING) {
            clock.advance(NANOS_PER_MS)
            trainer.update()
            repeat(source.consumeShots()) { if (trainer.phase == Phase.RUNNING) trainer.fire() }
        }
        return assertNotNull(finished)
    }

    @Test
    fun `the trainer driven by the simulated source records exactly what the headless runner records`() {
        for (seed in listOf(1L, 2L, 3L)) {
            assertEquals(HeadlessRound.flick(player, dpi, cm360, seed, ROUND_SECONDS), playWithSimulatedSource(seed), "seed $seed")
        }
    }

    @Test
    fun `every scenario recorded by the trainer equals the headless runner`() {
        for (kind in ScenarioKind.entries) {
            assertEquals(HeadlessRound.of(kind, player, dpi, cm360, 5, ROUND_SECONDS), playWithSimulatedSource(5, kind), kind.id)
        }
    }

    @Test
    fun `replaying a saved log through the trainer reproduces the round`() {
        val original = playWithSimulatedSource(4)
        val file = Files.createTempDirectory("replay").resolve("round.json")
        RoundLogFile.write(original, file)

        val replayed = replay(RoundLogFile.read(file), seed = 4)

        assertTrue(original.targetEvents.any { it is TargetEvent.Hit })
        assertEquals(original.mouseEvents.map { it.dx to it.dy }, replayed.mouseEvents.map { it.dx to it.dy })
        assertEquals(
            original.targetEvents.map { it.timeNanos - original.startNanos },
            replayed.targetEvents.map { it.timeNanos - replayed.startNanos },
        )
        assertEquals(original.targetEvents.map { it::class }, replayed.targetEvents.map { it::class })
    }
}
