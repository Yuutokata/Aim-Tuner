package de.yuuto.aimtune.core.sim

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
 * until it is on the target, and clicks. It never reads a clock: the caller passes the time into
 * [step], which the headless runner and `SimulatedMouseSource` both do. The aimer assumes the view
 * starts at yaw 0 / pitch 0 when [reset] is called, like the trainer.
 */
class SimulatedAimer(
    private val player: SimulatedPlayer,
    dpi: Double,
    private val cm360: Double,
    seed: Long,
) {
    private class Reach(val startNanos: Long, val durationNanos: Long, val dYaw: Double, val dPitch: Double) {
        val endNanos: Long get() = startNanos + durationNanos
    }

    private data class Offset(val x: Double, val y: Double)

    private enum class Mode { NO_TARGET, REACTING, MOVING, SETTLING, CLICKING }

    private val random = Random(seed)
    private val degPerCount = degreesPerCount(dpi, cm360)
    private val reaches = ArrayList<Reach>()
    private var baseYaw = 0.0
    private var basePitch = 0.0
    private var emittedX = 0L
    private var emittedY = 0L
    private var mode = Mode.NO_TARGET
    private var targetId = -1
    private var wakeNanos = 0L
    private var roundStartNanos = 0L
    private var sessionOffsetNanos = 0L
    private var corrections = 0

    /** Starts a round: the view is at (0, 0); [sessionOffsetNanos] is the time already spent in the session (fatigue). */
    fun reset(nowNanos: Long, sessionOffsetNanos: Long = 0L) {
        reaches.clear()
        baseYaw = 0.0
        basePitch = 0.0
        emittedX = 0
        emittedY = 0
        mode = Mode.NO_TARGET
        targetId = -1
        roundStartNanos = nowNanos
        this.sessionOffsetNanos = sessionOffsetNanos
    }

    fun step(nowNanos: Long, target: Target?): AimerStep {
        if (target == null) {
            mode = Mode.NO_TARGET
            return emitCounts(nowNanos, click = false)
        }
        if (target.id != targetId) beginTarget(nowNanos, target)
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

    private fun beginTarget(nowNanos: Long, target: Target) {
        targetId = target.id
        corrections = 0
        mode = Mode.REACTING
        val reaction = player.reactionMeanMs + player.reactionSdMs * gaussian()
        wakeNanos = nowNanos + ms(max(MIN_REACTION_MS, reaction) * fatigue(nowNanos))
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
        val scale = player.optimumScale * player.gainPreference(nominalSpeedCmPerS(amplitude, width))
        val effectiveCm360 = cm360 / scale
        val handCm = amplitude * effectiveCm360 / FULL_TURN
        val sigma = player.noiseLevel * fatigue *
            sqrt(sq(player.signalNoise * amplitude) + sq(player.handNoiseCm * FULL_TURN / effectiveCm360))
        val gain = if (primary) 1.0 - player.undershootBias else 1.0
        val moveX = error.x * gain + sigma * gaussian()
        val moveY = error.y * gain + sigma * gaussian()
        val fitts = (player.fittsInterceptMs + player.fittsSlopeMs * log2(1.0 + amplitude / width)) *
            (if (primary) 1.0 else CORRECTION_SPEEDUP)
        val travelMs = handCm / player.maxHandSpeedCmPerS * MS_PER_S
        val durationMs = max(MIN_REACH_MS, fitts + travelMs) * fatigue
        // Screen-plane x is yaw scaled by cos(pitch); convert back to a yaw change.
        val pitchMid = currentAngles(nowNanos).second + moveY / 2.0
        reaches += Reach(nowNanos, ms(durationMs), moveX / cos(Math.toRadians(pitchMid)), moveY)
        mode = Mode.MOVING
    }

    /** Hand tremor at the moment of the click: a small displacement that can turn a marginal hit into a miss. */
    private fun twitch(nowNanos: Long) {
        val effectiveCm360 = cm360 / player.optimumScale
        val sigma = player.noiseLevel * fatigue(nowNanos) * TWITCH_FRACTION * player.handNoiseCm * FULL_TURN / effectiveCm360
        foldFinished(nowNanos)
        reaches += Reach(nowNanos, ms(TWITCH_MS), sigma * gaussian(), sigma * gaussian())
    }

    private fun nominalSpeedCmPerS(amplitude: Double, width: Double): Double {
        val handCm = amplitude * cm360 / FULL_TURN
        val ms = player.fittsInterceptMs + player.fittsSlopeMs * log2(1.0 + amplitude / width)
        return handCm / ms * MS_PER_S
    }

    private fun fatigue(nowNanos: Long): Double {
        val minutes = (sessionOffsetNanos + nowNanos - roundStartNanos) / NANOS_PER_MINUTE
        return 1.0 + player.fatiguePerMinute * minutes
    }

    /** Remaining offset to the target on the screen plane in degrees, from the aimer's own continuous orientation. */
    private fun errorTo(nowNanos: Long, target: Target): Offset {
        val (yaw, pitch) = currentAngles(nowNanos)
        val c = target.center
        val targetYaw = Math.toDegrees(atan2(c.x, -c.z))
        val targetPitch = Math.toDegrees(asin(c.y / c.length))
        return Offset((targetYaw - yaw) * cos(Math.toRadians(targetPitch)), targetPitch - pitch)
    }

    private fun angularRadius(target: Target): Double = Math.toDegrees(atan(target.radius / target.center.length))

    private fun currentAngles(nowNanos: Long): Pair<Double, Double> {
        var yaw = baseYaw
        var pitch = basePitch
        for (r in reaches) {
            val s = minimumJerk((nowNanos - r.startNanos).toDouble() / r.durationNanos)
            yaw += r.dYaw * s
            pitch += r.dPitch * s
        }
        return yaw to pitch
    }

    private fun foldFinished(nowNanos: Long) {
        val done = reaches.filter { it.endNanos <= nowNanos }
        for (r in done) {
            baseYaw += r.dYaw
            basePitch += r.dPitch
        }
        reaches.removeAll(done.toSet())
    }

    private fun lastEnd(): Long = reaches.maxOfOrNull { it.endNanos } ?: 0L

    private fun emitCounts(nowNanos: Long, click: Boolean): AimerStep {
        val (yaw, pitch) = currentAngles(nowNanos)
        val x = (yaw / degPerCount).roundToLong()
        val y = (pitch / degPerCount).roundToLong()
        val dx = (x - emittedX).toInt()
        val dy = -(y - emittedY).toInt() // mouse down (positive dy) looks down
        emittedX = x
        emittedY = y
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
        const val MIN_REACTION_MS = 120.0
        const val MIN_REACH_MS = 60.0
        const val MIN_WIDTH_DEGREES = 0.3
        const val CORRECTION_SPEEDUP = 0.6
        const val MISS_RECHECK_MS = 100.0
        const val TWITCH_FRACTION = 0.25
        const val TWITCH_MS = 30.0
        val LN2 = ln(2.0)
    }
}
