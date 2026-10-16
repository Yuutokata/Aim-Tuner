package de.yuuto.aimtune.core.metrics

import de.yuuto.aimtune.core.analysis.SegmentationParams
import de.yuuto.aimtune.core.analysis.Submovement
import de.yuuto.aimtune.core.analysis.segmentSubmovements
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.recording.ViewTrack
import de.yuuto.aimtune.core.scenario.ScenarioKind
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
    /**
     * The speed peak of the first reach comes at least this long after a target appeared. A reach
     * whose peak comes earlier is the hand still moving from the previous click, not a reaction
     * (docs/REVIEW.md C2). The peak, not the start, because the start of a smooth reach is found
     * early (the slow ramp-up is above the low end threshold). Not applied to targets that were
     * already visible, where the player may be on the way before the previous hit.
     */
    val minReactionPeakNanos: Long = 120_000_000L,
    /** The first reach covers at least this share of the distance to the target; smaller movements are tremor, not a reach. */
    val minFirstReachFraction: Double = 0.25,
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
    /** Where the first submovement ended relative to the target, on the screen plane in degrees: > 0 is right of the target. */
    val firstErrorHDegrees: Double? = null,
    /** Same for the vertical axis: > 0 is above the target (the crosshair ended too high). */
    val firstErrorVDegrees: Double? = null,
)

data class RoundMetrics(
    val hits: Int,
    val shots: Int,
    val accuracy: Double,
    /** Share of reaches whose first click hit (null without clicks); the accuracy the score uses. */
    val firstShotAccuracy: Double?,
    val movements: List<MovementMetric>,
    val meanTimeToHitMs: Double?,
    val meanFirstError: Double?,
    val meanAbsFirstError: Double?,
    /** Sample standard deviation of the signed first-submovement errors; the "consistency" metric (lower is better). */
    val errorSpread: Double?,
    val meanCorrectiveSubmovements: Double?,
    val meanPathEfficiency: Double?,
    /** Share of the round the crosshair was on the moving target; only for tracking rounds. */
    val trackingOnTargetRatio: Double? = null,
    /** Mean angle between crosshair and the moving target's center in degrees; only for tracking rounds. */
    val trackingMeanDistanceDegrees: Double? = null,
    /** Share of flashes the player had turned away from when they popped; only for flash rounds. */
    val flashDodgeRate: Double? = null,
    /** Mean time from a flash becoming visible until it was beyond the safe angle, over the flashes dodged in time. */
    val flashMeanTurnMs: Double? = null,
    /** Mean peak speed of the turn away in counts per ms; the curve analysis uses the return movements, this one is a diagnostic. */
    val flashTurnPeakSpeed: Double? = null,
    /** Mean absolute horizontal and vertical endpoint error of the first submovements in degrees; they show which axis the misses come from. */
    val meanAbsFirstErrorHDegrees: Double? = null,
    val meanAbsFirstErrorVDegrees: Double? = null,
    /** Mean signed vertical endpoint error in degrees: > 0 means the first movement tends to end above the target. */
    val meanFirstErrorVDegrees: Double? = null,
) {
    /** Share of the endpoint error (summed over both axes) that is vertical, null without first movements. */
    val verticalErrorShare: Double?
        get() {
            val h = meanAbsFirstErrorHDegrees ?: return null
            val v = meanAbsFirstErrorVDegrees ?: return null
            return if (h + v > 0.0) v / (h + v) else null
        }
}

/** Computes the metrics of a round from its recording, as defined in docs/ALGORITHMS.md. */
fun computeRoundMetrics(recording: RoundRecording, params: MetricsParams = MetricsParams()): RoundMetrics {
    val summary = summarize(recording.targetEvents)
    val submovements = segmentSubmovements(recording.mouseEvents, recording.dpi, params.segmentation)
    val track = ViewTrack(recording)
    val spawns = recording.targetEvents.filterIsInstance<TargetEvent.Spawned>().associateBy { it.target.id }
    val hitEvents = recording.targetEvents.filterIsInstance<TargetEvent.Hit>()
    val movements = hitEvents.mapIndexedNotNull { index, hit ->
        val spawn = spawns[hit.targetId] ?: return@mapIndexedNotNull null
        // The reach starts at the later of the target's spawn and the previous hit (several targets can wait at once).
        val from = maxOf(spawn.timeNanos, hitEvents.getOrNull(index - 1)?.timeNanos ?: spawn.timeNanos)
        movementMetric(recording, track, submovements, spawn, from, hit, params)
    }
    val tracking = if (ScenarioKind.fromId(recording.scenarioId) == ScenarioKind.TRACKING) computeTrackingMetrics(recording, track) else null
    val flash = if (ScenarioKind.fromId(recording.scenarioId) == ScenarioKind.FLASH) computeFlashMetrics(recording, track, submovements) else null
    val errors = movements.mapNotNull { it.firstErrorRatio }
    return RoundMetrics(
        hits = summary.hits,
        shots = summary.shots,
        accuracy = summary.accuracy,
        firstShotAccuracy = summary.firstShotAccuracy,
        movements = movements,
        meanTimeToHitMs = summary.meanTimeToHitNanos?.let { it / 1e6 },
        meanFirstError = errors.averageOrNull(),
        meanAbsFirstError = errors.map { abs(it) }.averageOrNull(),
        errorSpread = errors.sampleStdDevOrNull(),
        meanCorrectiveSubmovements = movements.map { it.correctiveSubmovements.toDouble() }.averageOrNull(),
        meanPathEfficiency = movements.mapNotNull { it.pathEfficiency }.averageOrNull(),
        trackingOnTargetRatio = tracking?.onTargetRatio,
        trackingMeanDistanceDegrees = tracking?.meanDistanceDegrees,
        flashDodgeRate = flash?.dodgeRate,
        flashMeanTurnMs = flash?.meanTurnMs,
        flashTurnPeakSpeed = flash?.meanTurnPeakSpeed,
        meanAbsFirstErrorHDegrees = movements.mapNotNull { it.firstErrorHDegrees }.map { abs(it) }.averageOrNull(),
        meanAbsFirstErrorVDegrees = movements.mapNotNull { it.firstErrorVDegrees }.map { abs(it) }.averageOrNull(),
        meanFirstErrorVDegrees = movements.mapNotNull { it.firstErrorVDegrees }.averageOrNull(),
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
    fromNanos: Long,
    hit: TargetEvent.Hit,
    params: MetricsParams,
): MovementMetric? {
    val t0 = fromNanos
    val t1 = hit.timeNanos
    val start = track.viewAt(t0)
    val (targetYaw, targetPitch) = directionAngles(spawn.target)
    val toTarget = offset(start.yawDegrees, start.pitchDegrees, targetYaw, targetPitch, targetPitch)
    val distance = toTarget.length
    if (distance < params.minTargetDistanceDegrees) return null

    val inWindow = submovements.filter { it.peakNanos in t0..t1 }
    val firstIndex = inWindow.indexOfFirst { isFirstReach(it, track, if (fromNanos > spawn.timeNanos) null else t0, toTarget, distance, targetPitch, params) }
    val first = inWindow.getOrNull(firstIndex)
    val firstMoved = first?.let {
        val end = track.viewAt(it.endNanos - 1)
        offset(start.yawDegrees, start.pitchDegrees, end.yawDegrees, end.pitchDegrees, targetPitch)
    }
    val firstError = firstMoved?.let { moved ->
        val along = (moved.x * toTarget.x + moved.y * toTarget.y) / distance
        (along - distance) / distance
    }
    val path = angularPath(recording, track, t0, t1, targetPitch)
    return MovementMetric(
        targetId = spawn.target.id,
        timeToTargetNanos = t1 - t0,
        initialDistanceDegrees = distance,
        reactionNanos = first?.let { it.startNanos - t0 },
        firstErrorRatio = firstError,
        firstPeakSpeedCountsPerMs = first?.peakSpeedCountsPerMs,
        correctiveSubmovements = if (first == null) maxOf(0, inWindow.size - 1) else inWindow.size - 1 - firstIndex,
        pathEfficiency = if (path > 0.0) minOf(1.0, distance / path) else null,
        firstErrorHDegrees = firstMoved?.let { it.x - toTarget.x },
        firstErrorVDegrees = firstMoved?.let { it.y - toTarget.y },
    )
}

/** A submovement that is a reach toward the target rather than leftover motion or tremor. */
private fun isFirstReach(sub: Submovement, track: ViewTrack, newTargetAtNanos: Long?, toTarget: Offset, distance: Double, targetPitch: Double, params: MetricsParams): Boolean {
    if (newTargetAtNanos != null && sub.peakNanos < newTargetAtNanos + params.minReactionPeakNanos) return false
    val from = track.viewAt(sub.startNanos - 1)
    val to = track.viewAt(sub.endNanos - 1)
    val moved = offset(from.yawDegrees, from.pitchDegrees, to.yawDegrees, to.pitchDegrees, targetPitch)
    val along = (moved.x * toTarget.x + moved.y * toTarget.y) / distance
    return along >= params.minFirstReachFraction * distance
}

private fun angularPath(recording: RoundRecording, track: ViewTrack, t0: Long, t1: Long, referencePitchDegrees: Double): Double {
    val dpc = recording.degreesPerCount
    val yawScale = cos(Math.toRadians(referencePitchDegrees))
    return recording.mouseEvents.indices.asSequence()
        .filter { recording.mouseEvents[it].timeNanos in t0..t1 }
        .sumOf { i ->
            val e = recording.mouseEvents[i]
            val scale = track.scales[i]
            hypot(e.dx * scale * dpc * yawScale, e.dy * scale * dpc)
        }
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
