package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.score.FlashImportance
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.ScoreMetric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioScheduleTest {
    private fun counts(schedule: ScenarioSchedule, rounds: Int) = (0 until rounds).groupingBy { schedule.kindFor(it) }.eachCount()

    @Test
    fun `default weights give tracking two ninths of the rounds and split the rest`() {
        val schedule = ScenarioSchedule(ScenarioKind.entries.toSet() - ScenarioKind.FLASH, MetricWeights.DEFAULT)
        assertEquals(2.0 / 9, schedule.shares.getValue(ScenarioKind.TRACKING), 1e-12)
        assertEquals(7.0 / 27, schedule.shares.getValue(ScenarioKind.FLICK), 1e-12)
        val played = counts(schedule, 135)
        assertEquals(30, played.getValue(ScenarioKind.TRACKING))
        assertTrue((ScenarioKind.entries - ScenarioKind.FLASH).all { (played[it] ?: 0) > 0 })
        assertEquals(135, played.values.sum())
    }

    @Test
    fun `flash rounds are played as often as the importance says and only the dodge weight counts for that`() {
        val enabled = setOf(ScenarioKind.FLICK, ScenarioKind.FLASH)
        fun share(importance: FlashImportance) =
            ScenarioSchedule(enabled, MetricWeights(MetricWeights.DEFAULT.values + (ScoreMetric.FLASH_DODGE to importance.weight))).shares[ScenarioKind.FLASH] ?: 0.0
        assertEquals(0.0, share(FlashImportance.NEVER))
        assertEquals(15.0 / 85, share(FlashImportance.SOMETIMES), 1e-12)
        assertEquals(35.0 / 105, share(FlashImportance.OFTEN), 1e-12)
        assertEquals(ScenarioKind.FLICK, ScenarioSchedule(enabled, MetricWeights.DEFAULT).kindFor(3), "default weights have no dodge weight")
    }

    @Test
    fun `a disabled scenario takes no share and its weight goes to the others`() {
        val noTracking = ScenarioSchedule(setOf(ScenarioKind.FLICK, ScenarioKind.MICRO), MetricWeights.DEFAULT)
        assertEquals(setOf(ScenarioKind.FLICK, ScenarioKind.MICRO), noTracking.shares.keys)
        assertEquals(0.5, noTracking.shares.getValue(ScenarioKind.FLICK), 1e-12)
        val flickAndTracking = ScenarioSchedule(setOf(ScenarioKind.FLICK, ScenarioKind.TRACKING), MetricWeights.DEFAULT)
        assertEquals(7.0 / 9, flickAndTracking.shares.getValue(ScenarioKind.FLICK), 1e-12)
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
        val schedule = ScenarioSchedule(ScenarioKind.entries.toSet() - ScenarioKind.FLASH, MetricWeights.DEFAULT)
        val first = (0 until 30).map { schedule.kindFor(it) }
        assertEquals(first, (0 until 30).map { ScenarioSchedule(ScenarioKind.entries.toSet() - ScenarioKind.FLASH, MetricWeights.DEFAULT).kindFor(it) })
        // Every window of 10 rounds contains tracking, and nothing is played twice in a row.
        for (from in 0..20) assertTrue(first.subList(from, from + 10).contains(ScenarioKind.TRACKING), "window $from")
        assertTrue(first.zipWithNext().none { (a, b) -> a == b }, "no scenario twice in a row: $first")
    }
}
