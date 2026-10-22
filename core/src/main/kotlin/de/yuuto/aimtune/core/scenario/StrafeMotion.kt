package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.math.Vec3
import kotlin.math.abs
import kotlin.random.Random

/**
 * How a strafing target moves: move, stop, move, ... like a bot in a shooting range (values from docs/RANGE.md, "Behaviour
 * spec"). The speed is a speed in meters per second across the line of sight, so the angular speed depends on the target's
 * distance. The target stays within [maxOffsetMeters] of where it spawned, and its yaw within [maxAbsYawDegrees] so it does not
 * leave the screen.
 */
data class StrafeConfig(
    val speedMetersPerSecond: Double = 2.5,
    val movingSeconds: PositiveRange = PositiveRange(0.25, 0.5),
    val stopSeconds: PositiveRange = PositiveRange(0.1, 0.3),
    /** Chance that the target turns around after a stop instead of going on in the same direction. */
    val reverseProbability: Double = 0.6,
    val maxOffsetMeters: Double = 2.0,
    val maxAbsYawDegrees: Double = 90.0,
) {
    init {
        require(speedMetersPerSecond > 0.0 && maxOffsetMeters > 0.0) { "speed and offset must be positive" }
        require(reverseProbability in 0.0..1.0) { "reverse probability must be in 0..1" }
        require(maxAbsYawDegrees in 5.0..90.0) { "yaw limit must be in 5..90 degrees" }
    }

    companion object {
        /** The strafe of a game profile: targets stay on the screen, which is [FlashGeometry.halfFovDegrees] wide on each side. */
        fun from(geometry: TrainerGeometry): StrafeConfig =
            StrafeConfig(maxAbsYawDegrees = (geometry.flash.halfFovDegrees - SCREEN_EDGE_MARGIN_DEGREES).coerceAtLeast(5.0))

        private const val SCREEN_EDGE_MARGIN_DEGREES = 6.0
    }
}

/**
 * The path of one strafing target from its spawn, planned lazily from its own seed: legs are only added when time reaches
 * them, and nothing else draws from this random source, so the path does not depend on how often the scenario is updated.
 * [extendTo] guarantees that the path has a point after the given time, which is what [TargetPath] needs to interpolate
 * the target's position at any time of the log.
 */
class StrafeMotion(
    private val config: StrafeConfig,
    private val targetId: Int,
    private val spawn: Vec3,
    spawnNanos: Long,
    seed: Long,
) {
    private class Point(val timeNanos: Long, val center: Vec3)

    private val random = Random(seed)
    private val points = mutableListOf(Point(spawnNanos, spawn))
    private val distance = spawn.length

    /** Horizontal unit vector across the line of sight from the spawn. */
    private val across: Vec3 = Vec3(-spawn.z, 0.0, spawn.x).let { sideways ->
        val length = sideways.length
        if (length > 0.0) sideways * (1.0 / length) else Vec3(1.0, 0.0, 0.0)
    }
    private var offsetMeters = 0.0
    private var heading = if (random.nextBoolean()) 1.0 else -1.0
    private var nextLegMoves = random.nextBoolean()

    /** Adds legs until the path has a point after [nowNanos]; returns the waypoints this added, to be logged. */
    fun extendTo(nowNanos: Long): List<TargetEvent.Waypoint> {
        val before = points.size
        while (points.last().timeNanos <= nowNanos) addLeg()
        return points.drop(before).map { TargetEvent.Waypoint(it.timeNanos, targetId, it.center) }
    }

    /** Where the target is at [nowNanos]; the spawn before it spawned. */
    fun positionAt(nowNanos: Long): Vec3 {
        val after = points.indexOfFirst { it.timeNanos > nowNanos }
        if (after == -1) return points.last().center
        if (after == 0) return points.first().center
        val a = points[after - 1]
        val b = points[after]
        return TargetPath.interpolate(a.center, b.center, (nowNanos - a.timeNanos).toDouble() / (b.timeNanos - a.timeNanos))
    }

    private fun addLeg() {
        val last = points.last()
        if (nextLegMoves) {
            val seconds = config.movingSeconds.uniform(random)
            offsetMeters += step(config.speedMetersPerSecond * seconds)
            points += Point(last.timeNanos + toNanos(seconds), centerAt(offsetMeters))
        } else {
            val seconds = config.stopSeconds.uniform(random)
            points += Point(last.timeNanos + toNanos(seconds), last.center)
            if (random.nextDouble() < config.reverseProbability) heading = -heading
        }
        nextLegMoves = !nextLegMoves
    }

    /** The signed move of [length] meters along the heading; a target at the edge of its range turns around, or stays if it cannot move either way. */
    private fun step(length: Double): Double {
        for (candidate in listOf(heading * length, -heading * length)) {
            if (allowed(offsetMeters + candidate)) {
                heading = if (candidate > 0.0) 1.0 else -1.0
                return candidate
            }
        }
        return 0.0
    }

    /** Within the offset limit, and not further out than the yaw limit unless it is not further out than now. */
    private fun allowed(offset: Double): Boolean {
        if (abs(offset) > config.maxOffsetMeters) return false
        val yaw = abs(anglesOf(centerAt(offset)).first)
        return yaw <= config.maxAbsYawDegrees || yaw <= abs(anglesOf(centerAt(offsetMeters)).first)
    }

    /** The spawn moved [offset] meters across the line of sight, at the spawn's distance (the target strafes on an arc). */
    private fun centerAt(offset: Double): Vec3 = (spawn + across * offset).normalized() * distance

    private fun toNanos(seconds: Double): Long = (seconds * NANOS_PER_SECOND).toLong()

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        /** A seed for the motion of target [targetId] of a round with [roundSeed]; every target moves on its own random stream. */
        fun seedFor(roundSeed: Long, targetId: Int): Long = roundSeed xor ((targetId + 1) * SEED_MIX)

        private const val SEED_MIX = -7046029254386353131L
    }
}

/**
 * The strafing targets of a scenario: [add] starts a target's motion when it spawns, [advance] moves the targets to a time
 * and logs the waypoints that came due, [remove] forgets a target that was hit.
 */
internal class StrafingTargets(private val config: StrafeConfig, private val roundSeed: Long) {
    private val motions = HashMap<Int, StrafeMotion>()

    fun add(target: Target, nowNanos: Long, log: MutableList<TargetEvent>) {
        val motion = StrafeMotion(config, target.id, target.center, nowNanos, StrafeMotion.seedFor(roundSeed, target.id))
        motions[target.id] = motion
        log += motion.extendTo(nowNanos)
    }

    fun remove(target: Target) {
        motions.remove(target.id)
    }

    /** [targets] at [nowNanos]. */
    fun advance(targets: List<Target>, nowNanos: Long, log: MutableList<TargetEvent>): List<Target> =
        targets.map { target ->
            val motion = motions[target.id] ?: return@map target
            log += motion.extendTo(nowNanos)
            target.copy(center = motion.positionAt(nowNanos))
        }
}
