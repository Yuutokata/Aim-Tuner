package de.yuuto.aimtune.core.harness.review

import de.yuuto.aimtune.core.harness.TEST_DPI
import de.yuuto.aimtune.core.harness.geometricGrid
import de.yuuto.aimtune.core.harness.parabolicOptimum
import de.yuuto.aimtune.core.metrics.RoundMetrics
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.scenario.TrainerGeometry
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreCalculator
import de.yuuto.aimtune.core.score.ScoreMetric
import de.yuuto.aimtune.core.score.scoreInputs
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test

/**
 * Flash turn-away experiments (docs/ALGORITHMS.md "Flash turn-away"). Gated like the other review
 * experiments: set AIMTUNE_REVIEW; AIMTUNE_RUNS scales the number of simulated rounds or players per row.
 */
@EnabledIfEnvironmentVariable(named = "AIMTUNE_REVIEW", matches = ".+")
class FlashExperimentsTest {
    private val runs = (System.getenv("AIMTUNE_RUNS") ?: "30").toInt()
    private val geometry = TrainerGeometry.DEFAULT

    private fun round(kind: ScenarioKind, player: SimulatedPlayer, cm360: Double, seed: Long, offset: Long = 0L): RoundMetrics =
        computeRoundMetrics(HeadlessRound.of(kind, player, TEST_DPI, cm360, seed, 45, offset, geometry = geometry))

    /**
     * Flash rounds across the sensitivity range: what each metric does on its own and where the combined
     * score peaks. A score that rewards the highest sensitivity would peak at the edge of the grid.
     */
    @Test
    fun flashSweep() {
        val out = StringBuilder()
        val grid = geometricGrid(20.0, 160.0, 9)
        for (optimum in listOf(35.0, 50.0, 70.0)) {
            val player = SimulatedPlayer.forHeadSizedTargets(optimum)
            val metrics = grid.mapIndexed { gi, cm -> parallelList(runs) { r -> round(ScenarioKind.FLASH, player, cm, 9_000L + gi * 1_000 + r, r * 45_000_000_000L) } }
            val inputs = metrics.map { group -> group.map { it.scoreInputs() } }
            out.appendLine("== player optimum $optimum cm/360 (head-sized targets), $runs flash rounds per sensitivity ==")
            out.appendLine("cm/360   dodge   turn ms   first-shot acc   time to hit ms   flashes/round   score(all)   score(dodge only)   score(return only)")
            val all = meanScoresOf(inputs, MetricWeights(mapOf(ScoreMetric.FLASH_DODGE to 15.0, ScoreMetric.ACCURACY to 40.0, ScoreMetric.TIME_TO_HIT to 30.0)))
            val dodgeOnly = meanScoresOf(inputs, MetricWeights(mapOf(ScoreMetric.FLASH_DODGE to 1.0)))
            val returnOnly = meanScoresOf(inputs, MetricWeights(mapOf(ScoreMetric.ACCURACY to 40.0, ScoreMetric.TIME_TO_HIT to 30.0)))
            for (gi in grid.indices) {
                val g = metrics[gi]
                out.appendLine(
                    "%6.1f   %4.0f%%   %7.0f   %13.0f%%   %14.0f   %13.1f   %10.1f   %17.1f   %18.1f".format(
                        grid[gi], 100 * g.mapNotNull { it.flashDodgeRate }.average(), g.mapNotNull { it.flashMeanTurnMs }.average(),
                        100 * g.mapNotNull { it.firstShotAccuracy }.average(), g.mapNotNull { it.meanTimeToHitMs }.average(),
                        g.map { it.hits }.average(), all[gi], dodgeOnly[gi], returnOnly[gi],
                    ),
                )
            }
            out.appendLine("optimum of the combined score %.1f, dodge only %.1f, return only %.1f cm/360 (grid %.0f-%.0f)".format(parabolicOptimum(grid, all), parabolicOptimum(grid, dodgeOnly), parabolicOptimum(grid, returnOnly), grid.first(), grid.last()))
            out.appendLine()
        }
        write("flash-sweep.txt", out.toString())
    }

    private fun meanScoresOf(inputs: List<List<Map<ScoreMetric, Double>>>, weights: MetricWeights): List<Double> {
        val scores = ScoreCalculator(weights).scoreRounds(inputs.flatten())
        var i = 0
        return inputs.map { group -> group.indices.map { scores[i++] }.filterNotNull().average() }
    }
}
