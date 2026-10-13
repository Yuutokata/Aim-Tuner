package de.yuuto.aimtune.core.sim

import de.yuuto.aimtune.core.accel.AccelCurve
import de.yuuto.aimtune.core.accel.AccelProcessor
import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.math.Vec3
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.scenario.Target
import de.yuuto.aimtune.core.units.degreesPerCount
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/** What the aimer does in one poll: mouse counts to report (0, 0 = none) and whether it clicks. */
data class AimerStep(val dx: Int, val dy: Int, val click: Boolean) {
    companion object {
        val IDLE = AimerStep(0, 0, false)
    }
}

/**
 * Deterministic synthetic aimer. Per target it waits a reaction time, makes a minimum-jerk primary
 * reach with a noisy endpoint, then repeatedly checks its error after a feedback delay and corrects
 * until it is on the target, and clicks. With several targets visible it goes for the one nearest
 * to its aim and sticks to it. For [ScenarioKind.TRACKING] it follows the target instead (see
 * [trackStep]) and never clicks. It never reads a clock: the caller passes the time into
 * [step], which the headless runner and `SimulatedMouseSource` both do. The aimer assumes the view
 * starts at yaw 0 / pitch 0 when [reset] is called, like the trainer.
 */
class SimulatedAimer(
    private val player: SimulatedPlayer,
    dpi: Double,
    private val cm360: Double,
    seed: Long,
    private val kind: ScenarioKind = ScenarioKind.FLICK,
    accel: AccelCurve? = null,
) {
    /**
     * A planned movement: [dYaw]/[dPitch] degrees the player intends, delivered by a hand movement
     * that is [handScale] times the hand movement a linear mouse would need. The player plans with
     * the gain it expects, so [handScale] is 1 divided by that expected gain.
     */
    private class Reach(
        val startNanos: Long,
        val durationNanos: Long,
        val dYaw: Double,
        val dPitch: Double,
        val handScale: Double = 1.0,
    ) {
        val endNanos: Long get() = startNanos + durationNanos
    }

    private data class Offset(val x: Double, val y: Double)

    private class Observation(val timeNanos: Long, val center: Vec3)

    private enum class Mode { NO_TARGET, REACTING, MOVING, SETTLING, CLICKING }

    private val random = Random(seed)
    private val degPerCount = degreesPerCount(dpi, cm360)
    private val dpiValue = dpi
    private val accelCurve = accel
    private val accelProcessor = accel?.let { AccelProcessor(it, dpi) }
    private val reaches = ArrayList<Reach>()
    private var baseYaw = 0.0
    private var basePitch = 0.0
    private var baseHandYaw = 0.0
    private var baseHandPitch = 0.0

    /** What the player sees: the emitted counts run through the acceleration into a view, like the trainer does. */
    private var trueYaw = 0.0
    private var truePitch = 0.0
    private var emittedX = 0L
    private var emittedY = 0L
    private var mode = Mode.NO_TARGET
    private var targetId = -1
    private var wakeNanos = 0L
    private var roundStartNanos = 0L
    private var sessionOffsetNanos = 0L
    private var corrections = 0
    private val seenTargets = HashSet<Int>()
    private val observations = ArrayDeque<Observation>()
    private var nextControlNanos = 0L

    /** Starts a round: the view is at (0, 0); [sessionOffsetNanos] is the time already spent in the session (fatigue). */
    fun reset(nowNanos: Long, sessionOffsetNanos: Long = 0L) {
        reaches.clear()
        baseYaw = 0.0
        basePitch = 0.0
        baseHandYaw = 0.0
        baseHandPitch = 0.0
        trueYaw = 0.0
        truePitch = 0.0
        accelProcessor?.reset()
        emittedX = 0
        emittedY = 0
        mode = Mode.NO_TARGET
        targetId = -1
        roundStartNanos = nowNanos
        this.sessionOffsetNanos = sessionOffsetNanos
        seenTargets.clear()
        observations.clear()
        nextControlNanos = nowNanos
    }

    fun step(nowNanos: Long, target: Target?): AimerStep = step(nowNanos, listOfNotNull(target))

    /** One poll with all [targets] currently visible. */
    fun step(nowNanos: Long, targets: List<Target>): AimerStep {
        if (kind == ScenarioKind.TRACKING) return trackStep(nowNanos, targets.firstOrNull())
        val target = choose(nowNanos, targets)
        val seenBefore = target != null && target.id in seenTargets
        targets.forEach { seenTargets += it.id }
        return clickStep(nowNanos, target, seenBefore)
    }

    /** Keeps the current target while it is there, otherwise the one nearest to the aim. */
    private fun choose(nowNanos: Long, targets: List<Target>): Target? {
        targets.firstOrNull { it.id == targetId }?.let { return it }
        return targets.minByOrNull { target -> errorTo(nowNanos, target).let { hypot(it.x, it.y) } }
    }

    /**
     * Follows the target like a person does: it sees the target's position [SimulatedPlayer.trackingLagMs]
     * late, partly anticipates the motion during that delay, and every 40 ms corrects a share of the
     * remaining error. Corrections are noisy and limited by the hand's smooth speed.
     */
    private fun trackStep(nowNanos: Long, target: Target?): AimerStep {
        if (target == null) return emitCounts(nowNanos, click = false)
        observations.addLast(Observation(nowNanos, target.center))
        while (observations.size > 1 && observations.first().timeNanos < nowNanos - ms(TRACK_HISTORY_MS)) observations.removeFirst()
        if (nowNanos >= nextControlNanos) {
            controlTracking(nowNanos)
            nextControlNanos = nowNanos + ms(TRACK_STEP_MS)
        }
        return emitCounts(nowNanos, click = false)
    }

    private fun controlTracking(nowNanos: Long) {
        val fatigue = fatigue(nowNanos)
        val lagNanos = ms(player.trackingLagMs * fatigue)
        val delayed = observationAt(nowNanos - lagNanos) ?: return
        val earlier = observationAt(nowNanos - lagNanos - ms(VELOCITY_WINDOW_MS)) ?: delayed
        val seen = anglesOf(delayed.center)
        val before = anglesOf(earlier.center)
        val seconds = (delayed.timeNanos - earlier.timeNanos) / NANOS_PER_SECOND
        // The hand needs about half a reach to deliver a command, so that delay is anticipated too.
        val lead = player.trackingPrediction * (lagNanos + ms(TRACK_REACH_MS) / 2) / NANOS_PER_SECOND
        val predictedYaw = if (seconds > 0.0) seen.first + (seen.first - before.first) / seconds * lead else seen.first
        val predictedPitch = if (seconds > 0.0) seen.second + (seen.second - before.second) / seconds * lead else seen.second

        val (yaw, pitch) = believedAngles(nowNanos)
        val errorX = (predictedYaw - yaw) * cos(Math.toRadians(predictedPitch))
        val errorY = predictedPitch - pitch
        var moveX = errorX * player.trackingGain
        var moveY = errorY * player.trackingGain
        var amplitude = hypot(moveX, moveY)

        val stepSeconds = TRACK_STEP_MS / MS_PER_S
        val (expected, delivered) = gains(amplitude, stepSeconds, peak = false)
        val effectiveCm360 = cm360 / (player.optimumScale * delivered)
        val reachLimit = player.trackingMaxHandSpeedCmPerS * stepSeconds * FULL_TURN / effectiveCm360
        if (amplitude > reachLimit) {
            moveX *= reachLimit / amplitude
            moveY *= reachLimit / amplitude
            amplitude = reachLimit
        }
        val sigma = player.noiseLevel * fatigue *
            sqrt(sq(player.signalNoise * amplitude) + sq(player.handNoiseCm * player.trackingNoiseShare * FULL_TURN / effectiveCm360))
        moveX += sigma * gaussian()
        moveY += sigma * gaussian()
        foldFinished(nowNanos)
        reaches += Reach(nowNanos, ms(TRACK_REACH_MS), moveX / cos(Math.toRadians(pitch + moveY / 2.0)), moveY, 1.0 / expected)
    }

    /**
     * The gain the player expects and the gain the mouse delivers for a movement of [amplitude]
     * degrees in [seconds]: both depend on the hand speed, which in turn depends on the hand
     * movement the player plans with its expected gain, so the speed is found by iteration. The
     * speed is the peak speed of a minimum-jerk movement when [peak] is set, else the average.
     */
    private fun gains(amplitude: Double, seconds: Double, peak: Boolean): Pair<Double, Double> {
        var expected = 1.0
        var delivered = accelCurve?.multiplier(0.0) ?: 1.0
        repeat(GAIN_ITERATIONS) {
            val handInches = amplitude / (degPerCount * expected) / dpiValue
            val speed = (if (peak) PEAK_TO_MEAN_SPEED else 1.0) * handInches / seconds
            expected = player.gainPreference(speed)
            delivered = accelCurve?.multiplier(speed) ?: 1.0
        }
        return expected to delivered
    }

    /** Where the aim will be once every reach under way has finished, as the player believes. */
    private fun believedAngles(nowNanos: Long): Pair<Double, Double> {
        var yaw = trueYaw
        var pitch = truePitch
        for (r in reaches) {
            val remaining = 1.0 - minimumJerk((nowNanos - r.startNanos).toDouble() / r.durationNanos)
            yaw += r.dYaw * remaining
            pitch += r.dPitch * remaining
        }
        return yaw to pitch
    }

    private fun observationAt(timeNanos: Long): Observation? = observations.lastOrNull { it.timeNanos <= timeNanos }

    private fun anglesOf(center: Vec3): Pair<Double, Double> =
        Math.toDegrees(atan2(center.x, -center.z)) to Math.toDegrees(asin(center.y / center.length))

    private fun clickStep(nowNanos: Long, target: Target?, seenBefore: Boolean): AimerStep {
        if (target == null) {
            mode = Mode.NO_TARGET
            return emitCounts(nowNanos, click = false)
        }
        if (target.id != targetId) beginTarget(nowNanos, target, seenBefore)
        var click = false
        when (mode) {
            Mode.REACTING -> if (nowNanos >= wakeNanos) planReach(nowNanos, target, primary = true)
            Mode.MOVING -> if (nowNanos >= lastEnd()) {
                mode = Mode.SETTLING
                wakeNanos = nowNanos + ms(player.feedbackDelayMs * fatigue(nowNanos))
            }
            Mode.SETTLING -> if (nowNanos >= wakeNanos) decide(nowNanos, target)
            Mode.CLICKING -> if (nowNanos >= wakeNanos) {
                click = true
                twitch(nowNanos)
                // After a miss the target stays; look again shortly.
                mode = Mode.SETTLING
                wakeNanos = nowNanos + ms(MISS_RECHECK_MS)
            }
            Mode.NO_TARGET -> Unit
        }
        return emitCounts(nowNanos, click)
    }

    private fun beginTarget(nowNanos: Long, target: Target, seenBefore: Boolean) {
        targetId = target.id
        corrections = 0
        mode = Mode.REACTING
        val mean = if (seenBefore) player.reacquireMs else player.reactionMeanMs
        val reaction = mean + player.reactionSdMs * gaussian()
        wakeNanos = nowNanos + ms(max(MIN_REACTION_MS * if (seenBefore) REACQUIRE_FLOOR else 1.0, reaction) * fatigue(nowNanos))
    }

    private fun decide(nowNanos: Long, target: Target) {
        val error = errorTo(nowNanos, target)
        val onTarget = hypot(error.x, error.y) <= angularRadius(target)
        if (onTarget || corrections >= player.maxCorrections) {
            mode = Mode.CLICKING
            val delay = max(0.0, player.clickDelayMs + player.clickDelaySdMs * gaussian())
            wakeNanos = nowNanos + ms(delay * fatigue(nowNanos))
        } else {
            corrections++
            planReach(nowNanos, target, primary = false)
        }
    }

    private fun planReach(nowNanos: Long, target: Target, primary: Boolean) {
        foldFinished(nowNanos)
        val error = errorTo(nowNanos, target)
        val amplitude = hypot(error.x, error.y)
        val width = max(2.0 * angularRadius(target), MIN_WIDTH_DEGREES)
        val fatigue = fatigue(nowNanos)
        val fitts = (player.fittsInterceptMs + player.fittsSlopeMs * log2(1.0 + amplitude / width)) *
            (if (primary) 1.0 else CORRECTION_SPEEDUP)
        // Time, noise and gain depend on each other through the hand speed; three rounds settle them.
        var expected = 1.0
        var delivered = accelCurve?.multiplier(0.0) ?: 1.0
        var effectiveCm360 = cm360 / (player.optimumScale * delivered)
        var durationMs = MIN_REACH_MS
        repeat(GAIN_ITERATIONS) {
            effectiveCm360 = cm360 / (player.optimumScale * delivered)
            val handCm = amplitude * effectiveCm360 / FULL_TURN
            durationMs = max(MIN_REACH_MS, fitts + handCm / player.maxHandSpeedCmPerS * MS_PER_S) * fatigue
            val gains = gains(amplitude, durationMs / MS_PER_S, peak = true)
            expected = gains.first
            delivered = gains.second
        }
        val sigma = player.noiseLevel * fatigue *
            sqrt(sq(player.signalNoise * amplitude) + sq(player.handNoiseCm * FULL_TURN / effectiveCm360))
        val gain = if (primary) 1.0 - player.undershootBias else 1.0
        val moveX = error.x * gain + sigma * gaussian()
        val moveY = error.y * gain + sigma * gaussian()
        // Screen-plane x is yaw scaled by cos(pitch); convert back to a yaw change.
        val pitchMid = truePitch + moveY / 2.0
        reaches += Reach(nowNanos, ms(durationMs), moveX / cos(Math.toRadians(pitchMid)), moveY, 1.0 / expected)
        mode = Mode.MOVING
    }

    /** Hand tremor at the moment of the click: a small displacement that can turn a marginal hit into a miss. */
    private fun twitch(nowNanos: Long) {
        val effectiveCm360 = cm360 / player.optimumScale
        val sigma = player.noiseLevel * fatigue(nowNanos) * TWITCH_FRACTION * player.handNoiseCm * FULL_TURN / effectiveCm360
        foldFinished(nowNanos)
        reaches += Reach(nowNanos, ms(TWITCH_MS), sigma * gaussian(), sigma * gaussian())
    }

    private fun fatigue(nowNanos: Long): Double {
        val minutes = (sessionOffsetNanos + nowNanos - roundStartNanos) / NANOS_PER_MINUTE
        return 1.0 + player.fatiguePerMinute * minutes
    }

    /** Remaining offset to the target on the screen plane in degrees, from the view the player sees. */
    private fun errorTo(nowNanos: Long, target: Target): Offset {
        val yaw = trueYaw
        val pitch = truePitch
        val c = target.center
        val targetYaw = Math.toDegrees(atan2(c.x, -c.z))
        val targetPitch = Math.toDegrees(asin(c.y / c.length))
        return Offset((targetYaw - yaw) * cos(Math.toRadians(targetPitch)), targetPitch - pitch)
    }

    private fun angularRadius(target: Target): Double = Math.toDegrees(atan(target.radius / target.center.length))

    /** The hand position at [nowNanos] in degrees a linear mouse would have turned, summed over the reaches. */
    private fun handPosition(nowNanos: Long): Pair<Double, Double> {
        var yaw = baseHandYaw
        var pitch = baseHandPitch
        for (r in reaches) {
            val s = minimumJerk((nowNanos - r.startNanos).toDouble() / r.durationNanos)
            yaw += r.dYaw * r.handScale * s
            pitch += r.dPitch * r.handScale * s
        }
        return yaw to pitch
    }

    private fun foldFinished(nowNanos: Long) {
        val done = reaches.filter { it.endNanos <= nowNanos }
        for (r in done) {
            baseYaw += r.dYaw
            basePitch += r.dPitch
            baseHandYaw += r.dYaw * r.handScale
            baseHandPitch += r.dPitch * r.handScale
        }
        reaches.removeAll(done.toSet())
    }

    private fun lastEnd(): Long = reaches.maxOfOrNull { it.endNanos } ?: 0L

    private fun emitCounts(nowNanos: Long, click: Boolean): AimerStep {
        val (yaw, pitch) = handPosition(nowNanos)
        val x = (yaw / degPerCount).roundToLong()
        val y = (pitch / degPerCount).roundToLong()
        val dx = (x - emittedX).toInt()
        val dy = -(y - emittedY).toInt() // mouse down (positive dy) looks down
        emittedX = x
        emittedY = y
        if (dx != 0 || dy != 0) {
            val scale = accelProcessor?.scaleFor(MouseEvent(nowNanos, dx, dy)) ?: 1.0
            trueYaw += dx * scale * degPerCount
            truePitch -= dy * scale * degPerCount
        }
        return if (dx == 0 && dy == 0 && !click) AimerStep.IDLE else AimerStep(dx, dy, click)
    }

    private fun gaussian(): Double {
        val u1 = 1.0 - random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * random.nextDouble())
    }

    private fun ms(value: Double): Long = (value * NANOS_PER_MS).toLong()

    private fun sq(v: Double) = v * v

    private fun log2(v: Double) = ln(v) / LN2

    private fun minimumJerk(tau: Double): Double {
        val t = tau.coerceIn(0.0, 1.0)
        return t * t * t * (10 + t * (-15 + 6 * t))
    }

    private companion object {
        const val FULL_TURN = 360.0
        const val MS_PER_S = 1000.0
        const val NANOS_PER_MS = 1_000_000.0
        const val NANOS_PER_MINUTE = 60_000_000_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MIN_REACTION_MS = 120.0

        /** A target that was already in view may be re-acquired faster than the usual minimum reaction. */
        const val REACQUIRE_FLOOR = 0.5
        const val TRACK_STEP_MS = 40.0
        const val TRACK_REACH_MS = 80.0
        const val TRACK_HISTORY_MS = 600.0
        const val VELOCITY_WINDOW_MS = 100.0
        const val GAIN_ITERATIONS = 3

        /** Peak speed of a minimum-jerk movement relative to its average speed. */
        const val PEAK_TO_MEAN_SPEED = 1.875
        const val MIN_REACH_MS = 60.0
        const val MIN_WIDTH_DEGREES = 0.3
        const val CORRECTION_SPEEDUP = 0.6
        const val MISS_RECHECK_MS = 100.0
        const val TWITCH_FRACTION = 0.25
        const val TWITCH_MS = 30.0
        val LN2 = ln(2.0)
    }
}
