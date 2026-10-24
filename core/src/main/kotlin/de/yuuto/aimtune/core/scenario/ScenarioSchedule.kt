package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreMetric

/** The score metrics a round of this scenario produces. */
val ScenarioKind.producedMetrics: Set<ScoreMetric>
    get() = when (this) {
        ScenarioKind.TRACKING -> setOf(ScoreMetric.TRACKING)
        // Accuracy and time to hit of the return hit also score a flash round, but they do not make it play more often.
        ScenarioKind.FLASH -> setOf(ScoreMetric.FLASH_DODGE)
        ScenarioKind.FLICK, ScenarioKind.MICRO, ScenarioKind.SWITCHING, ScenarioKind.MICRO_STRAFE, ScenarioKind.SWITCH_STRAFE, ScenarioKind.RANGE ->
            setOf(ScoreMetric.ACCURACY, ScoreMetric.TIME_TO_HIT)
    }

/**
 * Which scenario each round of a blind test plays. A round only measures the metrics of its own
 * scenario, so the weights decide how often a scenario is played: the weight of each metric is
 * split among the enabled scenarios that produce it, and a scenario's share of the rounds is the
 * sum of its metrics' weights. With the default weights and all scenarios on, tracking gets 20 % of
 * the rounds and the three click scenarios 26.7 % each. A scenario whose metrics all have weight 0
 * is not played. Rounds are spread evenly (smooth weighted round robin), and the plan for a round
 * index never changes, so a run can be resumed.
 */
class ScenarioSchedule(enabled: Set<ScenarioKind>, weights: MetricWeights) {
    /** Share of the rounds per scenario, summing to 1; only scenarios that are played. */
    val shares: Map<ScenarioKind, Double>

    private val played: List<ScenarioKind>

    init {
        val kinds = ScenarioKind.entries.filter { it in enabled }
        val raw = kinds.associateWith { kind ->
            kind.producedMetrics.sumOf { metric ->
                val producers = kinds.count { metric in it.producedMetrics }
                (weights.values[metric] ?: 0.0) / producers
            }
        }
        val usable = raw.filterValues { it > 0.0 }.ifEmpty { kinds.associateWith { 1.0 } }.ifEmpty { mapOf(ScenarioKind.FLICK to 1.0) }
        val total = usable.values.sum()
        shares = usable.mapValues { it.value / total }
        played = shares.keys.toList()
    }

    fun kindFor(roundIndex: Int): ScenarioKind {
        require(roundIndex >= 0) { "round index must not be negative" }
        val credit = played.associateWith { 0.0 }.toMutableMap()
        var pick = played.first()
        repeat(roundIndex + 1) {
            played.forEach { credit[it] = credit.getValue(it) + shares.getValue(it) }
            pick = played.maxBy { credit.getValue(it) }
            credit[pick] = credit.getValue(pick) - 1.0
        }
        return pick
    }
}
