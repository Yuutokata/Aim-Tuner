package de.yuuto.aimtune.core.accel

import de.yuuto.aimtune.core.units.SearchRange
import kotlin.math.abs

/**
 * One acceleration curve family with a single parameter, its strength: the gain at high hand speed
 * relative to low speed (1 = no curve, 1.2 = 20 % more sensitivity for fast movements, 0.8 = 20 % less).
 * The curve is Raw Accel's natural mode, which starts at gain 1 and levels off by itself; its decay is fixed.
 *
 * The strength is searched blind with the same optimizer that finds the sensitivity, over a pseudo
 * cm/360 of `100 * strength` (so the search is on a log scale and symmetric in rising and falling), instead
 * of being inferred from how first movements miss: that inference sees only a narrow band of hand speeds
 * (docs/ASSESSMENT.md, "Follow-up on a more precise curve").
 */
object CurveStrength {
    const val MIN = 0.7
    const val MAX = 1.45

    /** The strength of "no curve". */
    const val NEUTRAL = 1.0

    /** Strengths closer to 1 than this are played as no curve at all. */
    const val PLAIN_BAND = 0.02

    private const val DECAY = 0.05
    private const val PSEUDO_SCALE = 100.0

    /** The range the optimizer searches, as pseudo cm/360. */
    val searchRange = SearchRange(MIN * PSEUDO_SCALE, MAX * PSEUDO_SCALE)

    fun toPseudo(strength: Double): Double = strength * PSEUDO_SCALE

    fun fromPseudo(pseudoCm360: Double): Double = pseudoCm360 / PSEUDO_SCALE

    /** The curve of [strength], or null for "no curve" when it is within [PLAIN_BAND] of 1. */
    fun curve(strength: Double): AccelCurve? {
        require(strength > 0.0 && strength.isFinite()) { "strength must be positive" }
        return if (abs(strength - NEUTRAL) < PLAIN_BAND) null else NaturalCurve(DECAY, strength)
    }

    /** True when [strength] would be played as a real curve. */
    fun isCurve(strength: Double): Boolean = abs(strength - NEUTRAL) >= PLAIN_BAND
}

/** The curve of [strength] as a candidate for the blind A/B test; the sensitivity factor stays 1 (the search included it). */
fun strengthAccel(strength: Double): AccelFit {
    require(CurveStrength.isCurve(strength)) { "a strength within ${CurveStrength.PLAIN_BAND} of 1 is no curve" }
    val curve = checkNotNull(CurveStrength.curve(strength))
    return AccelFit(
        family = "natural (strength search)",
        curve = curve,
        sensitivityFactor = 1.0,
        chiSquare = 0.0,
        nullChiSquare = 0.0,
        bicGain = 0.0,
        gainRange = if (strength > 1.0) strength else 1.0 / strength,
    )
}
