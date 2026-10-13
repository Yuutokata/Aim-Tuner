package de.yuuto.aimtune.core.accel

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * A Raw Accel curve that reproduces the wanted gain: the total gain at speed v is
 * `sensitivityFactor * curve.multiplier(v)`. Raw Accel curves start at 1, so a wanted gain that is
 * not 1 at low speed shows up as a factor for the in-game sensitivity: multiply it with
 * [sensitivityFactor] (equivalently divide cm/360 by it) and the average gain stays as it was.
 */
data class AccelFit(
    val family: String,
    val curve: AccelCurve,
    val sensitivityFactor: Double,
    /** Weighted squared misfit of this model, and of "no acceleration", over the speed bins. */
    val chiSquare: Double,
    val nullChiSquare: Double,
    /** BIC of "no acceleration" minus BIC of this model; above about 10 the curve is clearly better. */
    val bicGain: Double,
    /** Largest over smallest total gain across the measured speeds. */
    val gainRange: Double,
) {
    fun totalGain(speed: Double): Double = sensitivityFactor * curve.multiplier(speed)
}

/** What the fitting found: every family's fit, and the best one if it clears the bar. */
data class AccelFitResult(
    val points: List<GainPoint>,
    val fits: List<AccelFit>,
    /** The curve to try, or null when there is too little data or no curve is clearly better than none. */
    val recommended: AccelFit?,
    val reason: String,
)

/**
 * Fits the Raw Accel families (classic, natural, power) to the wanted gain of an [AccelAnalysis]
 * with a free overall factor, and recommends a curve only when it beats "no acceleration" by
 * [minBicGain] BIC points and changes the gain by at least [minGainRange] across the measured speeds.
 * Synchronous, jump and lookup modes are not fitted. See docs/ALGORITHMS.md ("Acceleration").
 */
fun fitAccel(analysis: AccelAnalysis, minBicGain: Double = 10.0, minGainRange: Double = 1.08): AccelFitResult {
    val points = analysis.points
    if (points.size < AccelAnalysis.MIN_BINS) {
        return AccelFitResult(points, emptyList(), null, "Not enough movements yet: about ${AccelAnalysis.MIN_BINS * AccelAnalysis.MIN_PER_BIN} are needed.")
    }
    if (analysis.speedRange < MIN_SPEED_RANGE) {
        return AccelFitResult(points, emptyList(), null, "The movements were all at about the same speed, so no curve can be judged.")
    }
    val weights = points.map { 1.0 / (it.standardError * it.standardError + FLOOR * FLOOR) }
    val ys = points.map { it.logGain }
    val nullChi = weights.indices.sumOf { weights[it] * ys[it] * ys[it] }
    val bicPenalty = ln(points.size.toDouble())

    val fits = FAMILIES.mapNotNull { family ->
        val best = family.starts.map { start ->
            NelderMead.minimize(start, step = START_STEP) { objective(family, it, points, weights, ys).first }
        }.minBy { it.value }
        val (chi, logK) = objective(family, best.point, points, weights, ys)
        val curve = family.build(best.point)
        val gains = points.map { exp(logK) * curve.multiplier(it.speed) }
        AccelFit(
            family = family.name,
            curve = curve,
            sensitivityFactor = exp(logK),
            chiSquare = chi,
            nullChiSquare = nullChi,
            bicGain = nullChi - (chi + (family.parameters + 1) * bicPenalty),
            gainRange = gains.max() / gains.min(),
        ).takeIf { it.chiSquare.isFinite() }
    }
    val best = fits.maxByOrNull { it.bicGain }
    return when {
        best == null -> AccelFitResult(points, fits, null, "No curve could be fitted.")
        best.bicGain < minBicGain -> AccelFitResult(points, fits, null, "No curve fits clearly better than no acceleration.")
        best.gainRange < minGainRange -> AccelFitResult(points, fits, null, "The best curve would change the gain by less than ${((minGainRange - 1) * 100).toInt()} %.")
        else -> AccelFitResult(points, fits, best, "The ${best.family} curve fits clearly better than no acceleration.")
    }
}

private const val MIN_SPEED_RANGE = 2.0

/** A bin's wanted gain is never trusted to better than this (log scale), however many movements it has. */
private const val FLOOR = 0.02
private const val START_STEP = 0.5

private class Family(val name: String, val parameters: Int, val starts: List<DoubleArray>, val build: (DoubleArray) -> AccelCurve)

private val FAMILIES = listOf(
    Family("classic", 3, listOf(doubleArrayOf(ln(0.05), 2.0, ln(1.8)), doubleArrayOf(ln(0.15), 2.0, ln(0.7)), doubleArrayOf(ln(0.02), 2.5, ln(3.0)))) { t ->
        ClassicCurve(
            acceleration = exp(t[0].coerceIn(ln(0.005), ln(5.0))),
            exponent = t[1].coerceIn(1.1, 4.0),
            cap = exp(t[2].coerceIn(ln(0.3), ln(6.0))),
        )
    },
    Family("natural", 2, listOf(doubleArrayOf(ln(0.1), ln(1.6)), doubleArrayOf(ln(0.2), ln(0.7)), doubleArrayOf(ln(0.05), ln(2.5)))) { t ->
        val limit = exp(t[1].coerceIn(ln(0.3), ln(6.0)))
        NaturalCurve(
            decayRate = exp(t[0].coerceIn(ln(0.01), ln(3.0))),
            // A limit of exactly 1 is no curve at all and Raw Accel's formula divides by it.
            limit = if (limit in 0.98..1.02) 1.02 else limit,
        )
    },
    Family("power", 2, listOf(doubleArrayOf(ln(0.1), 0.3), doubleArrayOf(ln(0.05), 0.6))) { t ->
        PowerCurve(scale = exp(t[0].coerceIn(ln(0.005), ln(2.0))), exponent = t[1].coerceIn(0.05, 1.5), outputOffset = 1.0)
    },
)

/** Weighted misfit of a family at [params] with the best overall factor, and that factor's log. */
private fun objective(family: Family, params: DoubleArray, points: List<GainPoint>, weights: List<Double>, ys: List<Double>): Pair<Double, Double> {
    val curve = family.build(params)
    val residual = points.indices.map { ys[it] - ln(max(curve.multiplier(points[it].speed), MIN_MULTIPLIER)) }
    val logK = weights.indices.sumOf { weights[it] * residual[it] } / weights.sum()
    val chi = weights.indices.sumOf { weights[it] * (residual[it] - logK).let { d -> d * d } }
    return chi to min(max(logK, -MAX_LOG_FACTOR), MAX_LOG_FACTOR)
}

private const val MIN_MULTIPLIER = 1e-3
private const val MAX_LOG_FACTOR = 2.0
