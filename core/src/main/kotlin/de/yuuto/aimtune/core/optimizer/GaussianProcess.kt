package de.yuuto.aimtune.core.optimizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * One round as the optimizer sees it: where it was played and the round's score. [group] says which
 * kind of round it was (the scenario); scores of one group share an unknown level of their own.
 */
data class Observation(val sessionId: Long, val cm360: Double, val score: Double, val group: Int = 0) {
    init {
        require(cm360 > 0.0 && cm360.isFinite() && score.isFinite()) { "invalid observation" }
    }

    /** The optimizer works on the log scale. */
    val x: Double get() = ln(cm360)
}

/**
 * Hyperparameters on standardized scores (mean 0, sd 1) and `x = ln(cm360)`.
 * [sessionVariance] is the variance of a per-session offset (a better or worse day),
 * [groupVariance] the same for a per-group (scenario) offset.
 */
data class GpHyperParams(
    val lengthscale: Double,
    val signalVariance: Double,
    val noiseVariance: Double,
    val sessionVariance: Double,
    val groupVariance: Double = 0.0,
)

/** Posterior of the score function on a set of points, in the units of the scores. */
class Posterior(val x: DoubleArray, val mean: DoubleArray, val covariance: DoubleArray) {
    val size: Int get() = x.size
    fun variance(i: Int): Double = covariance[i * size + i].coerceAtLeast(0.0)
    fun sd(i: Int): Double = sqrt(variance(i))
}

/** Matern 5/2 kernel for a distance [r] and lengthscale. */
internal fun matern52(r: Double, lengthscale: Double): Double {
    val z = SQRT5 * abs(r) / lengthscale
    return (1.0 + z + z * z / 3.0) * exp(-z)
}

private val SQRT5 = sqrt(5.0)

/**
 * Gaussian process regression of the round score over `ln(cm360)`:
 * `y = f(x) + offset(session) + offset(group) + noise` with a Matern 5/2 prior on f. The offsets are
 * integrated out through extra "same session" and "same group" covariance terms, so rounds of
 * several sessions and scenarios combine into one estimate of f.
 * Scores are standardized internally; results come back in score units.
 */
class GaussianProcess private constructor(
    private val xs: DoubleArray,
    private val yStandardized: DoubleArray,
    private val mean: Double,
    private val scale: Double,
    val hyper: GpHyperParams,
    private val factor: Cholesky,
    private val alpha: DoubleArray,
    val logMarginalLikelihood: Double,
) {
    /** Posterior mean and covariance of f at [grid] (ascending x values). */
    fun posterior(grid: DoubleArray): Posterior {
        val n = xs.size
        val m = grid.size
        val cross = Array(m) { j -> DoubleArray(n) { i -> hyper.signalVariance * matern52(xs[i] - grid[j], hyper.lengthscale) } }
        val mu = DoubleArray(m) { j -> mean + scale * cross[j].indices.sumOf { cross[j][it] * alpha[it] } }
        val v = Array(m) { j -> factor.solveLower(cross[j]) }
        val cov = DoubleArray(m * m)
        for (a in 0 until m) {
            for (b in 0..a) {
                var dot = 0.0
                for (k in 0 until n) dot += v[a][k] * v[b][k]
                val value = (hyper.signalVariance * matern52(grid[a] - grid[b], hyper.lengthscale) - dot) * scale * scale
                cov[a * m + b] = value
                cov[b * m + a] = value
            }
        }
        return Posterior(grid, mu, cov)
    }

    companion object {
        /** Fits with fixed hyperparameters; null when the covariance cannot be factored. */
        fun fit(observations: List<Observation>, hyper: GpHyperParams): GaussianProcess? {
            require(observations.isNotEmpty()) { "need at least one observation" }
            val n = observations.size
            val ys = observations.map { it.score }
            val mean = ys.average()
            val sd = sqrt(ys.sumOf { (it - mean) * (it - mean) } / maxOf(1, n - 1))
            val scale = if (sd < MIN_SCALE) 1.0 else sd
            val yStd = DoubleArray(n) { (ys[it] - mean) / scale }
            val xs = DoubleArray(n) { observations[it].x }
            val k = DoubleArray(n * n)
            for (i in 0 until n) {
                for (j in 0 until n) {
                    var v = hyper.signalVariance * matern52(xs[i] - xs[j], hyper.lengthscale)
                    if (observations[i].sessionId == observations[j].sessionId) v += hyper.sessionVariance
                    if (observations[i].group == observations[j].group) v += hyper.groupVariance
                    if (i == j) v += hyper.noiseVariance
                    k[i * n + j] = v
                }
            }
            val factor = Cholesky.factor(k, n) ?: return null
            val alpha = factor.solve(yStd)
            val lml = -0.5 * yStd.indices.sumOf { yStd[it] * alpha[it] } - 0.5 * factor.logDeterminant() - 0.5 * n * ln(2.0 * PI)
            return GaussianProcess(xs, yStd, mean, scale, hyper, factor, alpha, lml)
        }

        private const val MIN_SCALE = 1e-9
    }
}
