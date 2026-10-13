package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreMetric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioScheduleTest {
    private fun counts(schedule: ScenarioSchedule, rounds: Int) = (0 until rounds).groupingBy { schedule.kindFor(it) }.eachCount()

    @Test
    fun `default weights give tracking a fifth of the rounds and split the rest`() {
        val schedule = ScenarioSchedule(ScenarioKind.entries.toSet(), MetricWeights.DEFAULT)
        assertEquals(0.2, schedule.shares.getValue(ScenarioKind.TRACKING), 1e-12)
        assertEquals(0.8 / 3, schedule.shares.getValue(ScenarioKind.FLICK), 1e-12)
        val played = counts(schedule, 150)
        assertEquals(30, played.getValue(ScenarioKind.TRACKING))
        assertTrue(ScenarioKind.entries.all { (played[it] ?: 0) > 0 })
        assertEquals(150, played.values.sum())
    }

    @Test
    fun `a disabled scenario takes no share and its weight goes to the others`() {
        val noTracking = ScenarioSchedule(setOf(ScenarioKind.FLICK, ScenarioKind.MICRO), MetricWeights.DEFAULT)
        assertEquals(setOf(ScenarioKind.FLICK, ScenarioKind.MICRO), noTracking.shares.keys)
        assertEquals(0.5, noTracking.shares.getValue(ScenarioKind.FLICK), 1e-12)
        val flickAndTracking = ScenarioSchedule(setOf(ScenarioKind.FLICK, ScenarioKind.TRACKING), MetricWeights.DEFAULT)
        assertEquals(0.8, flickAndTracking.shares.getValue(ScenarioKind.FLICK), 1e-12)
    }

    @Test
    fun `a scenario whose metrics have no weight is not played`() {
        val weights = MetricWeights(mapOf(ScoreMetric.ACCURACY to 1.0, ScoreMetric.TRACKING to 0.0))
        val schedule = ScenarioSchedule(setOf(ScenarioKind.FLICK, ScenarioKind.TRACKING), weights)
        assertEquals(setOf(ScenarioKind.FLICK), schedule.shares.keys)
        assertTrue((0 until 20).all { schedule.kindFor(it) == ScenarioKind.FLICK })
    }

    @Test
    fun `only tracking with zero weight still plays something`() {
        val weights = MetricWeights(mapOf(ScoreMetric.ACCURACY to 1.0, ScoreMetric.TRACKING to 0.0))
        assertEquals(ScenarioKind.TRACKING, ScenarioSchedule(setOf(ScenarioKind.TRACKING), weights).kindFor(0))
        assertEquals(ScenarioKind.FLICK, ScenarioSchedule(emptySet(), weights).kindFor(0))
    }

    @Test
    fun `the plan is spread evenly and never changes`() {
        val schedule = ScenarioSchedule(ScenarioKind.entries.toSet(), MetricWeights.DEFAULT)
        val first = (0 until 30).map { schedule.kindFor(it) }
        assertEquals(first, (0 until 30).map { ScenarioSchedule(ScenarioKind.entries.toSet(), MetricWeights.DEFAULT).kindFor(it) })
        // Every window of 10 rounds contains tracking, and nothing is played twice in a row.
        for (from in 0..20) assertTrue(first.subList(from, from + 10).contains(ScenarioKind.TRACKING), "window $from")
        assertTrue(first.zipWithNext().none { (a, b) -> a == b }, "no scenario twice in a row: $first")
    }
}
