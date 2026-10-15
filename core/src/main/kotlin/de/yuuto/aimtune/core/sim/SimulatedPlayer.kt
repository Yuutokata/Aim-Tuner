package de.yuuto.aimtune.core.sim

/**
 * Parameters of the synthetic aimer used to verify the pipeline without a human. See
 * docs/ALGORITHMS.md ("Verification harness") for the model.
 *
 * The player is a hand with limited precision and speed: the endpoint error of a reach is
 * `sqrt((signalNoise * amplitude)^2 + (handNoiseCm * 360 / cm360)^2)` degrees (signal-dependent
 * noise plus a fixed hand precision, which hurts at high sensitivity), and the movement time adds
 * `distance / maxHandSpeed` (which hurts at low sensitivity). Their balance gives an interior
 * optimum cm/360 that [withOptimum] places where a test wants it.
 */
data class SimulatedPlayer(
    val reactionMeanMs: Double = 230.0,
    val reactionSdMs: Double = 30.0,
    val fittsInterceptMs: Double = 60.0,
    val fittsSlopeMs: Double = 90.0,
    val signalNoise: Double = 0.03,
    val handNoiseCm: Double = 0.07,
    val maxHandSpeedCmPerS: Double = 30.0,
    /** Mean fraction by which first reaches fall short of the target. */
    val undershootBias: Double = 0.03,
    val feedbackDelayMs: Double = 140.0,
    val clickDelayMs: Double = 60.0,
    val clickDelaySdMs: Double = 15.0,
    /** Multiplies every noise term; the sweeps use it as the "noise level". */
    val noiseLevel: Double = 1.0,
    /** Noise and times grow by this fraction per minute of session time. */
    val fatiguePerMinute: Double = 0.0,
    /** Moves the optimum: the player behaves as if cm/360 were divided by this. 1.0 is the base model. */
    val optimumScale: Double = 1.0,
    /**
     * The gain the player expects from the mouse as a function of the hand speed in inches per
     * second (peak speed of a movement; 1.0 = what the plain sensitivity gives). The player plans
     * its hand movements with it: where it expects more gain than it gets it undershoots, where it
     * expects less it overshoots, and an acceleration curve that delivers the expected gain removes
     * the error. This is what the acceleration analysis has to find. Keep it 1.0 at low speeds, as
     * the Raw Accel curves do.
     */
    val gainPreference: (Double) -> Double = { 1.0 },
    val maxCorrections: Int = 8,
    /** Reaction to a target that was already visible (switching), instead of one that just appeared. */
    val reacquireMs: Double = 110.0,
    /** Delay between what the target does and what the hand reacts to while tracking. */
    val trackingLagMs: Double = 150.0,
    /** Share of the target's motion during that delay the player anticipates (0 = none, 1 = perfect). */
    val trackingPrediction: Double = 0.8,
    /** Fraction of the remaining error corrected per control step (every 40 ms). */
    val trackingGain: Double = 0.7,
    /** Fastest the hand can move while tracking smoothly; slower than a flick, so a high cm/360 falls behind fast targets. */
    val trackingMaxHandSpeedCmPerS: Double = 4.0,
    /** Hand noise per control step as a multiple of the flick hand noise (smooth pursuit is less precise than a flick). */
    val trackingNoiseShare: Double = 2.0,
    /** How far beyond the minimum the player turns away from a flash, in degrees; people do not turn just enough. */
    val flashTurnMarginDegrees: Double = 10.0,
    /** Reaction to the enemy that appears when a flash pops; the pop is expected, so this is quicker than a surprise. */
    val flashReturnReactionMs: Double = 170.0,
    /** Reaction to a flash that becomes visible: the player expects flashes and is ready to turn, so this is quicker than a surprise. */
    val flashTurnReactionMs: Double = 180.0,
) {
    init {
        require(noiseLevel >= 0.0 && fatiguePerMinute >= 0.0 && optimumScale > 0.0) { "invalid player parameters" }
        require(handNoiseCm > 0.0 && maxHandSpeedCmPerS > 0.0) { "hand parameters must be positive" }
    }

    /** The same player with its optimum at [cm360] (valid because all effects depend on `cm360 / optimumScale` only). */
    fun withOptimum(cm360: Double): SimulatedPlayer {
        require(cm360 > 0.0) { "cm360 must be positive" }
        return copy(optimumScale = cm360 / BASE_OPTIMUM_CM360)
    }

    /** The optimum this player was configured for. */
    val declaredOptimumCm360: Double get() = BASE_OPTIMUM_CM360 * optimumScale

    companion object {
        /**
         * Optimum of the default parameters, measured with the reference sweep (a test guards it).
         * The tolerance of that test is the accuracy of this constant.
         */
        const val BASE_OPTIMUM_CM360 = 50.0

        /**
         * Optimum of the default parameters with the head-sized targets of the game profiles (0.4-1.9 degrees, flick
         * scenario), measured on a 40-640 cm/360 sweep (docs/ASSESSMENT.md C). The default hand was calibrated on
         * large targets and wants about 156 cm/360 there, far outside what people use (pros 33-65 cm/360, controlled
         * study 20-80); see [forHeadSizedTargets].
         */
        const val BASE_OPTIMUM_HEAD_TARGETS_CM360 = 156.0

        /**
         * A hand whose optimum with the profile's head-sized targets lies at [optimumCm360] (about where people are).
         * All effects depend on `cm360 / optimumScale` only, so scaling the default hand is exact.
         */
        fun forHeadSizedTargets(optimumCm360: Double = 50.0): SimulatedPlayer {
            require(optimumCm360 > 0.0) { "cm360 must be positive" }
            return SimulatedPlayer(optimumScale = optimumCm360 / BASE_OPTIMUM_HEAD_TARGETS_CM360)
        }
    }
}
