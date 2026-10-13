package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.math.Vec3
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Yaw and pitch in degrees of the direction to [center]. */
internal fun anglesOf(center: Vec3): Pair<Double, Double> =
    Math.toDegrees(atan2(center.x, -center.z)) to Math.toDegrees(asin(center.y / center.length))

/**
 * A spot [angle] degrees from ([yaw], [pitch]) in a random direction that lies inside the field. The
 * angle is measured on the screen plane (yaw scaled by cos(pitch)), which for these small fields is
 * the true angle within a few percent. Falls back to stepping toward the middle when the random
 * directions all leave the field (the previous target sits in a corner).
 */
internal fun placeAt(yaw: Double, pitch: Double, angle: Double, random: Random, fieldYaw: Double, fieldPitch: Double): Pair<Double, Double> {
    repeat(MAX_PLACEMENT_TRIES) {
        val heading = random.nextDouble(0.0, 2 * Math.PI)
        val nextPitch = pitch + angle * sin(heading)
        val nextYaw = yaw + angle * cos(heading) / cos(Math.toRadians(nextPitch))
        if (abs(nextYaw) <= fieldYaw && abs(nextPitch) <= fieldPitch) return nextYaw to nextPitch
    }
    val towardYaw = if (yaw > 0) -1.0 else 1.0
    return (yaw + towardYaw * minOf(angle, abs(yaw) + fieldYaw / 2)).coerceIn(-fieldYaw, fieldYaw) to pitch
}

private const val MAX_PLACEMENT_TRIES = 60
