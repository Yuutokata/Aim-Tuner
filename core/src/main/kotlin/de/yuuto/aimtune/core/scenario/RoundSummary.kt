package de.yuuto.aimtune.core.scenario

/** Basic outcome of a round, derived from its target events. Richer metrics come with milestone M3. */
data class RoundSummary(val hits: Int, val misses: Int, val meanTimeToHitNanos: Long?) {
    val shots: Int get() = hits + misses

    /** Hits per shot in [0, 1]; 0 when no shot was fired. */
    val accuracy: Double get() = if (shots == 0) 0.0 else hits.toDouble() / shots
}

/** Time to hit is measured from a target's spawn to the hit on it. */
fun summarize(events: List<TargetEvent>): RoundSummary {
    val spawnTimes = HashMap<Int, Long>()
    var hits = 0
    var misses = 0
    var totalHitTime = 0L
    for (event in events) {
        when (event) {
            is TargetEvent.Spawned -> spawnTimes[event.target.id] = event.timeNanos
            is TargetEvent.Hit -> {
                hits++
                totalHitTime += event.timeNanos - checkNotNull(spawnTimes[event.targetId]) { "hit without spawn: ${event.targetId}" }
            }
            is TargetEvent.Miss -> misses++
        }
    }
    return RoundSummary(hits, misses, if (hits == 0) null else totalHitTime / hits)
}
