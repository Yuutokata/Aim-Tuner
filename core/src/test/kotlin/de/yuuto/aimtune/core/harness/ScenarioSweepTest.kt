package de.yuuto.aimtune.core.harness

import de.yuuto.aimtune.core.metrics.RoundMetrics
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import kotlin.test.Test

/** Prints, per scenario, how the simulated player does across the search range (exploration and report). */
class ScenarioSweepTest {
    private val grid = geometricGrid(20.0, 140.0, 13)
    private val roundsEach = 12

    private fun rounds(kind: ScenarioKind, cm: Double, gi: Int): List<RoundMetrics> = List(roundsEach) { r ->
        val seed = 77L * 1_003 + gi * 1_009L + r
        computeRoundMetrics(HeadlessRound.of(kind, SimulatedPlayer(), TEST_DPI, cm, seed, 45, (gi * roundsEach + r) * 45_000_000_000L))
    }

    @Test
    fun `report how every scenario responds to sensitivity`() {
        val report = buildString {
            for (kind in ScenarioKind.entries) {
                val means = meanScores(SimulatedPlayer(), grid, roundsEach, seed = 77, kind = kind)
                appendLine("== ${kind.id}: score optimum ${"%.1f".format(parabolicOptimum(grid, means))} cm/360")
                appendLine("%8s %7s %7s %9s %9s %9s".format("cm/360", "score", "acc", "tth ms", "spread", "on-target"))
                grid.forEachIndexed { gi, cm ->
                    val ms = rounds(kind, cm, gi)
                    fun avg(f: (RoundMetrics) -> Double?) = ms.mapNotNull(f).average()
                    appendLine(
                        "%8.1f %7.1f %7.2f %9.0f %9.3f %9.2f".format(
                            cm, means[gi], avg { if (it.shots > 0) it.accuracy else null }, avg { it.meanTimeToHitMs },
                            avg { it.errorSpread }, avg { it.trackingOnTargetRatio },
                        ),
                    )
                }
            }
        }
        publishReport("scenario-sweeps.txt", report)
    }
}
