package de.yuuto.aimtune.core.accel

import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreMetric
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefineEngineTest {
    private val user = RawAccelProfile(
        "default", RawAccelArgs(RawAccelMode.CLASSIC, gain = true, inputOffset = 10.0, capX = 62.5, capY = 1.6, capMode = RawAccelCapMode.IN_OUT), 600.0, 800.0,
    )

    private fun engine(profile: RawAccelProfile = user, flash: Boolean = false) = RefineEngine(profile, 48.0, MetricWeights.DEFAULT, seed = 5, flash = flash)

    private val random = Random(1)

    /** Scores that favour a level factor near [bestLevel] on the level stage and are noise elsewhere. */
    private fun inputs(level: Double?, bestLevel: Double) = mapOf(
        ScoreMetric.ACCURACY to 0.7 - (if (level == null) 0.0 else 12.0 * (level - bestLevel) * (level - bestLevel)) + 0.002 * random.nextDouble(),
        ScoreMetric.TIME_TO_HIT to 600.0 + 4.0 * random.nextDouble(),
    )

    private fun playWarmup(e: RefineEngine) {
        repeat(e.warmupRounds) {
            val round = e.nextRound()
            assertEquals(user, round.profile, "the warm-up plays the imported profile")
            assertEquals(48.0, round.cm360, 1e-9, "at the game sensitivity, the output DPI factor is inside the driver model")
            e.record(inputs(null, 1.0))
        }
    }

    @Test
    fun `the run goes warm-up, three stages, test`() {
        val e = engine()
        assertEquals(listOf(RefineParameter.LEVEL, RefineParameter.HIGH_GAIN, RefineParameter.TRANSITION), e.stages)
        assertEquals(RefinePhase.WARMUP, e.phase)
        playWarmup(e)
        assertEquals(RefinePhase.SEARCH, e.phase)
        assertEquals(RefineParameter.LEVEL, e.stage)
        // level stage: the better the closer the played output DPI is to a level of 1.12 times the imported one
        while (e.phase == RefinePhase.SEARCH) {
            val round = e.nextRound()
            val level = round.profile.outputDpi / user.outputDpi
            e.record(inputs(level, 1.12))
        }
        assertEquals(RefinePhase.STAGE_RESULT, e.phase)
        val result = assertNotNull(e.currentResult)
        assertEquals(RefineParameter.LEVEL, result.parameter)
        assertEquals(12, result.rounds)
        assertTrue(result.best in RefineParameter.LEVEL.min..RefineParameter.LEVEL.max)
        e.continueToNextStage()
        assertEquals(RefinePhase.SEARCH, e.phase)
        assertEquals(RefineParameter.HIGH_GAIN, e.stage)
        e.finishStageNowAfter(8)
        assertEquals(RefinePhase.STAGE_RESULT, e.phase)
        e.goToTest()
        assertEquals(if (e.hasCandidate) RefinePhase.TEST else RefinePhase.DONE, e.phase)
    }

    private fun RefineEngine.finishStageNowAfter(rounds: Int) {
        repeat(rounds) {
            nextRound()
            record(inputs(null, 1.0))
        }
        finishStageNow()
    }

    @Test
    fun `a stage cannot end before its minimum`() {
        val e = engine()
        playWarmup(e)
        repeat(RefineSearch.MIN_ROUNDS - 1) {
            e.nextRound()
            e.record(inputs(null, 1.0))
        }
        assertFailsWith<IllegalStateException> { e.finishStageNow() }
    }

    @Test
    fun `a stage that finds no clear change leaves the profile as it was and the run ends without a test`() {
        val e = engine(RawAccelProfile("flat", RawAccelArgs(RawAccelMode.NOACCEL), 800.0, 800.0))
        assertEquals(listOf(RefineParameter.LEVEL), e.stages)
        e.skipWarmup()
        while (e.phase == RefinePhase.SEARCH) {
            e.nextRound()
            e.record(mapOf(ScoreMetric.ACCURACY to 0.7 + 0.001 * random.nextDouble(), ScoreMetric.TIME_TO_HIT to 600.0))
        }
        assertEquals(RefinePhase.STAGE_RESULT, e.phase)
        if (!assertNotNull(e.currentResult).excludesNeutral) {
            e.continueToNextStage()
            assertEquals(RefinePhase.DONE, e.phase)
            assertNull(e.verdict)
            assertEquals(e.profile, e.candidate)
        }
    }

    @Test
    fun `the test plays the imported profile against the candidate in the same scenario with the same targets`() {
        val e = RefineEngine(user, 48.0, MetricWeights.DEFAULT, seed = 5, stageRounds = 30)
        e.skipWarmup()
        // force a candidate by running the level stage with a strong preference
        while (e.phase == RefinePhase.SEARCH) {
            val round = e.nextRound()
            e.record(inputs(round.profile.outputDpi / user.outputDpi, 1.17))
        }
        val level = e.currentResult
        e.goToTest()
        assertEquals(RefinePhase.TEST, e.phase, "a clear level preference gives a candidate to test: $level")
        val first = e.nextRound()
        e.record(inputs(null, 1.0))
        val second = e.nextRound()
        assertEquals(first.scenario, second.scenario)
        assertEquals(first.seed, second.seed)
        val curves = setOf(first.profile, second.profile)
        assertEquals(2, curves.size, "one arm is the imported profile, the other the refined one")
        assertTrue(user in curves)
        assertEquals(setOf(48.0), setOf(first.cm360, second.cm360), "both arms play the game sensitivity")
    }

    @Test
    fun `flash adds flash rounds to the warm-up and the search`() {
        val e = engine(flash = true)
        val kinds = List(6) { e.nextRound().also { _ -> e.record(inputs(null, 1.0)) }.scenario }
        assertEquals(AccelScenarios.WITH_FLASH, kinds)
        assertTrue(ScenarioKind.FLASH in kinds)
    }
}
