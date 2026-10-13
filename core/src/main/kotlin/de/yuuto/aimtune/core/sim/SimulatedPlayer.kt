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
     * The player's preferred gain as a function of the nominal hand speed in cm/s (1.0 = none).
     * A value above 1 makes the player act as if the sensitivity were lower for that movement.
     * Hook for the acceleration work (M7); the default has no speed dependence.
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
    val trackingMaxHandSpeedCmPerS: Double = 8.0,
    /** Hand noise per control step as a multiple of the flick hand noise (smooth pursuit is less precise than a flick). */
    val trackingNoiseShare: Double = 2.0,
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
    }
}
