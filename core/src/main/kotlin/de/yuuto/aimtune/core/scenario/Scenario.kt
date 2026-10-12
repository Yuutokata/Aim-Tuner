package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.math.Vec3
import kotlin.math.sqrt

data class Target(val id: Int, val center: Vec3, val radius: Double) {
    /** True if the ray from [origin] along the normalized [direction] intersects this sphere. */
    fun isHitBy(origin: Vec3, direction: Vec3): Boolean {
        val toCenter = center - origin
        val projection = toCenter dot direction
        if (projection < 0.0) return false
        val closestSq = (toCenter dot toCenter) - projection * projection
        return sqrt(maxOf(closestSq, 0.0)) <= radius
    }
}

sealed interface TargetEvent {
    val timeNanos: Long

    data class Spawned(override val timeNanos: Long, val target: Target) : TargetEvent
    data class Hit(override val timeNanos: Long, val targetId: Int) : TargetEvent
    data class Miss(override val timeNanos: Long) : TargetEvent
}

enum class ShotResult { HIT, MISS, IGNORED }

/**
 * A short aim round. Implementations are deterministic for a given seed and never read a clock;
 * the caller passes the current time (nanoseconds, any monotonic origin) into every call.
 */
interface Scenario {
    val id: String
    val targets: List<Target>
    val events: List<TargetEvent>

    fun start(nowNanos: Long)
    fun isFinished(nowNanos: Long): Boolean
    fun fire(origin: Vec3, direction: Vec3, nowNanos: Long): ShotResult
}
