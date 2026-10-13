package de.yuuto.aimtune.storage

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
    val gameId: String? = null,
    val inGameSensitivity: Double? = null,
    /** [AimStyle] name; stored as text so the core enum stays free of serialization. */
    val aimStyle: String? = null,
    val padWidthCm: Double? = null,
    val padHeightCm: Double? = null,
    val roundSeconds: Int = DEFAULT_ROUND_SECONDS,
    val metricWeights: Map<String, Double> = DEFAULT_WEIGHTS,
) {
    /** @throws SettingsException describing the first invalid field. */
    fun validate() {
        fun positive(name: String, value: Double?) {
            if (value != null && !(value.isFinite() && value > 0.0)) throw SettingsException("$name must be a positive number, was $value")
        }
        positive("dpi", dpi)
        positive("inGameSensitivity", inGameSensitivity)
        positive("padWidthCm", padWidthCm)
        positive("padHeightCm", padHeightCm)
        if (roundSeconds <= 0) throw SettingsException("roundSeconds must be positive, was $roundSeconds")
        aimStyleOrNull()
        toMetricWeights()
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
        val DEFAULT_WEIGHTS: Map<String, Double> = MetricWeights.DEFAULT.values.mapKeys { it.key.name }
    }
}
