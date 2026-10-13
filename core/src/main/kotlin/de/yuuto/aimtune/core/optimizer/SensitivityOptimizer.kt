package de.yuuto.aimtune.core.optimizer

import de.yuuto.aimtune.core.score.normalCdf
import de.yuuto.aimtune.core.units.SearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Why a cm/360 was suggested; the app never shows this to the user during a blind test. */
enum class SuggestionKind { DESIGN, EXPLORE, REPEAT }

data class Suggestion(val cm360: Double, val kind: SuggestionKind)

data class CurvePoint(val cm360: Double, val meanScore: Double, val sdScore: Double)

/**
 * What the optimizer knows after a set of rounds. [intervalLowCm360]..[intervalHighCm360] is the
 * central credible interval of the best cm/360. [confidencePercent] is the posterior probability
 * that [optimumCm360] scores within [negligibleDifference] points of the true best.
 */
data class OptimizationResult(
    val optimumCm360: Double,
    /** True when [optimumCm360] is at an end of the searched range, so a better value may lie outside it. */
    val atRangeEdge: Boolean,
    val intervalLowCm360: Double,
    val intervalHighCm360: Double,
    val confidencePercent: Int,
    /** Score difference below which two sensitivities count as equally good (a tenth of the spread of round scores). */
    val negligibleDifference: Double,
    /** Average score points lost by picking [optimumCm360] instead of the true best, over the posterior. */
    val expectedRegret: Double,
    /** Largest expected improvement of one more round over the current best estimate, in score points. */
    val maxExpectedImprovement: Double,
    val recommendMoreRounds: Boolean,
    /** Rough estimate of the rounds still needed to reach the target confidence; 0 when reached. */
    val roundsToTarget: Int,
    val curve: List<CurvePoint>,
    val hyper: GpHyperParams,
    val rounds: Int,
    val sessions: Int,
)

data class OptimizerConfig(
    /** Rounds placed by a randomized space-filling design before the model chooses. */
    val initialDesignRounds: Int = 6,
    val gridPoints: Int = 121,
    val posteriorSamples: Int = 400,
    val intervalMass: Double = 0.8,
    /** Confidence (percent) at which more rounds are no longer recommended. */
    val targetConfidencePercent: Int = 85,
    /** Fraction of the score spread that counts as a negligible difference between two sensitivities. */
    val negligibleFraction: Double = 0.2,
    /** Below this many rounds the confidence is capped at 50 %. */
    val minRoundsForFullConfidence: Int = 10,
    val minNegligibleDifference: Double = 0.5,
    val repeatEvery: Int = 6,
    /** Other cm/360 values within this log distance of the previous round are not suggested next. */
    val exclusionLn: Double = 0.03,
    val maxRounds: Int = 80,
) {
    init {
        require(initialDesignRounds >= 2 && gridPoints >= 20 && posteriorSamples >= 50) { "config too small" }
        require(intervalMass in 0.5..0.99 && targetConfidencePercent in 1..99) { "invalid interval or target settings" }
    }
}

/**
 * Bayesian optimization of the sensitivity: a Gaussian process over `ln(cm360)` of the round score,
 * expected improvement to choose the next cm/360, occasional repeats to learn the noise, and a
 * credible interval of the best cm/360 for the result and the confidence. It is a pure function of
 * the history (and the seed), so a test can be stopped and continued, even across sessions.
 * See docs/ALGORITHMS.md ("Bayesian optimization").
 */
class SensitivityOptimizer(
    private val range: SearchRange,
    private val seed: Long = 0L,
    private val config: OptimizerConfig = OptimizerConfig(),
) {
    private val lnLo = ln(range.minCm360)
    private val lnHi = ln(range.maxCm360)
    private val width = lnHi - lnLo
    private val grid = DoubleArray(config.gridPoints) { lnLo + width * it / (config.gridPoints - 1) }

    fun suggestNext(history: List<Observation>): Suggestion {
        val n = history.size
        if (n < config.initialDesignRounds) return Suggestion(designPoint(n), SuggestionKind.DESIGN)
        val gp = fitBest(history) ?: return Suggestion(designPoint(n), SuggestionKind.DESIGN)
        val posterior = gp.posterior(grid)
        val last = history.last().x
        if (shouldRepeat(history)) {
            val best = history.filter { abs(it.x - last) > config.exclusionLn }
                .maxByOrNull { posteriorMeanAt(posterior, it.x) }
            if (best != null) return Suggestion(best.cm360, SuggestionKind.REPEAT)
        }
        val ei = expectedImprovement(posterior)
        val pick = grid.indices.filter { abs(grid[it] - last) > config.exclusionLn }.maxByOrNull { ei[it] }
            ?: grid.indices.maxBy { ei[it] }
        return Suggestion(exp(grid[pick]), SuggestionKind.EXPLORE)
    }

    /** Null while there are too few rounds for a model (fewer than 3). */
    fun analyze(history: List<Observation>): OptimizationResult? {
        if (history.size < MIN_ROUNDS_FOR_RESULT) return null
        val gp = fitBest(history) ?: return null
        val posterior = gp.posterior(grid)
        val best = grid.indices.maxBy { posterior.mean[it] }
        val scores = history.map { it.score }
        val spread = sqrt(scores.sumOf { (it - scores.average()) * (it - scores.average()) } / (scores.size - 1))
        val negligible = maxOf(config.minNegligibleDifference, config.negligibleFraction * spread)
        val summary = sampleOptimum(posterior, best, negligible, Random(seed xor (history.size.toLong() * SEED_MIX)))
        val maxEi = expectedImprovement(posterior).max()
        // With few rounds the noise is unknown, so a high confidence would be an artifact of the fit.
        val optimumX = refinedOptimumX(posterior, best)
        // A best value at the edge of the searched range may only be the end of a slope: the real optimum can lie outside.
        val atEdge = optimumX - lnLo < EDGE_FRACTION * width || lnHi - optimumX < EDGE_FRACTION * width
        val rawConfidence = (100.0 * summary.probabilityNegligibleLoss).toInt()
        val limited = history.size < config.minRoundsForFullConfidence || atEdge
        val confidence = if (limited) minOf(rawConfidence, LIMITED_CONFIDENCE_CAP) else rawConfidence
        val target = config.targetConfidencePercent
        val more = confidence < target && history.size < config.maxRounds
        return OptimizationResult(
            optimumCm360 = exp(optimumX),
            atRangeEdge = atEdge,
            intervalLowCm360 = exp(summary.lowX),
            intervalHighCm360 = exp(summary.highX),
            confidencePercent = confidence,
            negligibleDifference = negligible,
            expectedRegret = summary.regret,
            maxExpectedImprovement = maxEi,
            recommendMoreRounds = more,
            roundsToTarget = if (confidence >= target) 0 else roundsNeeded(history.size, confidence, target),
            curve = grid.indices.map { CurvePoint(exp(grid[it]), posterior.mean[it], posterior.sd(it)) },
            hyper = gp.hyper,
            rounds = history.size,
            sessions = history.map { it.sessionId }.distinct().size,
        )
    }

    /** The i-th point of a seeded, shuffled, stratified design over the log range. */
    private fun designPoint(index: Int): Double {
        val random = Random(seed)
        val n = config.initialDesignRounds
        val strata = (0 until n).shuffled(random)
        val jitter = List(n) { DESIGN_JITTER_MIN + random.nextDouble() * (1.0 - 2 * DESIGN_JITTER_MIN) }
        val slot = strata[index % n]
        return exp(lnLo + width * (slot + jitter[slot]) / n)
    }

    private fun shouldRepeat(history: List<Observation>): Boolean {
        val n = history.size
        if (n < MIN_ROUNDS_FOR_REPEAT) return false
        val hasRepeat = history.indices.any { i ->
            (i + 1 until n).any { j -> history[i].sessionId == history[j].sessionId && abs(history[i].x - history[j].x) < REPEAT_TOLERANCE_LN }
        }
        return !hasRepeat || n % config.repeatEvery == 0
    }

    private fun posteriorMeanAt(posterior: Posterior, x: Double): Double {
        val i = ((x - lnLo) / width * (config.gridPoints - 1)).toInt().coerceIn(0, config.gridPoints - 1)
        return posterior.mean[i]
    }

    private fun expectedImprovement(posterior: Posterior): DoubleArray {
        val incumbent = posterior.mean.max()
        return DoubleArray(posterior.size) { i ->
            val sd = posterior.sd(i)
            if (sd < MIN_SD) {
                0.0
            } else {
                val z = (posterior.mean[i] - incumbent) / sd
                (posterior.mean[i] - incumbent) * normalCdf(z) + sd * normalPdf(z)
            }
        }
    }

    /** The chance of a negligible loss is assumed to fail proportionally to 1/rounds; a rough guide, not a guarantee. */
    private fun roundsNeeded(rounds: Int, confidence: Int, target: Int): Int {
        val failureNow = (100 - confidence).coerceAtLeast(1).toDouble()
        val needed = rounds * failureNow / (100 - target)
        return (needed - rounds).toInt().coerceIn(1, MAX_ROUNDS_ESTIMATE)
    }

    private data class OptimumSummary(val lowX: Double, val highX: Double, val regret: Double, val probabilityNegligibleLoss: Double)

    /** Draws functions from the posterior and summarizes where their maxima lie. */
    private fun sampleOptimum(posterior: Posterior, bestIndex: Int, negligible: Double, random: Random): OptimumSummary {
        val m = posterior.size
        val covariance = posterior.covariance.copyOf()
        val meanVariance = (0 until m).sumOf { covariance[it * m + it] } / m
        for (i in 0 until m) covariance[i * m + i] += SAMPLING_JITTER * maxOf(meanVariance, MIN_SD)
        val factor = Cholesky.factor(covariance, m)
        if (factor == null) {
            val half = Z_80 * posterior.sd(bestIndex) / maxOf(abs(posterior.mean[bestIndex]), 1.0) * width
            return OptimumSummary(posterior.x[bestIndex] - half, posterior.x[bestIndex] + half, 0.0, 0.0)
        }
        val argmaxX = DoubleArray(config.posteriorSamples)
        var regret = 0.0
        var negligibleLoss = 0
        repeat(config.posteriorSamples) { s ->
            val draw = factor.multiplyLower(DoubleArray(m) { random.gaussian() })
            var top = 0
            for (i in 0 until m) if (draw[i] + posterior.mean[i] > draw[top] + posterior.mean[top]) top = i
            argmaxX[s] = posterior.x[top]
            val loss = (draw[top] + posterior.mean[top]) - (draw[bestIndex] + posterior.mean[bestIndex])
            regret += loss
            if (loss <= negligible) negligibleLoss++
        }
        argmaxX.sort()
        val tail = (1.0 - config.intervalMass) / 2.0
        val low = argmaxX[(tail * (argmaxX.size - 1)).toInt()]
        val high = argmaxX[((1.0 - tail) * (argmaxX.size - 1)).toInt()]
        return OptimumSummary(low, high, regret / config.posteriorSamples, negligibleLoss.toDouble() / config.posteriorSamples)
    }

    /** Parabola through the best grid point and its neighbours, for a position finer than the grid. */
    private fun refinedOptimumX(posterior: Posterior, best: Int): Double {
        if (best == 0 || best == posterior.size - 1) return posterior.x[best]
        val y0 = posterior.mean[best - 1]
        val y1 = posterior.mean[best]
        val y2 = posterior.mean[best + 1]
        val denominator = y0 - 2 * y1 + y2
        if (denominator >= 0.0) return posterior.x[best]
        val offset = 0.5 * (y0 - y2) / denominator
        return posterior.x[best] + offset.coerceIn(-1.0, 1.0) * (posterior.x[best + 1] - posterior.x[best])
    }

    /** Best hyperparameters from a small grid by marginal likelihood, scaled to the search range. */
    private fun fitBest(history: List<Observation>): GaussianProcess? {
        val multiSession = history.map { it.sessionId }.distinct().size > 1
        val multiGroup = history.map { it.group }.distinct().size > 1
        var best: GaussianProcess? = null
        for (ls in LENGTHSCALE_FACTORS) for (sv in SIGNAL_VARIANCES) for (nv in NOISE_VARIANCES) {
            for (tv in if (multiSession) SESSION_VARIANCES else listOf(0.0)) for (gv in if (multiGroup) GROUP_VARIANCES else listOf(0.0)) {
                val gp = GaussianProcess.fit(history, GpHyperParams(ls * width, sv, nv, tv, gv)) ?: continue
                if (best == null || gp.logMarginalLikelihood > best.logMarginalLikelihood) best = gp
            }
        }
        return best
    }

    private fun normalPdf(z: Double) = exp(-0.5 * z * z) / sqrt(2.0 * PI)

    private fun Random.gaussian(): Double = sqrt(-2.0 * ln(1.0 - nextDouble())) * cos(2.0 * PI * nextDouble())

    private companion object {
        const val MIN_ROUNDS_FOR_RESULT = 3
        const val MIN_ROUNDS_FOR_REPEAT = 8
        const val REPEAT_TOLERANCE_LN = 0.02
        const val DESIGN_JITTER_MIN = 0.15
        const val MIN_SD = 1e-9
        const val SAMPLING_JITTER = 1e-6
        const val Z_80 = 1.2816
        const val LIMITED_CONFIDENCE_CAP = 50

        /** An optimum within this share of the log range width from either end counts as at the edge. */
        const val EDGE_FRACTION = 0.02
        const val MAX_ROUNDS_ESTIMATE = 40
        val SEED_MIX = 0x9E3779B97F4A7C15uL.toLong()
        val LENGTHSCALE_FACTORS = listOf(0.12, 0.2, 0.3, 0.45, 0.7)
        val SIGNAL_VARIANCES = listOf(0.5, 1.0, 2.0)
        val NOISE_VARIANCES = listOf(0.15, 0.3, 0.5, 0.8, 1.2)
        val SESSION_VARIANCES = listOf(0.0, 0.1, 0.4)
        val GROUP_VARIANCES = listOf(0.0, 0.4)
    }
}
