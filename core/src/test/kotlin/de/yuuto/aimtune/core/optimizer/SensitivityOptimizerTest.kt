package de.yuuto.aimtune.core.optimizer

import de.yuuto.aimtune.core.units.SearchRange
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SensitivityOptimizerTest {
    private val range = SearchRange(20.0, 100.0)
    private val truth = 45.0

    /** A noisy score with a known peak at [truth] on the log scale. */
    private fun score(cm360: Double, random: Random, noise: Double = 3.0): Double {
        val d = ln(cm360 / truth)
        return 70.0 - 40.0 * d * d + noise * (random.nextDouble() * 2 - 1)
    }

    private fun run(rounds: Int, seed: Long, noise: Double = 3.0): Pair<SensitivityOptimizer, List<Observation>> {
        val optimizer = SensitivityOptimizer(range, seed)
        val random = Random(seed)
        var history = emptyList<Observation>()
        repeat(rounds) {
            val s = optimizer.suggestNext(history)
            history = history + Observation(0, s.cm360, score(s.cm360, random, noise))
        }
        return optimizer to history
    }

    @Test
    fun `suggestions are deterministic for a history and seed`() {
        val (optimizer, history) = run(9, seed = 3)
        assertEquals(optimizer.suggestNext(history), optimizer.suggestNext(history))
        assertNotEquals(SensitivityOptimizer(range, 4).suggestNext(emptyList()), SensitivityOptimizer(range, 3).suggestNext(emptyList()))
    }

    @Test
    fun `the first rounds spread over the whole range in a hidden random order`() {
        val optimizer = SensitivityOptimizer(range, seed = 11)
        val firsts = (0 until 6).map { optimizer.suggestNext(List(it) { i -> Observation(0, 40.0, 50.0 + i) }) }
        assertTrue(firsts.all { it.kind == SuggestionKind.DESIGN && it.cm360 in range })
        val sorted = firsts.map { it.cm360 }.sorted()
        assertTrue(sorted.first() < 30.0 && sorted.last() > 70.0, "design must cover the range: $sorted")
        assertNotEquals(sorted, firsts.map { it.cm360 }, "order must not be ascending")
    }

    @Test
    fun `later suggestions stay in range, avoid the last point and include repeats`() {
        val (optimizer, history) = run(24, seed = 5)
        assertTrue(history.all { it.cm360 in range })
        assertTrue(history.zipWithNext().none { (a, b) -> abs(a.x - b.x) < 1e-9 }, "no immediate duplicates")
        val repeats = history.indices.count { i -> (0 until i).any { j -> abs(history[i].x - history[j].x) < 0.03 } }
        assertTrue(repeats >= 1, "the optimizer must retest a point to learn the noise")
        assertTrue(optimizer.suggestNext(history).cm360 in range)
    }

    @Test
    fun `it finds the peak of a noisy function and the interval contains it`() {
        var hits = 0
        for (seed in 1L..6L) {
            val (optimizer, history) = run(30, seed)
            val result = assertNotNull(optimizer.analyze(history))
            assertTrue(abs(ln(result.optimumCm360 / truth)) < 0.2, "seed $seed: ${result.optimumCm360}")
            if (truth in result.intervalLowCm360..result.intervalHighCm360) hits++
            assertTrue(result.intervalLowCm360 <= result.optimumCm360 + 1e-9 || result.intervalHighCm360 >= result.optimumCm360 - 1e-9)
        }
        assertTrue(hits >= 4, "interval contained the truth only $hits of 6 times")
    }

    @Test
    fun `confidence is capped early and grows with data, and a recommendation follows it`() {
        val early = assertNotNull(run(6, 2).let { (o, h) -> o.analyze(h) })
        assertTrue(early.confidencePercent <= 50)
        val late = assertNotNull(run(40, 2).let { (o, h) -> o.analyze(h) })
        assertTrue(late.confidencePercent > early.confidencePercent)
        assertEquals(late.confidencePercent < 85, late.recommendMoreRounds)
        assertEquals(late.recommendMoreRounds, late.roundsToTarget > 0)
    }

    @Test
    fun `too few rounds give no result`() {
        assertNull(SensitivityOptimizer(range).analyze(listOf(Observation(0, 40.0, 50.0), Observation(0, 50.0, 55.0))))
    }

    @Test
    fun `rounds of several sessions merge into one result`() {
        val optimizer = SensitivityOptimizer(range, 7)
        val random = Random(7)
        var history = emptyList<Observation>()
        repeat(28) { i ->
            val session = if (i < 14) 1L else 2L
            val s = optimizer.suggestNext(history)
            // The second session is a worse day: every score is 10 points lower.
            val offset = if (session == 2L) -10.0 else 0.0
            history = history + Observation(session, s.cm360, score(s.cm360, random) + offset)
        }
        val result = assertNotNull(optimizer.analyze(history))
        assertEquals(2, result.sessions)
        assertTrue(abs(ln(result.optimumCm360 / truth)) < 0.25, "merged optimum ${result.optimumCm360}")
        assertTrue(exp(abs(ln(result.curve.maxBy { it.meanScore }.cm360 / truth))) < 1.3)
    }
}
