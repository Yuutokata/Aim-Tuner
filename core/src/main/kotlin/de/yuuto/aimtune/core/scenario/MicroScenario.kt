package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.math.Vec3
import de.yuuto.aimtune.core.math.ViewAngles
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/** Geometry of the micro-adjust scenario. Distances and radius are in scene units (meters). */
data class MicroConfig(
    val durationNanos: Long,
    val maxYawDegrees: Double = 40.0,
    val maxPitchDegrees: Double = 20.0,
    /** Angle between the previous target and the next one. */
    val minStepDegrees: Double = 2.5,
    val maxStepDegrees: Double = 9.0,
    val minDistance: Double = 10.0,
    val maxDistance: Double = 14.0,
    val targetRadius: Double = 0.15,
    /** When set, targets are head-sized angles and the steps come from the game's geometry. */
    val geometry: TrainerGeometry? = null,
) {
    init {
        require(durationNanos > 0) { "durationNanos must be positive" }
        require(0.0 < minStepDegrees && minStepDegrees < maxStepDegrees) { "invalid step range" }
        require(0.0 < minDistance && minDistance <= maxDistance) { "invalid distance range" }
        require(targetRadius > 0.0) { "targetRadius must be positive" }
    }
}

/**
 * Small targets close to where the crosshair just was: each hit spawns the next target a few
 * degrees away, so the round is about fine corrections instead of big flicks.
 */
class MicroScenario(private val config: MicroConfig, seed: Long) : Scenario {
    override val kind = ScenarioKind.MICRO

    private val random = Random(seed)
    private val eventLog = mutableListOf<TargetEvent>()
    private var current: Target? = null
    private var yaw = 0.0
    private var pitch = 0.0
    private var nextId = 0
    private var startNanos: Long? = null

    override val targets: List<Target> get() = listOfNotNull(current)
    override val events: List<TargetEvent> get() = eventLog

    override fun start(nowNanos: Long) {
        startNanos = nowNanos
        spawn(nowNanos)
    }

    override fun isFinished(nowNanos: Long): Boolean {
        val start = startNanos ?: return false
        return nowNanos - start >= config.durationNanos
    }

    override fun fire(origin: Vec3, direction: Vec3, nowNanos: Long): ShotResult {
        val target = current
        if (startNanos == null || isFinished(nowNanos) || target == null) return ShotResult.IGNORED
        if (!target.isHitBy(origin, direction)) {
            eventLog += TargetEvent.Miss(nowNanos)
            return ShotResult.MISS
        }
        eventLog += TargetEvent.Hit(nowNanos, target.id)
        spawn(nowNanos)
        return ShotResult.HIT
    }

    private fun spawn(nowNanos: Long) {
        val (nextYaw, nextPitch) = nextAngles()
        yaw = nextYaw
        pitch = nextPitch
        val g = config.geometry
        val distance = if (g != null) g.clickTargetDistanceMeters.uniform(random) else random.nextDouble(config.minDistance, config.maxDistance)
        val radius = if (g != null) g.radiusFor(g.clickTargetDiameterDegrees.logUniform(random), distance) else config.targetRadius
        val target = Target(nextId++, ViewAngles.directionOf(yaw, pitch) * distance, radius)
        current = target
        eventLog += TargetEvent.Spawned(nowNanos, target)
    }

    /** A random spot whose true angle to the previous target is within the step range and inside the field. */
    private fun nextAngles(): Pair<Double, Double> {
        config.geometry?.let { g ->
            return placeAt(yaw, pitch, g.microStepDegrees.uniform(random), random, minOf(g.fieldYawDegrees, config.maxYawDegrees), minOf(g.fieldPitchDegrees, config.maxPitchDegrees))
        }
        val from = ViewAngles.directionOf(yaw, pitch)
        repeat(MAX_PLACEMENT_ATTEMPTS) {
            val candidateYaw = yaw + random.nextDouble(-config.maxStepDegrees, config.maxStepDegrees)
            val candidatePitch = pitch + random.nextDouble(-config.maxStepDegrees, config.maxStepDegrees)
            val step = ViewAngles.directionOf(candidateYaw, candidatePitch).angleToDegrees(from)
            val inField = abs(candidateYaw) <= config.maxYawDegrees && abs(candidatePitch) <= config.maxPitchDegrees
            if (inField && step in config.minStepDegrees..config.maxStepDegrees) return candidateYaw to candidatePitch
        }
        // Cornered at the edge of the field: step back toward the middle.
        return (yaw - yaw.sign * config.minStepDegrees) to pitch
    }

    private companion object {
        const val MAX_PLACEMENT_ATTEMPTS = 200
    }
}
