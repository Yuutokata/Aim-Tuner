package de.yuuto.aimtune.core.scenario

/** Basic outcome of a round, derived from its target events. Richer metrics come with milestone M3. */
data class RoundSummary(val hits: Int, val misses: Int, val meanTimeToHitNanos: Long?) {
    val shots: Int get() = hits + misses

    /** Hits per shot in [0, 1]; 0 when no shot was fired. */
    val accuracy: Double get() = if (shots == 0) 0.0 else hits.toDouble() / shots
}

/**
 * Time to hit is measured from the later of the target's spawn and the previous hit to the hit on
 * it. With one target at a time that is the spawn; with several visible at once (switching) it is
 * the time the player needed to get from the last hit to this one.
 */
fun summarize(events: List<TargetEvent>): RoundSummary {
    val spawnTimes = HashMap<Int, Long>()
    var hits = 0
    var misses = 0
    var totalHitTime = 0L
    var previousHitNanos = Long.MIN_VALUE
    for (event in events) {
        when (event) {
            is TargetEvent.Spawned -> spawnTimes[event.target.id] = event.timeNanos
            is TargetEvent.Hit -> {
                hits++
                val spawn = checkNotNull(spawnTimes[event.targetId]) { "hit without spawn: ${event.targetId}" }
                totalHitTime += event.timeNanos - maxOf(spawn, previousHitNanos)
                previousHitNanos = event.timeNanos
            }
            is TargetEvent.Miss -> misses++
            is TargetEvent.Waypoint -> Unit
        }
    }
    return RoundSummary(hits, misses, if (hits == 0) null else totalHitTime / hits)
}
