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
    FLASH("flash", "Flash turn-away", true),
    ;

    /**
     * A new seeded round of this scenario. Without [geometry] the fixed legacy values are used (they
     * are what the simulation harness was calibrated on); with it the targets follow the game's
     * angular sizes, distances and speeds.
     */
    fun create(durationNanos: Long, seed: Long, geometry: TrainerGeometry? = null): Scenario = when (this) {
        FLICK -> FlickScenario(FlickConfig(durationNanos = durationNanos, geometry = geometry), seed)
        MICRO -> MicroScenario(MicroConfig(durationNanos = durationNanos, geometry = geometry), seed)
        TRACKING -> TrackingScenario(geometry?.let { TrackingConfig.from(it, durationNanos) } ?: TrackingConfig(durationNanos = durationNanos), seed)
        SWITCHING -> SwitchingScenario(SwitchingConfig(durationNanos = durationNanos, geometry = geometry), seed)
        FLASH -> FlashScenario(durationNanos, geometry ?: TrainerGeometry.DEFAULT, seed)
    }

    companion object {
        fun fromId(id: String): ScenarioKind? = entries.firstOrNull { it.id == id }
    }
}
