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

/** Why the analysis ended as it did; the app turns it into a translated text, [AccelFitResult.reason] is the English one. */
enum class AccelReason { NOT_ENOUGH_MOVEMENTS, NARROW_SPEED_RANGE, NO_FIT, NOT_BETTER_THAN_NONE, SMALL_CHANGE, FITS }

data class AccelFitResult(
    val points: List<GainPoint>,
    val fits: List<AccelFit>,
    /** The curve to try, or null when there is too little data or no curve is clearly better than none. */
    val recommended: AccelFit?,
    val reason: String,
    val reasonCode: AccelReason,
    /** Number the reason's text mentions: movements needed, or the smallest worthwhile change in percent; 0 otherwise. */
    val reasonValue: Int = 0,
)

/**
 * Fits the Raw Accel families (classic, natural, power) to the wanted gain of an [AccelAnalysis]
 * with a free overall factor, and recommends a curve only when it beats "no acceleration" by
 * [minBicGain] BIC points and changes the gain by at least [minGainRange] across the measured speeds.
 * Synchronous, jump and lookup modes are not fitted. See docs/ALGORITHMS.md ("Acceleration").
 */
fun fitAccel(analysis: AccelAnalysis, minBicGain: Double = MIN_BIC_GAIN, minGainRange: Double = MIN_GAIN_RANGE): AccelFitResult {
    val points = analysis.points
    if (points.size < AccelAnalysis.MIN_BINS) {
        val needed = AccelAnalysis.MIN_BINS * AccelAnalysis.MIN_PER_BIN
        return AccelFitResult(points, emptyList(), null, "Not enough movements yet: about $needed are needed.", AccelReason.NOT_ENOUGH_MOVEMENTS, needed)
    }
    if (analysis.speedRange < MIN_SPEED_RANGE) {
        return AccelFitResult(points, emptyList(), null, "The movements were all at about the same speed, so no curve can be judged.", AccelReason.NARROW_SPEED_RANGE)
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
        best == null -> AccelFitResult(points, fits, null, "No curve could be fitted.", AccelReason.NO_FIT)
        best.bicGain < minBicGain -> AccelFitResult(points, fits, null, "No curve fits clearly better than no acceleration.", AccelReason.NOT_BETTER_THAN_NONE)
        best.gainRange < minGainRange -> {
            val percent = ((minGainRange - 1) * 100).toInt()
            AccelFitResult(points, fits, null, "The best curve would change the gain by less than $percent %.", AccelReason.SMALL_CHANGE, percent)
        }
        else -> AccelFitResult(points, fits, best, "The ${best.family} curve fits clearly better than no acceleration.", AccelReason.FITS)
    }
}

/** Slowest to fastest bin; a rising preference slows the hand, and the slowest reaches are not used (AccelAnalysis.MIN_SPEED). */
/**
 * Without the lowest speeds the best BIC gain of 400 simulated players without any preference was at most -1.6
 * (noise x0.7 to x1.6, docs/ASSESSMENT.md B1), and their gain range at most 1.04 in nine of ten; 5 leaves a margin
 * for real data, and the blind test stays the final judge.
 */
const val MIN_BIC_GAIN = 5.0
const val MIN_GAIN_RANGE = 1.04

private const val MIN_SPEED_RANGE = 1.5

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
    // The overall factor is bounded: the curve under test must not smuggle in a different sensitivity (ASSESSMENT.md B2).
    val logK = (weights.indices.sumOf { weights[it] * residual[it] } / weights.sum()).coerceIn(-MAX_LOG_FACTOR, MAX_LOG_FACTOR)
    val chi = weights.indices.sumOf { weights[it] * (residual[it] - logK).let { d -> d * d } }
    return chi to logK
}

private const val MIN_MULTIPLIER = 1e-3
/** The fitted sensitivity factor stays within about +-10 %. */
private val MAX_LOG_FACTOR = ln(1.1)

/**
 * A mild curve for a player who wants to try one although the analysis found none: the gain moves by
 * [strength] (15 % by default) from slow to fast hands, upward or downward, and the low-speed sensitivity stays
 * as it is. It is a hypothesis, not a finding; the blind test decides if it is worth keeping.
 */
fun trialAccel(rising: Boolean, strength: Double = TRIAL_STRENGTH): AccelFit {
    require(strength > 0.0 && strength < 0.9) { "strength must be between 0 and 0.9" }
    val limit = if (rising) 1.0 + strength else 1.0 - strength
    return AccelFit(
        family = "natural (trial)",
        curve = NaturalCurve(decayRate = TRIAL_DECAY, limit = limit),
        sensitivityFactor = 1.0,
        chiSquare = 0.0,
        nullChiSquare = 0.0,
        bicGain = 0.0,
        gainRange = if (rising) limit else 1.0 / limit,
    )
}

const val TRIAL_STRENGTH = 0.15
private const val TRIAL_DECAY = 0.05
