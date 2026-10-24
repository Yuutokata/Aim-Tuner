package de.yuuto.aimtune.core.harness.review

import de.yuuto.aimtune.core.accel.settings.AxisRatio
import de.yuuto.aimtune.core.accel.settings.AxisRatioAnalysis
import de.yuuto.aimtune.core.accel.settings.PassiveResult
import de.yuuto.aimtune.core.accel.settings.RotationAnalysis
import de.yuuto.aimtune.core.accel.settings.SnapReplay
import de.yuuto.aimtune.core.accel.settings.firstReaches
import de.yuuto.aimtune.core.harness.TEST_DPI
import de.yuuto.aimtune.core.harness.publishReport
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.sim.HandGeometry
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.math.abs
import kotlin.test.Test

/**
 * Gate of the passive setting analyses (M22e, docs/ALGORITHMS.md "Passive setting analyses"): a simulated hand with a known grip
 * rotation, vertical gain, left or up bias or drift plays 20 flick and 10 switching rounds without acceleration, and the analyses
 * run on the stored rounds. Every hand feeds every axis analysis (the cross rows show whether a deviation leaks into another
 * analysis); the rows with a target are the ones of the milestone. Thresholds live in the analyses and were fixed before this ran.
 */
@EnabledIfEnvironmentVariable(named = "AIMTUNE_REVIEW", matches = ".+")
class PassiveSettingsGateTest {
    private val runs = System.getenv("AIMTUNE_RUNS")?.toIntOrNull() ?: 200
    private val cm360 = CALIBRATION_BASE_CM360

    private class Hand(val label: String, val hand: HandGeometry, val snap: Boolean = false)

    private val hands = listOf(
        Hand("neutral", HandGeometry(), snap = true),
        Hand("rotation +3", HandGeometry(gripRotationDegrees = 3.0)),
        Hand("rotation -3", HandGeometry(gripRotationDegrees = -3.0)),
        Hand("rotation +6", HandGeometry(gripRotationDegrees = 6.0)),
        Hand("rotation -6", HandGeometry(gripRotationDegrees = -6.0)),
        Hand("rotation +10", HandGeometry(gripRotationDegrees = 10.0)),
        Hand("rotation -10", HandGeometry(gripRotationDegrees = -10.0)),
        Hand("g_y 0.8", HandGeometry(verticalGain = 0.8)),
        Hand("g_y 1.25", HandGeometry(verticalGain = 1.25)),
        Hand("b_L 0.85", HandGeometry(leftBias = 0.85)),
        Hand("b_L 1.15", HandGeometry(leftBias = 1.15)),
        Hand("b_U 0.85", HandGeometry(upBias = 0.85)),
        Hand("b_U 1.15", HandGeometry(upBias = 1.15)),
        Hand("drift sd 0.06", HandGeometry(driftSlopeSd = 0.06), snap = true),
    )

    private fun rounds(hand: HandGeometry, run: Int): List<RoundRecording> {
        val player = SimulatedPlayer(hand = hand)
        return (0 until ROUNDS_PER_RUN).map { i ->
            val kind = if (i < FLICK_ROUNDS) ScenarioKind.FLICK else ScenarioKind.SWITCHING
            HeadlessRound.of(kind, player, TEST_DPI, cm360, run * 1_000L + i, ROUND_SECONDS)
        }
    }

    private class RunResults(val rotation: PassiveResult, val axes: Map<AxisRatio, PassiveResult>, val snap: PassiveResult?, val reaches: Int)

    private fun analyze(hand: Hand, run: Int): RunResults {
        val recordings = rounds(hand.hand, run)
        val reaches = firstReaches(recordings)
        return RunResults(
            rotation = RotationAnalysis.analyze(reaches),
            axes = AxisRatio.entries.associateWith { AxisRatioAnalysis.analyze(reaches, it) },
            snap = if (hand.snap) SnapReplay.analyze(recordings).result else null,
            reaches = reaches.size,
        )
    }

    private fun value(r: PassiveResult): Double? = when (r) {
        is PassiveResult.Recommend -> r.value
        is PassiveResult.NoChange -> r.estimate
        is PassiveResult.NotEnoughData -> null
    }

    private fun line(label: String, results: List<PassiveResult>, neutral: Double, truth: Double?, within: Double?, valueOf: (PassiveResult) -> Double? = ::value): String {
        val rec = results.filterIsInstance<PassiveResult.Recommend>()
        val direction = if (truth == null) null else rec.count { (it.value - neutral) * (truth - neutral) > 0 }
        val exact = if (truth == null || within == null) null else rec.count { (it.value - neutral) * (truth - neutral) > 0 && abs(it.value - truth) <= within }
        val median = results.mapNotNull(valueOf).let { if (it.isEmpty()) Double.NaN else median(it) }
        val notEnough = results.count { it is PassiveResult.NotEnoughData }
        fun pct(count: Int?) = if (count == null) "     -" else "%5.0f%%".format(100.0 * count / results.size)
        return "%-34s %4d %s %s %s %9.3f %s".format(label, results.size, pct(rec.size), pct(direction), pct(exact), median, pct(notEnough))
    }

    @Test
    fun passiveSettingsGate() {
        val out = StringBuilder("== Gate M22e passive settings: $runs runs per row, $FLICK_ROUNDS flick + $SWITCH_ROUNDS switching rounds per run, no acceleration, cm/360 $cm360 ==\n")
        out.appendLine("rec = recommendation rate; dir = recommended in the right direction (share of all runs); ok = right direction and within tolerance (rotation: 2 deg); NED = NotEnoughData rate")
        out.appendLine("%-34s %4s %6s %6s %6s %9s %6s".format("row (analysis)", "n", "rec", "dir", "ok", "median", "NED"))
        val all = hands.associateWith { hand -> parallelList(runs) { analyze(hand, it) } }
        for ((hand, results) in all) {
            val g = hand.hand
            val reaches = results.map { it.reaches.toDouble() }
            out.appendLine("-- ${hand.label}: median usable first reaches per run %.0f".format(median(reaches)))
            val rotationTruth = g.gripRotationDegrees
            out.appendLine(line("  rotation", results.map { it.rotation }, 0.0, rotationTruth.takeIf { it != 0.0 }, 2.0))
            val yx = line("  Y/X", results.map { it.axes.getValue(AxisRatio.Y_X) }, 1.0, g.verticalGain.takeIf { it != 1.0 }, null)
            val lr = line("  L/R", results.map { it.axes.getValue(AxisRatio.L_R) }, 1.0, g.leftBias.takeIf { it != 1.0 }, null)
            val ud = line("  U/D", results.map { it.axes.getValue(AxisRatio.U_D) }, 1.0, g.upBias.takeIf { it != 1.0 }, null)
            out.appendLine(yx)
            out.appendLine(lr)
            out.appendLine(ud)
            if (hand.snap) {
                val snaps = results.mapNotNull { it.snap }
                val recommended = snaps.filterIsInstance<PassiveResult.Recommend>()
                val byValue = recommended.groupingBy { it.value }.eachCount().toSortedMap()
                out.appendLine(
                    "  snap: recommends 0 in %.0f%% of runs; recommends >0 in %.0f%% %s; not enough data %.0f%%".format(
                        100.0 * snaps.count { it !is PassiveResult.Recommend } / snaps.size,
                        100.0 * recommended.size / snaps.size,
                        byValue,
                        100.0 * snaps.count { it is PassiveResult.NotEnoughData } / snaps.size,
                    ),
                )
            }
        }
        publishReport("m22e-passive-gate.txt", out.toString())
    }

    private companion object {
        const val FLICK_ROUNDS = 20
        const val SWITCH_ROUNDS = 10
        const val ROUNDS_PER_RUN = FLICK_ROUNDS + SWITCH_ROUNDS
    }
}
