package de.yuuto.aimtune.core.metrics

import de.yuuto.aimtune.core.analysis.SegmentationParams
import de.yuuto.aimtune.core.analysis.Submovement
import de.yuuto.aimtune.core.analysis.segmentSubmovements
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.recording.ViewTrack
import de.yuuto.aimtune.core.scenario.Target
import de.yuuto.aimtune.core.scenario.TargetEvent
import de.yuuto.aimtune.core.scenario.summarize
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

data class MetricsParams(
    val segmentation: SegmentationParams = SegmentationParams(),
    /** Targets closer than this to the crosshair at spawn time are not analysed as movements. */
    val minTargetDistanceDegrees: Double = 2.0,
)

/** One reach from a target's spawn to the hit on it. */
data class MovementMetric(
    val targetId: Int,
    val timeToTargetNanos: Long,
    val initialDistanceDegrees: Double,
    /** Spawn until the first submovement starts, if any submovement was detected. */
    val reactionNanos: Long?,
    /** Signed endpoint error of the first submovement relative to the target distance: > 0 overshoot, < 0 undershoot. */
    val firstErrorRatio: Double?,
    val firstPeakSpeedCountsPerMs: Double?,
    val correctiveSubmovements: Int,
    /** Straight angular distance divided by the angular path actually travelled, in (0, 1]. */
    val pathEfficiency: Double?,
)

data class RoundMetrics(
    val hits: Int,
    val shots: Int,
    val accuracy: Double,
    val movements: List<MovementMetric>,
    val meanTimeToHitMs: Double?,
    val meanFirstError: Double?,
    val meanAbsFirstError: Double?,
    /** Sample standard deviation of the signed first-submovement errors; the "consistency" metric (lower is better). */
    val errorSpread: Double?,
    val meanCorrectiveSubmovements: Double?,
    val meanPathEfficiency: Double?,
)

/** Computes the flick metrics of a round from its recording, as defined in docs/ALGORITHMS.md. */
fun computeRoundMetrics(recording: RoundRecording, params: MetricsParams = MetricsParams()): RoundMetrics {
    val summary = summarize(recording.targetEvents)
    val submovements = segmentSubmovements(recording.mouseEvents, recording.dpi, params.segmentation)
    val track = ViewTrack(recording)
    val spawns = recording.targetEvents.filterIsInstance<TargetEvent.Spawned>().associateBy { it.target.id }
    val movements = recording.targetEvents.filterIsInstance<TargetEvent.Hit>().mapNotNull { hit ->
        val spawn = spawns[hit.targetId] ?: return@mapNotNull null
        movementMetric(recording, track, submovements, spawn, hit, params)
    }
    val errors = movements.mapNotNull { it.firstErrorRatio }
    return RoundMetrics(
        hits = summary.hits,
        shots = summary.shots,
        accuracy = summary.accuracy,
        movements = movements,
        meanTimeToHitMs = summary.meanTimeToHitNanos?.let { it / 1e6 },
        meanFirstError = errors.averageOrNull(),
        meanAbsFirstError = errors.map { abs(it) }.averageOrNull(),
        errorSpread = errors.sampleStdDevOrNull(),
        meanCorrectiveSubmovements = movements.map { it.correctiveSubmovements.toDouble() }.averageOrNull(),
        meanPathEfficiency = movements.mapNotNull { it.pathEfficiency }.averageOrNull(),
    )
}

/** Offset on the screen plane, in degrees, with yaw scaled by cos(pitch) so both axes are true angles. */
private data class Offset(val x: Double, val y: Double) {
    val length: Double get() = hypot(x, y)
}

private fun offset(fromYaw: Double, fromPitch: Double, toYaw: Double, toPitch: Double, referencePitchDegrees: Double): Offset {
    var dYaw = (toYaw - fromYaw) % 360.0
    if (dYaw > 180.0) dYaw -= 360.0
    if (dYaw < -180.0) dYaw += 360.0
    return Offset(dYaw * cos(Math.toRadians(referencePitchDegrees)), toPitch - fromPitch)
}

private fun movementMetric(
    recording: RoundRecording,
    track: ViewTrack,
    submovements: List<Submovement>,
    spawn: TargetEvent.Spawned,
    hit: TargetEvent.Hit,
    params: MetricsParams,
): MovementMetric? {
    val t0 = spawn.timeNanos
    val t1 = hit.timeNanos
    val start = track.viewAt(t0)
    val (targetYaw, targetPitch) = directionAngles(spawn.target)
    val toTarget = offset(start.yawDegrees, start.pitchDegrees, targetYaw, targetPitch, targetPitch)
    val distance = toTarget.length
    if (distance < params.minTargetDistanceDegrees) return null

    val inWindow = submovements.filter { it.peakNanos in t0..t1 }
    val first = inWindow.firstOrNull()
    val firstError = first?.let {
        val end = track.viewAt(it.endNanos - 1)
        val moved = offset(start.yawDegrees, start.pitchDegrees, end.yawDegrees, end.pitchDegrees, targetPitch)
        val along = (moved.x * toTarget.x + moved.y * toTarget.y) / distance
        (along - distance) / distance
    }
    val path = angularPath(recording, t0, t1, targetPitch)
    return MovementMetric(
        targetId = spawn.target.id,
        timeToTargetNanos = t1 - t0,
        initialDistanceDegrees = distance,
        reactionNanos = first?.let { it.startNanos - t0 },
        firstErrorRatio = firstError,
        firstPeakSpeedCountsPerMs = first?.peakSpeedCountsPerMs,
        correctiveSubmovements = maxOf(0, inWindow.size - 1),
        pathEfficiency = if (path > 0.0) minOf(1.0, distance / path) else null,
    )
}

private fun angularPath(recording: RoundRecording, t0: Long, t1: Long, referencePitchDegrees: Double): Double {
    val dpc = recording.degreesPerCount
    val yawScale = cos(Math.toRadians(referencePitchDegrees))
    return recording.mouseEvents.asSequence()
        .filter { it.timeNanos in t0..t1 }
        .sumOf { hypot(it.dx * dpc * yawScale, it.dy * dpc) }
}

private fun directionAngles(target: Target): Pair<Double, Double> {
    val c = target.center
    val yaw = Math.toDegrees(atan2(c.x, -c.z))
    val pitch = Math.toDegrees(asin(c.y / c.length))
    return yaw to pitch
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun List<Double>.sampleStdDevOrNull(): Double? {
    if (size < 2) return null
    val mean = average()
    return sqrt(sumOf { (it - mean) * (it - mean) } / (size - 1))
}
