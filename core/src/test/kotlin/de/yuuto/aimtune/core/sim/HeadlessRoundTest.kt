package de.yuuto.aimtune.core.sim

import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.scenario.TargetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeadlessRoundTest {
    private fun hits(player: SimulatedPlayer, cm360: Double = 50.0, seed: Long = 1) =
        HeadlessRound.flick(player, 800.0, cm360, seed, 30).targetEvents.count { it is TargetEvent.Hit }

    @Test
    fun `the same seed gives the same recording`() {
        val player = SimulatedPlayer()
        assertEquals(HeadlessRound.flick(player, 800.0, 50.0, 7, 20), HeadlessRound.flick(player, 800.0, 50.0, 7, 20))
    }

    @Test
    fun `a round has ordered events and realistic hit counts`() {
        val rec = HeadlessRound.flick(SimulatedPlayer(), 800.0, 50.0, 3, 30)
        assertTrue(rec.mouseEvents.zipWithNext().all { (a, b) -> a.timeNanos < b.timeNanos })
        assertTrue(rec.mouseEvents.all { it.timeNanos in rec.startNanos..rec.endNanos })
        val hits = rec.targetEvents.count { it is TargetEvent.Hit }
        assertTrue(hits in 12..45, "hits in 30 s: $hits")
    }

    @Test
    fun `the analysis finds corrections and a small first-movement error`() {
        val metrics = computeRoundMetrics(HeadlessRound.flick(SimulatedPlayer(), 800.0, 50.0, 4, 45))
        assertNotNull(metrics.errorSpread)
        assertTrue(metrics.errorSpread!! in 0.005..0.2, "spread ${metrics.errorSpread}")
        assertTrue(metrics.meanFirstError!! < 0.05, "players undershoot a little, mean ${metrics.meanFirstError}")
        assertTrue(metrics.accuracy > 0.8, "accuracy ${metrics.accuracy}")
    }

    @Test
    fun `a very low sensitivity ruins precision and a very high one costs time`() {
        val player = SimulatedPlayer()
        val best = hits(player, 50.0)
        assertTrue(hits(player, 12.0) < best / 2)
        assertTrue(hits(player, 250.0) < best)
    }

    @Test
    fun `more noise means fewer hits`() {
        assertTrue(hits(SimulatedPlayer(noiseLevel = 2.0)) < hits(SimulatedPlayer(noiseLevel = 0.5)))
    }

    @Test
    fun `the optimum can be moved by configuration`() {
        // A player whose optimum is 25 does better at 25 than a base player does there, and the reverse at 50.
        val low = SimulatedPlayer().withOptimum(25.0)
        val base = SimulatedPlayer()
        assertEquals(25.0, low.declaredOptimumCm360, 1e-9)
        assertTrue(hits(low, 25.0) > hits(base, 25.0))
        assertTrue(hits(base, 50.0) > hits(low, 50.0))
    }

    @Test
    fun `a gain preference of two makes a doubled sensitivity setting feel right`() {
        // At 100 cm/360 a plain player (optimum 50) is too slow; one that prefers gain 2 acts as if it were at 50.
        val preferring = SimulatedPlayer(gainPreference = { 2.0 })
        assertTrue(hits(preferring, 100.0, 5) > hits(SimulatedPlayer(), 100.0, 5))
    }
}
