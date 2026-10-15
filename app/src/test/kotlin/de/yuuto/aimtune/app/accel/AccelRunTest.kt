package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.core.accel.AbStatus
import de.yuuto.aimtune.core.accel.trialAccel
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
    fun `a player who expects a falling gain gets a curve that the blind test confirms`() {
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
    fun `a player can test a mild trial curve although the analysis found none`() {
        val run = newRun()
        while (run.phase == AccelPhase.CALIBRATION) play(run, SimulatedPlayer())
        assertNull(run.candidate)
        run.startTrial(rising = true)
        assertEquals(AccelPhase.TEST, run.phase)
        assertTrue(run.isTrial)
        val trial = checkNotNull(run.candidate)
        assertEquals(1.0, trial.sensitivityFactor, "the low-speed sensitivity stays as it is")
        assertTrue(trial.curve.multiplier(30.0) in 1.1..1.16)
        val configs = (0 until 2).map { run.nextRoundConfig().also { _ -> play(run, SimulatedPlayer()) } }
        assertEquals(1, configs.count { it.accel != null }, "one round of a pair has the curve")
        assertTrue(configs.all { it.cm360 == 55.0 })
    }

    @Test
    fun `a falling trial curve lowers the gain with speed and a found curve cannot be replaced by a trial`() {
        val falling = trialAccel(rising = false)
        assertTrue(falling.curve.multiplier(30.0) < 0.9 && falling.gainRange in 1.1..1.2)
        val run = sampleAccelRun(base, AccelPhase.ANALYSIS)
        assertFailsWith<IllegalStateException> { run.startTrial() }
    }

    @Test
    fun `both rounds of a test pair have the same targets and the curve round uses the factor`() {
        val run = sampleAccelRun(base, AccelPhase.ANALYSIS)
        val candidate = checkNotNull(run.candidate)
        run.startTest()
        repeat(6) { pair ->
            val first = run.nextRoundConfig().also { play(run, fallingGainPlayer) }
            val second = run.nextRoundConfig().also { play(run, fallingGainPlayer) }
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
        assertEquals("accel.label.calibration", AccelRun(base, MetricWeights.DEFAULT, 1, 3).label.key)
        run.startTest()
        repeat(3) {
            assertEquals("accel.label.test", run.label.key)
            assertEquals(1, run.label.args.size, "only the number of the round")
            play(run, fallingGainPlayer)
        }
    }

    @Test
    fun `the analysis can start early from five rounds and says if they were enough`() {
        val run = newRun()
        repeat(AccelRun.MIN_CALIBRATION_ROUNDS - 1) { play(run, fallingGainPlayer) }
        assertTrue(!run.canAnalyseNow)
        assertFailsWith<IllegalStateException> { run.analyseNow() }
        play(run, fallingGainPlayer)
        assertTrue(run.canAnalyseNow)
        run.analyseNow()
        assertEquals(AccelPhase.ANALYSIS, run.phase)
        assertEquals(AccelRun.MIN_CALIBRATION_ROUNDS, run.calibrationDone)
        assertNotNull(run.fit)
        assertTrue(!run.canAnalyseNow, "no early analysis once the calibration is over")
    }

    @Test
    fun `tracking is never part of the acceleration rounds`() {
        assertTrue(ScenarioKind.TRACKING !in AccelRun.CLICK_SCENARIOS)
    }
}
