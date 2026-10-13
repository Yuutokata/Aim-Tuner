package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.core.accel.AbStatus
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import de.yuuto.aimtune.games.GameProfiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccelRunTest {
    private val base = TrainerConfig(800.0, 55.0, checkNotNull(GameProfiles.load().find("valorant")), roundSeconds = 30, seed = 0)

    private fun newRun() = AccelRun(base, MetricWeights.DEFAULT, runKey = 1, seed = 3)

    private fun play(run: AccelRun, player: SimulatedPlayer) {
        val next = run.nextRoundConfig()
        run.recordRound(HeadlessRound.of(next.scenario, player, next.dpi, next.cm360, next.seed, next.roundSeconds, accel = next.accel))
    }

    @Test
    fun `calibration rounds are plain, at the base sensitivity and cover all click scenarios`() {
        val run = newRun()
        val configs = (0 until run.calibrationRounds).map {
            run.nextRoundConfig().also { _ -> play(run, SimulatedPlayer()) }
        }
        assertTrue(configs.all { it.accel == null && it.cm360 == 55.0 })
        assertEquals(AccelRun.CLICK_SCENARIOS.toSet(), configs.map { it.scenario }.toSet())
        assertEquals(configs.size, configs.map { it.seed }.toSet().size, "every round has its own targets")
        assertEquals(AccelPhase.ANALYSIS, run.phase)
        assertNotNull(run.fit)
    }

    @Test
    fun `a player who expects a rising gain gets a curve that the blind test confirms`() {
        val run = sampleAccelRun(base, AccelPhase.DONE)
        assertNotNull(run.candidate)
        assertEquals(AbStatus.ACCEL_BETTER, run.verdict?.status)
        assertEquals(AccelPhase.DONE, run.phase)
        assertTrue(run.testRoundsDone in 8..run.maxTestRounds && run.testRoundsDone % 2 == 0)
    }

    @Test
    fun `a flat player gets no curve and cannot start the test`() {
        val run = newRun()
        while (run.phase == AccelPhase.CALIBRATION) play(run, SimulatedPlayer())
        assertEquals(AccelPhase.ANALYSIS, run.phase)
        assertNull(run.candidate)
        assertFailsWith<IllegalStateException> { run.startTest() }
        assertFailsWith<IllegalStateException> { run.nextRoundConfig() }
    }

    @Test
    fun `both rounds of a test pair have the same targets and the curve round uses the factor`() {
        val run = sampleAccelRun(base, AccelPhase.ANALYSIS)
        val candidate = checkNotNull(run.candidate)
        run.startTest()
        repeat(6) { pair ->
            val first = run.nextRoundConfig().also { play(run, risingGainPlayer) }
            val second = run.nextRoundConfig().also { play(run, risingGainPlayer) }
            assertEquals(first.seed, second.seed, "pair $pair")
            assertEquals(first.scenario, second.scenario)
            assertTrue((first.accel == null) != (second.accel == null), "one plain, one with the curve")
            val accelerated = if (first.accel != null) first else second
            assertEquals(candidate.curve, accelerated.accel)
            assertEquals(55.0 / candidate.sensitivityFactor, accelerated.cm360, 1e-9)
            assertEquals(55.0, (if (first.accel == null) first else second).cm360, 1e-9)
            if (run.phase == AccelPhase.DONE) return
        }
    }

    @Test
    fun `the label never says which condition a test round has`() {
        val run = sampleAccelRun(base, AccelPhase.ANALYSIS)
        assertEquals("Calibration", AccelRun(base, MetricWeights.DEFAULT, 1, 3).label.substringBefore(' '))
        run.startTest()
        repeat(3) {
            assertTrue(run.label.startsWith("Test round"))
            assertTrue(!run.label.contains("curve", ignoreCase = true) && !run.label.contains("plain", ignoreCase = true))
            play(run, risingGainPlayer)
        }
    }

    @Test
    fun `tracking is never part of the acceleration rounds`() {
        assertTrue(ScenarioKind.TRACKING !in AccelRun.CLICK_SCENARIOS)
    }
}
