package de.yuuto.aimtune.core.harness.review

import de.yuuto.aimtune.core.accel.AbCondition
import de.yuuto.aimtune.core.accel.AbStatus
import de.yuuto.aimtune.core.accel.AccelAbPlan
import de.yuuto.aimtune.core.accel.AccelScenarios
import de.yuuto.aimtune.core.accel.RawAccelArgs
import de.yuuto.aimtune.core.accel.RawAccelCapMode
import de.yuuto.aimtune.core.accel.RawAccelMode
import de.yuuto.aimtune.core.accel.RawAccelProfile
import de.yuuto.aimtune.core.accel.RefineEngine
import de.yuuto.aimtune.core.accel.RefineParameter
import de.yuuto.aimtune.core.accel.RefinePhase
import de.yuuto.aimtune.core.accel.RefineResult
import de.yuuto.aimtune.core.accel.RefineRound
import de.yuuto.aimtune.core.accel.adjusted
import de.yuuto.aimtune.core.harness.TEST_DPI
import de.yuuto.aimtune.core.harness.geometricGrid
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreCalculator
import de.yuuto.aimtune.core.score.ScoreMetric
import de.yuuto.aimtune.core.score.scoreInputs
import de.yuuto.aimtune.core.sim.HandAdaptation
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * M18 gate: the headless driver of "Refine my Raw Accel curve" ([RefineEngine], the logic the app runs) with simulated players
 * whose hand is adapted to the imported curve. The imported profile is the user's real one (classic, gain form, offset 10,
 * cap 62.5 in/s at 1.6, output DPI 600 at device DPI 800). The game sensitivity is 55 cm/360, flash rounds are on.
 *
 * What the simulated player wants is a profile, its "ideal": [Model.ideal] gives the shape of the gain it expects at a hand
 * speed (the simulated player's `gainPreference`), and the level is its optimum scale. The scale is calibrated so that the
 * imported profile played at the game sensitivity sits at the player's optimum level: without that, the NULL model would
 * not be null (the simulated hand has one best cm/360, and a curve changes the cm/360 it effectively plays). The calibration
 * is printed in the report.
 *
 * The user policy is fixed: every stage is played to its end, the player continues through all stages and takes the test.
 * AIMTUNE_SEEDS scales the run counts (40 = the numbers in the table header).
 */
@EnabledIfEnvironmentVariable(named = "AIMTUNE_REVIEW", matches = ".+")
class RefineGateTest {
    private val gameCm360 = 55.0
    private val weights = MetricWeights.DEFAULT
    private val imported = RawAccelProfile(
        "default", RawAccelArgs(RawAccelMode.CLASSIC, gain = true, inputOffset = 10.0, capX = 62.5, capY = 1.6, capMode = RawAccelCapMode.IN_OUT), 600.0, 800.0,
    )
    private val scale = (System.getenv("AIMTUNE_SEEDS")?.toIntOrNull() ?: 40) / 40.0

    private fun n(base: Int) = (base * scale).toInt().coerceAtLeast(4)

    /** A simulated player: the profile whose gain shape it expects, the level it wants relative to the imported one, and its range bias. */
    private class Model(val name: String, val ideal: RawAccelProfile, val levelWant: Double = 1.0, val rangeBias: Double = 0.0, val runs: Int, val stageRounds: Int = 12)

    private val models = listOf(
        Model("REFINE_NULL", imported, runs = n(100)),
        Model("REFINE_LEVEL-15", imported, levelWant = 0.87, runs = n(100)),
        Model("REFINE_LEVEL+15", imported, levelWant = 1.15, runs = n(100)),
        Model("REFINE_LEVEL-15 (24r)", imported, levelWant = 0.87, runs = n(60), stageRounds = 24),
        Model("REFINE_LEVEL+15 (24r)", imported, levelWant = 1.15, runs = n(60), stageRounds = 24),
        Model("REFINE_CAP-25", imported.adjusted(RefineParameter.HIGH_GAIN, 0.75), runs = n(60)),
        Model("REFINE_CAP+25", imported.adjusted(RefineParameter.HIGH_GAIN, 1.25), runs = n(60)),
        Model("REFINE_RANGE", imported, rangeBias = 0.05, runs = n(100)),
    )

    private val optimumScales = HashMap<Double, Double>()
    private var lastSweep = ""

    private fun player(model: Model, optimumScale: Double): SimulatedPlayer {
        val preference: (Double) -> Double = { v -> model.ideal.curve.multiplier(v) }
        return SimulatedPlayer(
            optimumScale = optimumScale / model.levelWant, gainPreference = preference, rangeBias = model.rangeBias,
            // A fresh, mutable hand per simulated person, settled on the curve it has been playing: the imported one.
            adaptation = HandAdaptation.adaptedTo(preference, delivered = { v -> imported.curve.multiplier(v) }),
        )
    }

    private fun play(player: SimulatedPlayer, round: RefineRound, index: Int): Map<ScoreMetric, Double> =
        computeRoundMetrics(
            HeadlessRound.of(round.scenario, player, TEST_DPI, round.cm360, round.seed, ROUND_SECONDS, index * ROUND_SECONDS * NANOS_PER_SECOND, accel = round.profile),
        ).scoreInputs()

    /** The level factor at which a stateless player with [optimumScale] scores best with the imported profile (smooth fit of a sweep). */
    private fun levelOptimum(rangeBias: Double, optimumScale: Double): Double {
        val grid = geometricGrid(0.75, 1.33, 9)
        val per = 300
        val base = SimulatedPlayer(optimumScale = optimumScale, gainPreference = { imported.curve.multiplier(it) }, rangeBias = rangeBias)
        val kinds = AccelScenarios.WITH_FLASH
        val inputs = grid.indices.map { gi ->
            parallelList(per) { r ->
                val round = RefineRound(kinds[r % kinds.size], 77L * 1_009 + gi * 101 + r, imported, gameCm360 / grid[gi])
                play(base, round, gi * per + r)
            }
        }
        val scores = ScoreCalculator(weights).scoreRounds(inputs.flatten(), grid.indices.flatMap { gi -> List(per) { kinds[it % kinds.size].id } })
        var i = 0
        val means = grid.indices.map { (0 until per).mapNotNull { scores[i++] }.average() }
        val all = scores.filterNotNull()
        val mean = all.average()
        lastSweep = grid.zip(means).joinToString(" ") { "%.2f:%.1f".format(it.first, it.second) } + "  (round score sd %.1f)".format(kotlin.math.sqrt(all.sumOf { (it - mean) * (it - mean) } / all.size))
        return SmoothCurve(grid, means, degree = 2).argmax(grid.first(), grid.last())
    }

    /** Scales a player's optimum so that its best level with the imported profile is 1 (the optimum is proportional to 1 / scale). */
    private fun calibrated(rangeBias: Double, log: StringBuilder): Double = optimumScales.getOrPut(rangeBias) {
        var scaleNow = 0.95
        repeat(3) { step ->
            val k = levelOptimum(rangeBias, scaleNow)
            log.appendLine("  calibration rangeBias $rangeBias step ${step + 1}: optimumScale %.4f -> best level factor %.3f".format(scaleNow, k))
            scaleNow *= k
        }
        val check = levelOptimum(rangeBias, scaleNow)
        log.appendLine("  sweep (level factor : mean score): $lastSweep")
        log.appendLine("  calibration rangeBias $rangeBias final: optimumScale %.4f -> best level factor %.3f".format(scaleNow, check))
        scaleNow
    }

    private class Run(val results: Map<RefineParameter, RefineResult>, val changed: Boolean, val status: AbStatus?, val benefit: Double?)

    private fun runOnce(model: Model, optimumScale: Double, seed: Long): Run {
        val player = player(model, optimumScale)
        val engine = RefineEngine(imported, gameCm360, weights, seed, flash = true, stageRounds = model.stageRounds)
        var index = 0
        while (engine.phase != RefinePhase.DONE) {
            if (engine.phase == RefinePhase.STAGE_RESULT) engine.continueToNextStage()
            else engine.record(play(player, engine.nextRound(), index++))
        }
        val status = engine.verdict?.status
        val benefit = if (status == AbStatus.ACCEL_BETTER) trueBenefit(model, optimumScale, engine.candidate, seed) else null
        return Run(engine.stageResults.associateBy { it.parameter }, engine.hasCandidate, status, benefit)
    }

    /** Mean score of the candidate minus the control over 40 pairs of the real plan, played by a fresh player of the same model. */
    private fun trueBenefit(model: Model, optimumScale: Double, candidate: RawAccelProfile, seed: Long): Double {
        val pairs = 40
        val player = player(model, optimumScale)
        val plan = AccelAbPlan(AccelScenarios.WITH_FLASH, 900_000L + seed, pairs)
        val rounds = (0 until 2 * pairs).map { plan.round(it) }
        val inputs = rounds.mapIndexed { i, r ->
            val profile = if (r.condition == AbCondition.ACCEL) candidate else imported
            play(player, RefineRound(r.scenario, r.seed, profile, gameCm360), i)
        }
        val scores = ScoreCalculator(weights).scoreRounds(inputs, rounds.map { it.scenario.id })
        val diffs = (0 until pairs).mapNotNull { p ->
            val a = (2 * p..2 * p + 1).first { rounds[it].condition == AbCondition.ACCEL }
            val c = (2 * p..2 * p + 1).first { rounds[it].condition == AbCondition.PLAIN }
            val sa = scores[a]
            val sc = scores[c]
            if (sa != null && sc != null) sa - sc else null
        }
        return diffs.average()
    }

    @Test
    fun gate() {
        val out = StringBuilder()
        out.appendLine("== GATE m18 'Refine my Raw Accel curve' @ git ${gitShortHash()} | profile: classic gain, offset 10, cap 62.5 in/s -> 1.6, output 600 / device 800 | game ${gameCm360} cm/360 | flash on ==")
        out.appendLine("policy: 8 warm-up rounds, stages LEVEL, HIGH_GAIN, TRANSITION of 12 rounds each, continue through all stages, then the A/B test (max 12 pairs, alpha 0.01 per look, >= 6 pairs, >= 2 points). Export = the test says the candidate is better.")
        val started = System.currentTimeMillis()
        val rows = StringBuilder()
        val calibration = StringBuilder()
        for (model in models) {
            val optimumScale = calibrated(model.rangeBias, calibration)
            val runs = parallelList(model.runs) { s -> runOnce(model, optimumScale, 31_000L + s) }
            rows.appendLine(row(model, runs))
            out.appendLine("[${model.name} done, elapsed %d s]".format((System.currentTimeMillis() - started) / 1000))
        }
        out.appendLine()
        out.appendLine("-- calibration of the simulated hand (level factor at which the imported profile scores best; target 1.00)")
        out.append(calibration)
        out.appendLine()
        out.appendLine("-- results. runs = simulated people; level side = LEVEL stage best on the wanted side of 1; level carried = its range excluded 1; carried = any stage carried a change;")
        out.appendLine("-- exported = the A/B test said better; harmful = exported although the 40-pair mean benefit of the candidate over the imported profile is < 0; benefit = mean of that over exports")
        out.appendLine("model             runs  levelBest(med)  level side  level carried  gain carried  trans carried | carried  tested  better  worse  nodiff | exported  harmful  benefit")
        out.append(rows)
        out.appendLine()
        out.appendLine("targets: REFINE_NULL exported <= 5 %; LEVEL +-15: level side >= 70 %, exported >= 50 %, harmful 0; CAP and RANGE: report only.")
        write("gate-m18.txt", out.toString())
    }

    private fun row(model: Model, runs: List<Run>): String {
        val n = runs.size
        fun pct(count: Int) = "%5.1f%%".format(100.0 * count / n)
        val level = runs.map { it.results[RefineParameter.LEVEL] }
        val bests = level.mapNotNull { it?.best }
        val side = when {
            model.levelWant > 1.0 -> level.count { it != null && it.best > 1.0 }
            model.levelWant < 1.0 -> level.count { it != null && it.best < 1.0 }
            else -> -1
        }
        val exported = runs.filter { it.status == AbStatus.ACCEL_BETTER }
        val harmful = exported.count { (it.benefit ?: 0.0) < 0.0 }
        val statuses = runs.mapNotNull { it.status }
        fun carried(p: RefineParameter) = runs.count { it.results[p]?.excludesNeutral == true }
        return "%-22s %4d  %13.3f   %9s  %12s  %11s  %12s | %7s  %6d  %6d  %5d  %6d | %8s  %7d  %7s".format(
            model.name, n, median(bests), if (side < 0) "     -" else pct(side), pct(carried(RefineParameter.LEVEL)), pct(carried(RefineParameter.HIGH_GAIN)),
            pct(carried(RefineParameter.TRANSITION)), pct(runs.count { it.changed }), statuses.size,
            statuses.count { it == AbStatus.ACCEL_BETTER }, statuses.count { it == AbStatus.ACCEL_WORSE },
            statuses.count { it == AbStatus.NO_DIFFERENCE || it == AbStatus.NEED_MORE_ROUNDS }, pct(exported.size), harmful,
            if (exported.isEmpty()) "-" else "%+.2f".format(exported.mapNotNull { it.benefit }.average()),
        )
    }
}
