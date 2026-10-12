package de.yuuto.aimtune.core.recording

import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.math.ViewAngles
import de.yuuto.aimtune.core.scenario.TargetEvent
import de.yuuto.aimtune.core.units.degreesPerCount

/**
 * Everything needed to recompute a round's metrics: raw mouse counts with timestamps and the target
 * events, plus the sensitivity that was active. The view always starts at yaw 0, pitch 0 at
 * [startNanos]. Times are nanoseconds on one monotonic clock.
 */
data class RoundRecording(
    val scenarioId: String,
    val seed: Long,
    val dpi: Double,
    val cm360: Double,
    val startNanos: Long,
    val endNanos: Long,
    val mouseEvents: List<MouseEvent>,
    val targetEvents: List<TargetEvent>,
) {
    val degreesPerCount: Double get() = degreesPerCount(dpi, cm360)
}

/** The camera orientation over time, rebuilt from the recorded counts. */
class ViewTrack(recording: RoundRecording) {
    private val times = LongArray(recording.mouseEvents.size)
    private val views: Array<ViewAngles>
    private val start = ViewAngles()

    init {
        var view = start
        val dpc = recording.degreesPerCount
        views = Array(recording.mouseEvents.size) { i ->
            val e = recording.mouseEvents[i]
            times[i] = e.timeNanos
            view = view.rotatedByCounts(e.dx, e.dy, dpc)
            view
        }
    }

    /** Orientation after every event with time <= [timeNanos]. */
    fun viewAt(timeNanos: Long): ViewAngles {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= timeNanos) lo = mid + 1 else hi = mid
        }
        return if (lo == 0) start else views[lo - 1]
    }
}
