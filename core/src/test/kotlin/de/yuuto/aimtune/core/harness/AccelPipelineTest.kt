package de.yuuto.aimtune.core.harness

import de.yuuto.aimtune.core.accel.AbCondition
import de.yuuto.aimtune.core.accel.AbOutcome
import de.yuuto.aimtune.core.accel.AbStatus
import de.yuuto.aimtune.core.accel.AbVerdict
import de.yuuto.aimtune.core.accel.AccelAbPlan
import de.yuuto.aimtune.core.accel.AccelAnalysis
import de.yuuto.aimtune.core.accel.AccelFitResult
import de.yuuto.aimtune.core.accel.DEFAULT_ALPHA_PER_LOOK
import de.yuuto.aimtune.core.accel.evaluateAb
import de.yuuto.aimtune.core.accel.fitAccel
import de.yuuto.aimtune.core.accel.movementSamples
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.scoreInputs
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertTrue

/** What the acceleration pipeline did for one simulated player. */
class AccelRun(val fit: AccelFitResult, val verdict: AbVerdict?)

private val CLICK_KINDS = listOf(ScenarioKind.FLICK, ScenarioKind.SWITCHING, ScenarioKind.MICRO)

/**
 * The whole acceleration flow against a simulated player: rounds without acceleration at the given
 * sensitivity, the analysis and fit, and, if a curve is recommended, the blind A/B test (plain at
 * [baseCm360] against the curve at [baseCm360] divided by its sensitivity factor) until it decides.
 */
fun runAccelPipeline(player: SimulatedPlayer, baseCm360: Double, seed: Long, analysisRounds: Int = 15, roundSeconds: Int = 45): AccelRun {
    val metrics = (0 until analysisRounds).map {
        computeRoundMetrics(HeadlessRound.of(CLICK_KINDS[it % CLICK_KINDS.size], player, TEST_DPI, baseCm360, seed * 1_000 + it, roundSeconds))
    }
    val fit = fitAccel(AccelAnalysis(movementSamples(metrics, TEST_DPI)))
    val recommended = fit.recommended ?: return AccelRun(fit, null)

    val plan = AccelAbPlan(CLICK_KINDS, seed)
    val outcomes = mutableListOf<AbOutcome>()
    var verdict: AbVerdict? = null
    for (index in 0 until 2 * plan.maxPairs) {
        val round = plan.round(index)
        val accelerated = round.condition == AbCondition.ACCEL
        val recording = HeadlessRound.of(
            round.scenario, player, TEST_DPI,
            if (accelerated) baseCm360 / recommended.sensitivityFactor else baseCm360,
            round.seed, roundSeconds, sessionOffsetNanos = index * roundSeconds * 1_000_000_000L,
            accel = if (accelerated) recommended.curve else null,
        )
        outcomes += AbOutcome(round.pair, round.condition, computeRoundMetrics(recording).scoreInputs())
        if (index % 2 == 1) {
            verdict = evaluateAb(outcomes, MetricWeights.DEFAULT)
            if (verdict.status != AbStatus.NEED_MORE_ROUNDS) break
        }
    }
    return AccelRun(fit, verdict)
}

class AccelPipelineTest {
    private val seeds = 1L..10L

    /** Expects a gain that rises from 1 toward 1.5 with the hand speed, which Raw Accel's natural curve can deliver. */
    private val wantsRisingGain = SimulatedPlayer(gainPreference = { v -> 1.0 + 0.5 * (1.0 - exp(-v / 6.0)) })

    private fun summary(label: String, runs: List<AccelRun>) = buildString {
        val offered = runs.filter { it.fit.recommended != null }
        appendLine("$label: curve offered in ${offered.size} of ${runs.size} runs")
        for ((i, run) in runs.withIndex()) {
            val fit = run.fit.recommended
            val verdict = run.verdict
            appendLine(
                "  seed %2d: %-50s %s".format(
                    seeds.first() + i,
                    fit?.let { "%s, factor %.2f, range x%.2f, BIC gain %.0f".format(it.family, it.sensitivityFactor, it.gainRange, it.bicGain) } ?: run.fit.reason.take(50),
                    verdict?.let { "A/B %s after %d pairs, difference %+.1f points (interval %+.1f to %+.1f)".format(it.status, it.pairs, it.meanDifference, it.lowerBound, it.upperBound) } ?: "",
                ),
            )
        }
    }

    @Test
    fun `a player who wants a rising gain is offered a curve that the blind test confirms, a player who does not is left alone`() {
        val wants = seeds.map { runAccelPipeline(wantsRisingGain, 55.0, it) }
        val flat = seeds.map { runAccelPipeline(SimulatedPlayer(), 55.0, it) }
        publishReport("accel-pipeline.txt", summary("Expects a rising gain", wants) + summary("Flat expectation", flat))

        val wantsOffered = wants.filter { it.fit.recommended != null }
        assertTrue(wantsOffered.size >= 8, "curve offered to ${wantsOffered.size} of ${wants.size}")
        assertTrue(wantsOffered.count { it.verdict?.status == AbStatus.ACCEL_BETTER } >= wantsOffered.size * 0.6, "confirmed by the blind test")
        assertTrue(wants.none { it.verdict?.status == AbStatus.ACCEL_WORSE })

        // A flat player may be offered a curve now and then, but the blind test must not confirm it.
        assertTrue(flat.count { it.fit.recommended != null } <= 2, "flat players offered a curve")
        assertTrue(flat.none { it.verdict?.status == AbStatus.ACCEL_BETTER }, "flat player 'improved' by a curve")
    }

    /** The A/B decision on its own: "curve" and "plain" rounds that are really the same must rarely come out as a difference. */
    @Test
    fun `the blind test rarely sees a difference where there is none and sees a harmful curve`() {
        val player = SimulatedPlayer()
        val plan = { seed: Long -> AccelAbPlan(CLICK_KINDS, seed) }
        fun verdictFor(seed: Long, accel: de.yuuto.aimtune.core.accel.AccelCurve?, factor: Double): AbVerdict {
            val outcomes = mutableListOf<AbOutcome>()
            var verdict = evaluateAb(emptyList(), MetricWeights.DEFAULT)
            val abPlan = plan(seed)
            for (index in 0 until 2 * abPlan.maxPairs) {
                val round = abPlan.round(index)
                val accelerated = round.condition == AbCondition.ACCEL
                // No shared seed between the two members of a pair: a fair null comparison.
                val roundSeed = round.seed + if (accelerated) 5_000 else 0
                val recording = HeadlessRound.of(
                    round.scenario, player, TEST_DPI, if (accelerated) 55.0 / factor else 55.0, roundSeed, 45,
                    accel = if (accelerated) accel else null,
                )
                outcomes += AbOutcome(round.pair, round.condition, computeRoundMetrics(recording).scoreInputs())
                if (index % 2 == 1) {
                    verdict = evaluateAb(outcomes, MetricWeights.DEFAULT)
                    if (verdict.status != AbStatus.NEED_MORE_ROUNDS) break
                }
            }
            return verdict
        }

        val nothing = (1L..30L).map { verdictFor(it, accel = null, factor = 1.0) }
        val falseBetter = nothing.count { it.status == AbStatus.ACCEL_BETTER }
        val falseWorse = nothing.count { it.status == AbStatus.ACCEL_WORSE }
        val harmful = (1L..10L).map { verdictFor(it, accel = de.yuuto.aimtune.core.accel.NaturalCurve(0.25, 2.2), factor = 1.0) }
        publishReport(
            "accel-ab-calibration.txt",
            "Blind A/B with no real difference (30 runs, up to 10 pairs, per-look level $DEFAULT_ALPHA_PER_LOOK): curve better in $falseBetter, worse in $falseWorse, " +
                "undecided in ${nothing.count { it.status == AbStatus.NO_DIFFERENCE }}\n" +
                "Harmful curve on a flat player (10 runs): worse in ${harmful.count { it.status == AbStatus.ACCEL_WORSE }}, better in ${harmful.count { it.status == AbStatus.ACCEL_BETTER }}, " +
                "pairs needed ${harmful.map { it.pairs }}\n",
        )
        // Chance alone should give well under a tenth of the runs in either direction.
        assertTrue(falseBetter <= 3 && falseWorse <= 3, "no-difference runs came out better $falseBetter, worse $falseWorse of 30")

        // A curve that gets in this flat player's way (strongly rising gain) makes the rounds worse.
        assertTrue(harmful.count { it.status == AbStatus.ACCEL_WORSE } >= 7, "harmful curve judged worse in ${harmful.count { it.status == AbStatus.ACCEL_WORSE }} of 10")
        assertTrue(harmful.none { it.status == AbStatus.ACCEL_BETTER })
    }
}
