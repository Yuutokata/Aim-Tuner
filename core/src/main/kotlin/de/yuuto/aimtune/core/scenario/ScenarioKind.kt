package de.yuuto.aimtune.core.scenario

/**
 * The scenarios the trainer can run. [id] is what is stored with a round. Tracking is followed with
 * the crosshair alone (the on-target time is measured), so it does not use clicks.
 */
enum class ScenarioKind(val id: String, val displayName: String, val usesClicks: Boolean) {
    FLICK("flick", "Flick", true),
    MICRO("micro", "Micro-adjust", true),
    TRACKING("tracking", "Tracking", false),
    SWITCHING("switching", "Target switching", true),
    ;

    /** A new seeded round of this scenario with its default geometry. */
    fun create(durationNanos: Long, seed: Long): Scenario = when (this) {
        FLICK -> FlickScenario(FlickConfig(durationNanos = durationNanos), seed)
        MICRO -> MicroScenario(MicroConfig(durationNanos = durationNanos), seed)
        TRACKING -> TrackingScenario(TrackingConfig(durationNanos = durationNanos), seed)
        SWITCHING -> SwitchingScenario(SwitchingConfig(durationNanos = durationNanos), seed)
    }

    companion object {
        fun fromId(id: String): ScenarioKind? = entries.firstOrNull { it.id == id }
    }
}
