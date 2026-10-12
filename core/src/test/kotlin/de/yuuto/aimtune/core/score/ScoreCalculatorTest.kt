package de.yuuto.aimtune.core.score

import de.yuuto.aimtune.core.score.ScoreMetric.ACCURACY
import de.yuuto.aimtune.core.score.ScoreMetric.CONSISTENCY
import de.yuuto.aimtune.core.score.ScoreMetric.TIME_TO_HIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreCalculatorTest {
    private val calculator = ScoreCalculator(MetricWeights.DEFAULT)

    private fun round(accuracy: Double, ttkMs: Double, spread: Double? = null) =
        buildMap {
            put(ACCURACY, accuracy)
            put(TIME_TO_HIT, ttkMs)
            if (spread != null) put(CONSISTENCY, spread)
        }

    @Test
    fun `default weights follow the design mockup`() {
        val w = MetricWeights.DEFAULT.normalized(ScoreMetric.entries.toSet())
        assertEquals(0.4, w.getValue(ACCURACY), 1e-12)
        assertEquals(0.3, w.getValue(TIME_TO_HIT), 1e-12)
        assertEquals(0.2, w.getValue(ScoreMetric.TRACKING), 1e-12)
        assertEquals(0.1, w.getValue(CONSISTENCY), 1e-12)
    }

    @Test
    fun `weights are renormalized over the available metrics`() {
        val w = MetricWeights.DEFAULT.normalized(setOf(ACCURACY, TIME_TO_HIT))
        assertEquals(4.0 / 7.0, w.getValue(ACCURACY), 1e-12)
        assertEquals(3.0 / 7.0, w.getValue(TIME_TO_HIT), 1e-12)
    }

    @Test
    fun `better accuracy and faster hits score higher`() {
        val rounds = listOf(round(0.6, 700.0), round(0.7, 600.0), round(0.8, 500.0), round(0.9, 400.0))
        val scores = calculator.scoreRounds(rounds).map { assertNotNull(it) }
        assertEquals(scores.sorted(), scores)
        assertTrue(scores.last() > 50 && scores.first() < 50)
    }

    @Test
    fun `lower error spread scores higher`() {
        val rounds = listOf(round(0.8, 500.0, 0.30), round(0.8, 500.0, 0.20), round(0.8, 500.0, 0.10))
        val scores = calculator.scoreRounds(rounds).map { assertNotNull(it) }
        assertTrue(scores[0] < scores[1] && scores[1] < scores[2])
    }

    @Test
    fun `a typical round scores near 50 and scores stay inside 0 to 100`() {
        val rounds = listOf(round(0.6, 700.0), round(0.7, 600.0), round(0.8, 500.0))
        val scores = calculator.scoreRounds(rounds).map { assertNotNull(it) }
        assertEquals(50.0, scores[1], 0.5)
        assertTrue(scores.all { it in 0.0..100.0 })
    }

    @Test
    fun `too few rounds give no score`() {
        assertTrue(calculator.scoreRounds(listOf(round(0.8, 500.0), round(0.7, 600.0))).all { it == null })
    }

    @Test
    fun `a metric without variation is ignored instead of dividing by zero`() {
        val rounds = listOf(round(0.8, 700.0), round(0.8, 600.0), round(0.8, 500.0))
        val scores = calculator.scoreRounds(rounds).map { assertNotNull(it) }
        assertTrue(scores[2] > scores[0])
    }

    @Test
    fun `rounds missing a metric are scored on the others`() {
        val rounds = listOf(round(0.6, 700.0, 0.3), round(0.7, 600.0, 0.2), round(0.8, 500.0, 0.1), round(0.9, 400.0))
        val scores = calculator.scoreRounds(rounds)
        assertNotNull(scores.last())
        assertNull(ScoreCalculator(MetricWeights.DEFAULT).scoreRounds(listOf(emptyMap(), emptyMap(), emptyMap())).firstOrNull())
    }

    @Test
    fun `normal cdf matches known values`() {
        assertEquals(0.5, normalCdf(0.0), 1e-7)
        assertEquals(0.97500, normalCdf(1.96), 1e-4)
        assertEquals(0.15866, normalCdf(-1.0), 1e-4)
        assertEquals(1.0, normalCdf(8.0), 1e-7)
    }

    @Test
    fun `invalid weights are rejected`() {
        assertFailsWith<IllegalArgumentException> { MetricWeights(mapOf(ACCURACY to -1.0)) }
        assertFailsWith<IllegalArgumentException> { MetricWeights(mapOf(ACCURACY to 0.0)) }
    }
}
