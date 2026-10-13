package de.yuuto.aimtune.core.accel

import de.yuuto.aimtune.core.metrics.RoundMetrics
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** One first movement: its peak hand speed in inches per second and its signed endpoint error (> 0 overshoot). */
data class MovementSample(val speed: Double, val errorRatio: Double)

/** The samples of rounds played without acceleration, taken from their analysed movements. */
fun movementSamples(metrics: List<RoundMetrics>, dpi: Double): List<MovementSample> =
    metrics.flatMap { it.movements }.mapNotNull { movement ->
        val peak = movement.firstPeakSpeedCountsPerMs ?: return@mapNotNull null
        val error = movement.firstErrorRatio ?: return@mapNotNull null
        MovementSample(AccelProcessor.speedInPerSecond(peak, dpi), error)
    }

/** The gain the player wanted around one speed, relative to the average over all movements. */
data class GainPoint(val speed: Double, val logGain: Double, val standardError: Double, val count: Int) {
    val gain: Double get() = exp(logGain)
}

/**
 * Error against speed: a movement that landed at `(1 + e)` times its distance wanted `1 / (1 + e)`
 * times the gain it got. Movements are grouped into bins of similar speed (equal counts), each bin's
 * mean log ratio gives the wanted log gain there, and the whole is normalized so the average wanted
 * log gain over all movements is 0: it says how the wanted gain changes with speed, not what the
 * sensitivity should be (that is the optimizer's job). See docs/ALGORITHMS.md ("Acceleration").
 */
class AccelAnalysis(samples: List<MovementSample>, private val minPerBin: Int = MIN_PER_BIN) {
    /** Samples used: movements with a plausible ratio (the first movement went toward the target). */
    val used: List<MovementSample> = samples.filter { it.speed > 0.0 && (1.0 + it.errorRatio) in MIN_RATIO..MAX_RATIO }

    val points: List<GainPoint>

    /** Ratio of the fastest to the slowest bin speed; a curve cannot be judged without a wide range. */
    val speedRange: Double get() = if (points.size < 2) 1.0 else points.last().speed / points.first().speed

    init {
        val logRatios = used.map { ln(1.0 + it.errorRatio) }
        val meanLog = if (logRatios.isEmpty()) 0.0 else logRatios.average()
        val bins = binCount(used.size)
        val sorted = used.indices.sortedBy { used[it].speed }
        points = if (bins < 2) {
            emptyList()
        } else {
            (0 until bins).map { b ->
                val slice = sorted.subList(b * sorted.size / bins, (b + 1) * sorted.size / bins)
                val ys = slice.map { logRatios[it] }
                val mean = ys.average()
                val sd = if (ys.size > 1) sqrt(ys.sumOf { (it - mean) * (it - mean) } / (ys.size - 1)) else 0.0
                val speeds = slice.map { used[it].speed }.sorted()
                GainPoint(speeds[speeds.size / 2], meanLog - mean, sd / sqrt(ys.size.toDouble()), ys.size)
            }
        }
    }

    private fun binCount(count: Int): Int = (count / minPerBin).coerceAtMost(MAX_BINS).let { if (it < MIN_BINS) 0 else it }

    companion object {
        const val MIN_PER_BIN = 20
        const val MIN_BINS = 4
        const val MAX_BINS = 10

        /** A first movement that landed at under a fifth or over three times the distance was not a reach for this target. */
        const val MIN_RATIO = 0.2
        const val MAX_RATIO = 3.0
    }
}
