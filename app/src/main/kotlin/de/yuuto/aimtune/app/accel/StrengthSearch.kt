package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.core.accel.CurveStrength
import de.yuuto.aimtune.core.metrics.RoundMetrics
import de.yuuto.aimtune.core.optimizer.Observation
import de.yuuto.aimtune.core.optimizer.SensitivityOptimizer
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreCalculator
import de.yuuto.aimtune.core.score.ScoreMetric
import de.yuuto.aimtune.core.score.scoreInputs

/** What the blind strength search found. [low]..[high] is the credible range of the best strength. */
data class StrengthResult(
    val strength: Double,
    val low: Double,
    val high: Double,
    val confidencePercent: Int,
    val rounds: Int,
    /** True when the credible range lies on one side of "no curve" and the strength is a real curve. */
    val excludesPlain: Boolean,
    /** True when the best strength is at an end of the searched range, so a stronger curve may do better. */
    val atEdge: Boolean,
)

/**
 * The blind search for the curve strength: rounds played with different strengths (some of them no curve
 * at all) at the recommended sensitivity, scored like every other round, and the same optimizer that
 * finds the sensitivity looks for the strength that scores best. Without UI; [AccelRun] drives it.
 */
class StrengthSearch(private val weights: MetricWeights, seed: Long, val plannedRounds: Int = DEFAULT_ROUNDS) {
    private val optimizer = SensitivityOptimizer(CurveStrength.searchRange, seed)
    private val inputs = mutableListOf<Map<ScoreMetric, Double>>()
    private val scenarios = mutableListOf<String>()
    private val pseudo = mutableListOf<Double>()

    val roundsPlayed: Int get() = inputs.size

    /** The strength of the next round. Blind: callers must not show it. */
    fun nextStrength(): Double = CurveStrength.fromPseudo(optimizer.suggestNext(history()).cm360)

    fun record(strength: Double, scenario: ScenarioKind, metrics: RoundMetrics) {
        inputs += metrics.scoreInputs()
        scenarios += scenario.id
        pseudo += CurveStrength.toPseudo(strength)
    }

    /** The result so far, or null while there are too few rounds. */
    fun result(): StrengthResult? {
        val analysis = optimizer.analyze(history()) ?: return null
        val strength = CurveStrength.fromPseudo(analysis.optimumCm360)
        val low = CurveStrength.fromPseudo(analysis.intervalLowCm360)
        val high = CurveStrength.fromPseudo(analysis.intervalHighCm360)
        val side = low > CurveStrength.NEUTRAL || high < CurveStrength.NEUTRAL
        return StrengthResult(strength, low, high, analysis.confidencePercent, roundsPlayed, side && CurveStrength.isCurve(strength), analysis.atRangeEdge)
    }

    private fun history(): List<Observation> {
        val scores = ScoreCalculator(weights).scoreRounds(inputs, scenarios)
        return pseudo.indices.map { Observation(0L, pseudo[it], scores[it] ?: UNKNOWN_SCORE, scenarios[it].hashCode(), it) }
    }

    companion object {
        /** The experiments used 24 rounds (docs/ASSESSMENT.md); the user may finish from [MIN_ROUNDS] on. */
        const val DEFAULT_ROUNDS = 24
        const val MIN_ROUNDS = 16

        /** A round without a usable score yet only counts for the design phase. */
        private const val UNKNOWN_SCORE = 50.0
    }
}
