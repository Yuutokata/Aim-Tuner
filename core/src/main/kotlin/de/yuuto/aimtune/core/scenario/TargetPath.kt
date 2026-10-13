package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.math.Vec3

/**
 * Where moving targets are over time, rebuilt from the target events: the spawn position and the
 * [TargetEvent.Waypoint]s of a target form its path, and between two points it moves with constant
 * angular speed at a linearly changing distance. Scenarios and metrics both use [interpolate], so
 * what the player saw and what the analysis assumes cannot drift apart.
 */
class TargetPath(events: List<TargetEvent>) {
    private class Point(val timeNanos: Long, val center: Vec3)

    private val paths: Map<Int, List<Point>> = buildMap<Int, MutableList<Point>> {
        for (event in events) {
            when (event) {
                is TargetEvent.Spawned -> getOrPut(event.target.id) { mutableListOf() } += Point(event.timeNanos, event.target.center)
                is TargetEvent.Waypoint -> getOrPut(event.targetId) { mutableListOf() } += Point(event.timeNanos, event.center)
                is TargetEvent.Hit, is TargetEvent.Miss -> Unit
            }
        }
    }.mapValues { (_, points) -> points.sortedBy { it.timeNanos } }

    /** The center of target [targetId] at [timeNanos], or null before it spawned (or if it never did). */
    fun positionAt(targetId: Int, timeNanos: Long): Vec3? {
        val points = paths[targetId] ?: return null
        if (timeNanos < points.first().timeNanos) return null
        val after = points.indexOfFirst { it.timeNanos > timeNanos }
        if (after == -1) return points.last().center
        val a = points[after - 1]
        val b = points[after]
        return interpolate(a.center, b.center, (timeNanos - a.timeNanos).toDouble() / (b.timeNanos - a.timeNanos))
    }

    companion object {
        /** Direction and distance both change linearly with [fraction] in [0, 1]. */
        fun interpolate(from: Vec3, to: Vec3, fraction: Double): Vec3 {
            val direction = from.normalized() * (1.0 - fraction) + to.normalized() * fraction
            val distance = from.length * (1.0 - fraction) + to.length * fraction
            return direction.normalized() * distance
        }
    }
}
