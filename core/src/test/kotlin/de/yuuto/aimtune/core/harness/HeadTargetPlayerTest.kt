package de.yuuto.aimtune.core.harness

import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.scenario.FlickConfig
import de.yuuto.aimtune.core.scenario.FlickScenario
import de.yuuto.aimtune.core.scenario.TrainerGeometry
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreCalculator
import de.yuuto.aimtune.core.score.scoreInputs
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import java.util.stream.IntStream
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the head-target preset: with the profile's small targets its optimum lies where people sit, not at 150 cm/360. */
class HeadTargetPlayerTest {
    @Test
    fun `the head-target hand has its optimum near the requested value`() {
        val player = SimulatedPlayer.forHeadSizedTargets(50.0)
        val grid = geometricGrid(25.0, 100.0, 7)
        val roundsEach = 16
        val inputs = grid.map { cm ->
            val rows = arrayOfNulls<Map<de.yuuto.aimtune.core.score.ScoreMetric, Double>>(roundsEach)
            IntStream.range(0, roundsEach).parallel().forEach { r ->
                val seed = 7_000L + r
                val scenario = FlickScenario(FlickConfig(45_000_000_000L, geometry = TrainerGeometry.DEFAULT), seed)
                val recording = HeadlessRound.run(player, TEST_DPI, cm, seed, 45, (r * 45_000_000_000L), scenario)
                rows[r] = computeRoundMetrics(recording).scoreInputs()
            }
            rows.map { checkNotNull(it) }
        }
        val scores = ScoreCalculator(MetricWeights.DEFAULT).scoreRounds(inputs.flatten())
        var i = 0
        val means = inputs.map { group -> group.indices.map { scores[i++] }.filterNotNull().average() }
        val optimum = parabolicOptimum(grid, means)
        assertTrue(optimum in 35.0..70.0, "optimum $optimum cm/360, means $means")
    }
}
