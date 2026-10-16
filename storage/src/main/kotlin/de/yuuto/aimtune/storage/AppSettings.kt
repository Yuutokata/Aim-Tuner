package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.score.FlashImportance
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreMetric
import de.yuuto.aimtune.core.units.AimStyle
import kotlinx.serialization.Serializable

class SettingsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * User settings stored as JSON. Everything personal is nullable and stays null until onboarding
 * fills it; nothing user-specific is defaulted. Metric weights are keyed by [ScoreMetric] name.
 */
@Serializable
data class AppSettings(
    val version: Int = CURRENT_VERSION,
    val language: String? = null,
    val dpi: Double? = null,
    /** Id of a game profile, or `custom` together with [customYaw] and [customFovDegrees]. */
    val gameId: String? = null,
    val customYaw: Double? = null,
    val customFovDegrees: Double? = null,
    val inGameSensitivity: Double? = null,
    /** [AimStyle] name; stored as text so the core enum stays free of serialization. */
    val aimStyle: String? = null,
    val padWidthCm: Double? = null,
    val padHeightCm: Double? = null,
    val roundSeconds: Int = DEFAULT_ROUND_SECONDS,
    val metricWeights: Map<String, Double> = DEFAULT_WEIGHTS,
    /** Ids of the scenarios a blind test plays ([ScenarioKind.id]); how often each is played follows [metricWeights]. */
    val scenarios: List<String> = DEFAULT_SCENARIOS,
    /** Play the cue sounds of the flash scenario (alert, whine, pop, ringing). */
    val flashSound: Boolean = true,
    /** The player's Valorant crosshairs, stored as profile codes; none means the default crosshair is drawn. */
    val crosshairs: List<SavedCrosshair> = emptyList(),
    /** Id of the selected entry of [crosshairs]; used in all scenarios. */
    val activeCrosshair: String? = null,
) {
    /** @throws SettingsException describing the first invalid field. */
    fun validate() {
        fun positive(name: String, value: Double?) {
            if (value != null && !(value.isFinite() && value > 0.0)) throw SettingsException("$name must be a positive number, was $value")
        }
        positive("dpi", dpi)
        positive("inGameSensitivity", inGameSensitivity)
        positive("customYaw", customYaw)
        positive("customFovDegrees", customFovDegrees)
        positive("padWidthCm", padWidthCm)
        positive("padHeightCm", padHeightCm)
        if (roundSeconds <= 0) throw SettingsException("roundSeconds must be positive, was $roundSeconds")
        aimStyleOrNull()
        toMetricWeights()
        enabledScenarios()
        validateCrosshairs()
    }

    /** @throws SettingsException for unknown scenario ids or an empty selection. */
    fun enabledScenarios(): Set<ScenarioKind> {
        if (scenarios.isEmpty()) throw SettingsException("at least one scenario must be enabled")
        return scenarios.map { id -> ScenarioKind.fromId(id) ?: throw SettingsException("unknown scenario '$id'") }.toSet()
    }

    /**
     * How much the player cares about flashes: the flash scenario is off, or its weight is the nearest level.
     * The weight is what makes the scenario play (see [de.yuuto.aimtune.core.scenario.ScenarioSchedule]).
     */
    fun flashImportance(): FlashImportance =
        if (ScenarioKind.FLASH.id !in scenarios) FlashImportance.NEVER else FlashImportance.of(metricWeights[ScoreMetric.FLASH_DODGE.name])

    /** These settings with the flash scenario switched on or off and its weight set for [importance]; the other scenarios stay. */
    fun withFlashImportance(importance: FlashImportance): AppSettings {
        val others = scenarios.filter { it != ScenarioKind.FLASH.id }
        val flash = if (importance == FlashImportance.NEVER) emptyList() else listOf(ScenarioKind.FLASH.id)
        return copy(
            metricWeights = metricWeights + (ScoreMetric.FLASH_DODGE.name to importance.weight),
            scenarios = (others + flash).ifEmpty { listOf(ScenarioKind.FLICK.id) },
        )
    }

    /** @throws SettingsException if [aimStyle] is set to something that is not an aim style. */
    fun aimStyleOrNull(): AimStyle? = aimStyle?.let { name ->
        AimStyle.entries.firstOrNull { it.name == name } ?: throw SettingsException("unknown aim style '$name'")
    }

    /** @throws SettingsException for unknown metric names or weights that are negative or all zero. */
    fun toMetricWeights(): MetricWeights {
        val byMetric = metricWeights.mapKeys { (name, _) ->
            ScoreMetric.entries.firstOrNull { it.name == name } ?: throw SettingsException("unknown metric '$name' in metricWeights")
        }
        return try {
            MetricWeights(byMetric)
        } catch (e: IllegalArgumentException) {
            throw SettingsException("invalid metricWeights: ${e.message}", e)
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val DEFAULT_ROUND_SECONDS = 45
        val DEFAULT_WEIGHTS: Map<String, Double> =
            MetricWeights.DEFAULT.values.mapKeys { it.key.name } + (ScoreMetric.FLASH_DODGE.name to FlashImportance.SOMETIMES.weight)
        val DEFAULT_SCENARIOS: List<String> = listOf(ScenarioKind.FLICK.id, ScenarioKind.FLASH.id)
    }
}
