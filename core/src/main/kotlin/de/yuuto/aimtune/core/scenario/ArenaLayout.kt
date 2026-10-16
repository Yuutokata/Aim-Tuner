package de.yuuto.aimtune.core.scenario

import kotlin.math.abs

enum class ArenaFeatureKind { CORNER, DOORWAY }

/**
 * A place where an enemy can stand: next to a wall corner or in a doorway, at [yawDegrees] and
 * [distanceMeters] from the player. The scene draws the corner or door frame there; the scenarios
 * sometimes put a target at the feature instead of at the drawn spot.
 */
data class ArenaFeature(val kind: ArenaFeatureKind, val yawDegrees: Double, val distanceMeters: Double) {
    init {
        require(yawDegrees in -180.0..180.0 && distanceMeters > 0.0) { "feature needs a yaw in -180..180 and a positive distance" }
    }
}

/**
 * The minimal map the trainer draws: a floor at eye height below the camera, a far wall and corner and
 * doorway features. It exists so targets stand where enemies stand (on the floor, at corners and doors)
 * and so the player can place the crosshair on head height. The scenarios stay angle-based: a feature
 * only moves a drawn target to the nearest feature when that is within [snapDegrees] of its yaw, which
 * happens for [featureShare] of the flick and switching targets, so the angle mix of the profile is
 * kept to within [snapDegrees].
 */
data class ArenaLayout(
    val wallDistanceMeters: Double = 36.0,
    val features: List<ArenaFeature> = DEFAULT_FEATURES,
    /** Share of flick and switching targets that stand at a feature when one is within [snapDegrees]. */
    val featureShare: Double = 0.3,
    val snapDegrees: Double = 2.5,
) {
    init {
        require(wallDistanceMeters in 20.0..80.0) { "wall distance must be 20..80 m" }
        require(featureShare in 0.0..1.0 && snapDegrees in 0.0..10.0) { "feature share must be 0..1 and snap 0..10 degrees" }
    }

    /** The feature nearest in yaw to [yawDegrees], if it is within [snapDegrees]. */
    fun featureNear(yawDegrees: Double): ArenaFeature? =
        features.minByOrNull { abs(it.yawDegrees - yawDegrees) }?.takeIf { abs(it.yawDegrees - yawDegrees) <= snapDegrees }

    companion object {
        /** Corners and doorways every 20-30 degrees at distances typical for duels, within the field of the flick scenario. */
        val DEFAULT_FEATURES = listOf(
            ArenaFeature(ArenaFeatureKind.CORNER, -80.0, 9.0),
            ArenaFeature(ArenaFeatureKind.DOORWAY, -55.0, 22.0),
            ArenaFeature(ArenaFeatureKind.CORNER, -32.0, 14.0),
            ArenaFeature(ArenaFeatureKind.DOORWAY, -10.0, 28.0),
            ArenaFeature(ArenaFeatureKind.CORNER, 12.0, 11.0),
            ArenaFeature(ArenaFeatureKind.DOORWAY, 33.0, 18.0),
            ArenaFeature(ArenaFeatureKind.CORNER, 55.0, 24.0),
            ArenaFeature(ArenaFeatureKind.DOORWAY, 80.0, 12.0),
        )
        val DEFAULT = ArenaLayout()
        val NONE = ArenaLayout(featureShare = 0.0)
    }
}
