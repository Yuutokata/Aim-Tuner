package de.yuuto.aimtune.core.accel

import de.yuuto.aimtune.core.units.SearchRange
import kotlin.math.abs
import kotlin.math.pow

/**
 * The three things "Refine my Raw Accel curve" changes about a profile the player imported, one at a time, each as a
 * factor around 1 (1 = the imported value). A factor is searched blind over [min]..[max] on a log scale, like
 * [CurveStrength]: the optimizer sees a pseudo cm/360 of `100 * factor`.
 *
 * - [LEVEL]: the output DPI times k, so the whole curve is as much more or less sensitive. Every mode has it.
 * - [HIGH_GAIN]: how much the curve adds at high hand speed. Classic: `capY - 1` times s (cap modes `in_out` and `output`)
 *   or, without a cap output (`input` mode, or `output` with cap 0), the acceleration times `s^(1/(exponent-1))`, which
 *   scales the output that is reached at the cap by s. Natural: `limit - 1` times s, with the decay rate times s as well,
 *   because Raw Accel's natural curve derives its speed scale from `decayRate / |limit - 1|`, and the curve should rise as
 *   fast as before, only to another height.
 * - [TRANSITION]: where the curve starts to rise. Classic: the input offset times t; with an input cap
 *   (`in_out`, `input`) the cap point moves with the offset when the offset grows (`capX * t`), because Raw Accel
 *   refuses a cap below the offset and the rise should keep its shape. Natural: the decay rate divided by t (t > 1 is a
 *   slower, more gradual approach to the limit). Classic with offset 0 has no transition to move.
 *
 * Modes without a meaningful parameter (noaccel, lut) support [LEVEL] only.
 */
enum class RefineParameter(val min: Double, val max: Double) {
    LEVEL(0.85, 1.18),
    HIGH_GAIN(0.75, 1.33),
    TRANSITION(0.5, 2.0),
    ;

    /** The range the optimizer searches, as pseudo cm/360. */
    val searchRange: SearchRange get() = SearchRange(min * PSEUDO_SCALE, max * PSEUDO_SCALE)

    fun toPseudo(factor: Double): Double = factor * PSEUDO_SCALE

    fun fromPseudo(pseudoCm360: Double): Double = pseudoCm360 / PSEUDO_SCALE

    /** True when [factor] is far enough from 1 to be played as a change. */
    fun isChange(factor: Double): Boolean = abs(factor - NEUTRAL) >= NEUTRAL_BAND

    /**
     * True when the credible range [low]..[high] of the best [factor] clearly excludes 1 (no change). Both directions need
     * the same evidence here: unlike the strength search there is a curve to compare with, not the plain mouse.
     */
    fun excludesNeutral(factor: Double, low: Double, high: Double): Boolean = isChange(factor) && (low > NEUTRAL || high < NEUTRAL)

    companion object {
        const val NEUTRAL = 1.0

        /** Factors closer to 1 than this are no change. */
        const val NEUTRAL_BAND = 0.02
        private const val PSEUDO_SCALE = 100.0
    }
}

/** The smallest `capY` / `limit` an adjusted profile gets, so that a falling curve scaled up never reaches or crosses 0. */
private const val MIN_HIGH_OUTPUT = 0.05

/** True when the profile has something [parameter] could change. */
fun RawAccelProfile.supports(parameter: RefineParameter): Boolean = when (parameter) {
    RefineParameter.LEVEL -> true
    RefineParameter.HIGH_GAIN -> when (args.mode) {
        RawAccelMode.CLASSIC -> hasHighOutput()
        RawAccelMode.NATURAL -> args.limit != 1.0
        RawAccelMode.LUT, RawAccelMode.NOACCEL -> false
    }
    RefineParameter.TRANSITION -> when (args.mode) {
        RawAccelMode.CLASSIC -> args.inputOffset > 0.0 && hasHighOutput()
        RawAccelMode.NATURAL -> args.limit != 1.0
        RawAccelMode.LUT, RawAccelMode.NOACCEL -> false
    }
}

/** A classic curve whose cap output is the point 1, or that has no effect at all, has no high gain to scale. */
private fun RawAccelProfile.hasHighOutput(): Boolean =
    if (args.capMode == RawAccelCapMode.IN_OUT || (args.capMode == RawAccelCapMode.OUTPUT && args.capY > 0.0)) args.capY != 1.0 else true

/**
 * The profile with [parameter] changed by [factor] (1 is the profile itself). The result always passes Raw Accel's validation
 * (`RawAccelArgs`): a scaled cap output is kept above [MIN_HIGH_OUTPUT], and the cap point never falls below the offset.
 * @throws IllegalArgumentException if the profile does not [supports] the parameter or [factor] is not positive.
 */
fun RawAccelProfile.adjusted(parameter: RefineParameter, factor: Double): RawAccelProfile {
    require(factor > 0.0 && factor.isFinite()) { "the factor must be positive" }
    require(supports(parameter)) { "${args.mode.json} profiles do not support $parameter" }
    if (factor == 1.0) return this
    return when (parameter) {
        RefineParameter.LEVEL -> copy(outputDpi = outputDpi * factor)
        RefineParameter.HIGH_GAIN -> copy(args = args.withHighGain(factor))
        RefineParameter.TRANSITION -> copy(args = args.withTransition(factor))
    }
}

/** The profile with several adjustments applied in the order of [RefineParameter]; a parameter it does not support is skipped. */
fun RawAccelProfile.adjusted(factors: Map<RefineParameter, Double>): RawAccelProfile =
    RefineParameter.entries.fold(this) { profile, parameter ->
        val factor = factors[parameter]
        if (factor == null || !profile.supports(parameter)) profile else profile.adjusted(parameter, factor)
    }

private fun RawAccelArgs.withHighGain(s: Double): RawAccelArgs = when (mode) {
    RawAccelMode.CLASSIC -> {
        val capsOutput = capMode == RawAccelCapMode.IN_OUT || (capMode == RawAccelCapMode.OUTPUT && capY > 0.0)
        if (capsOutput) copy(capY = (1.0 + (capY - 1.0) * s).coerceAtLeast(MIN_HIGH_OUTPUT))
        else copy(acceleration = acceleration * s.pow(1.0 / (exponentClassic - 1.0)))
    }
    RawAccelMode.NATURAL -> copy(limit = (1.0 + (limit - 1.0) * s).coerceAtLeast(MIN_HIGH_OUTPUT), decayRate = decayRate * s)
    else -> error("unreachable: $mode has no high gain")
}

private fun RawAccelArgs.withTransition(t: Double): RawAccelArgs = when (mode) {
    RawAccelMode.CLASSIC -> {
        val offset = inputOffset * t
        val movesCap = capMode != RawAccelCapMode.OUTPUT && capX > 0.0 && t > 1.0
        copy(inputOffset = offset, capX = if (movesCap) capX * t else capX)
    }
    RawAccelMode.NATURAL -> copy(decayRate = decayRate / t)
    else -> error("unreachable: $mode has no transition")
}

/**
 * What one arm of a round plays for a profile: the curve and the cm/360 that gives the game sensitivity the profile would
 * give in the game. Raw Accel multiplies the counts by `outputDpi / deviceDpi` after the curve, so a bigger output DPI is
 * the same as a smaller cm/360; [AccelProcessor] sees the DPI of the settings, which an import requires to equal the file's.
 */
data class PlayedProfile(val curve: RawAccelCurve, val cm360: Double)

/** The arm of this profile when the player's game sensitivity is [gameCm360] (at the device DPI, output DPI factor 1). */
fun RawAccelProfile.played(gameCm360: Double): PlayedProfile {
    require(gameCm360 > 0.0) { "cm360 must be positive" }
    return PlayedProfile(curve, gameCm360 / (outputDpi / effectiveDeviceDpi))
}
