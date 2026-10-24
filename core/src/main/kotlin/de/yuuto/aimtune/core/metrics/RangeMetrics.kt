package de.yuuto.aimtune.core.metrics

import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.TargetEvent

/** What a Range round adds to the common metrics: kills and how long each bot took to die. */
data class RangeMetrics(
    val kills: Int,
    /** Spawn to kill per bot, over the bots that were killed. */
    val medianTimeToKillMs: Double?,
    /** Clicks (head hits, body hits and misses) per kill; null without a kill. */
    val shotsPerKill: Double?,
)

/** Kills, time to kill per bot and shots per kill of a Range round, from its target events. */
fun computeRangeMetrics(recording: RoundRecording): RangeMetrics {
    val spawns = HashMap<Int, Long>()
    val timesToKill = ArrayList<Double>()
    var shots = 0
    for (event in recording.targetEvents) {
        when (event) {
            is TargetEvent.Spawned -> spawns[event.target.id] = event.timeNanos
            is TargetEvent.Hit -> {
                shots++
                spawns[event.targetId]?.let { timesToKill += (event.timeNanos - it) / 1e6 }
            }
            is TargetEvent.Miss, is TargetEvent.BodyHit -> shots++
            is TargetEvent.Waypoint, is TargetEvent.Despawned, is TargetEvent.FlashCue, is TargetEvent.FlashPop -> Unit
        }
    }
    val sorted = timesToKill.sorted()
    val median = when {
        sorted.isEmpty() -> null
        sorted.size % 2 == 1 -> sorted[sorted.size / 2]
        else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }
    return RangeMetrics(timesToKill.size, median, if (timesToKill.isEmpty()) null else shots.toDouble() / timesToKill.size)
}
