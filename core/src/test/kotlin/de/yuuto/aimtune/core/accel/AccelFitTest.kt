package de.yuuto.aimtune.core.accel

import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccelFitTest {
    /** The gain a player wants: rises from 1 toward 1.5 with the hand speed, like Raw Accel's natural mode. */
    private fun wanted(v: Double) = 1.0 + 0.5 * (1.0 - exp(-v / 6.0))

    /** Movements at log-uniform speeds whose error is what a wrong gain produces: landing at `gain delivered / gain wanted`. */
    private fun samples(count: Int, seed: Long, noise: Double = 0.06, wantedGain: (Double) -> Double): List<MovementSample> {
        val random = Random(seed)
        return List(count) {
            val v = exp(random.nextDouble(ln(1.0), ln(25.0)))
            val ratio = exp(-ln(wantedGain(v)) + noise * gaussian(random))
            MovementSample(v, ratio - 1.0)
        }
    }

    private fun gaussian(random: Random) = sqrt(-2.0 * ln(1.0 - random.nextDouble())) * kotlin.math.cos(2.0 * Math.PI * random.nextDouble())

    @Test
    fun `the analysis recovers how the wanted gain changes with speed`() {
        val analysis = AccelAnalysis(samples(600, 1, wantedGain = ::wanted))
        assertTrue(analysis.points.size >= 6)
        assertTrue(analysis.points.zipWithNext().all { (a, b) -> a.speed < b.speed })
        // Against the truth, normalized to an average of 0 over the movements.
        val truth = analysis.points.map { ln(wanted(it.speed)) }
        val shift = analysis.points.indices.sumOf { analysis.points[it].logGain - truth[it] } / analysis.points.size
        analysis.points.indices.forEach { assertEquals(truth[it] + shift, analysis.points[it].logGain, 0.05, "speed ${analysis.points[it].speed}") }
        // Normalized: the median wanted log gain over all movements is 0 (so the count-weighted average of the bins is close to it).
        assertEquals(0.0, analysis.points.sumOf { it.logGain * it.count } / analysis.used.size, 0.03)
    }

    @Test
    fun `a rising wanted gain gives a curve that matches it`() {
        val result = fitAccel(AccelAnalysis(samples(600, 2, wantedGain = ::wanted)))
        val fit = assertNotNull(result.recommended, result.reason)
        assertTrue(fit.bicGain > 10 && fit.gainRange > 1.1, "bic ${fit.bicGain} range ${fit.gainRange}")
        // The fitted total gain follows the wanted gain up to a constant at every measured speed.
        val ratios = listOf(1.5, 3.0, 6.0, 12.0, 20.0).map { fit.totalGain(it) / wanted(it) }
        val spread = ratios.max() / ratios.min()
        assertTrue(spread < 1.06, "fit deviates by $spread: ${ratios.map { "%.3f".format(it) }}")
    }

    @Test
    fun `a falling wanted gain gives a falling curve`() {
        val result = fitAccel(AccelAnalysis(samples(600, 3) { v -> 1.0 / wanted(v) }))
        val fit = assertNotNull(result.recommended, result.reason)
        assertTrue(fit.totalGain(20.0) < fit.totalGain(1.5), "gain at 20 in/s ${fit.totalGain(20.0)} vs 1.5 in/s ${fit.totalGain(1.5)}")
    }

    @Test
    fun `a player who wants the same gain at every speed gets no curve`() {
        var recommended = 0
        for (seed in 1L..30L) {
            if (fitAccel(AccelAnalysis(samples(400, seed) { 1.0 })).recommended != null) recommended++
        }
        // A constant error is a sensitivity matter, not an acceleration matter.
        assertTrue(recommended <= 2, "$recommended of 30 flat players were offered a curve")
    }

    @Test
    fun `too few movements or a narrow speed range are explained`() {
        val few = fitAccel(AccelAnalysis(samples(40, 4, wantedGain = ::wanted)))
        assertNull(few.recommended)
        assertTrue(few.reason.contains("Not enough"))
        val narrow = fitAccel(AccelAnalysis(List(300) { MovementSample(5.0 + it * 0.001, 0.0) }))
        assertNull(narrow.recommended)
        assertTrue(narrow.reason.contains("same speed"))
    }

    @Test
    fun `implausible first movements are left out`() {
        val odd = listOf(MovementSample(5.0, -0.95), MovementSample(5.0, 4.0), MovementSample(0.0, 0.1), MovementSample(5.0, 0.1))
        assertEquals(1, AccelAnalysis(odd).used.size)
    }

    @Test
    fun `the simulated player who expects a rising gain is found out and one who does not is left alone`() {
        val wants = SimulatedPlayer(gainPreference = { v -> wanted(v) })
        fun analysisOf(player: SimulatedPlayer, seed: Long): AccelAnalysis {
            // Click scenarios together: micro-adjust moves slowly, flicks and switching fast.
            val kinds = listOf(ScenarioKind.FLICK, ScenarioKind.MICRO, ScenarioKind.SWITCHING)
            val metrics = (0 until 15).map { computeRoundMetrics(HeadlessRound.of(kinds[it % 3], player, DPI, 55.0, seed * 100 + it, 45)) }
            return AccelAnalysis(movementSamples(metrics, DPI))
        }
        val found = fitAccel(analysisOf(wants, 1))
        val fit = assertNotNull(found.recommended, found.reason)
        assertTrue(fit.gainRange > 1.1, "range ${fit.gainRange}")
        assertTrue(fit.totalGain(15.0) > fit.totalGain(2.0), "rising: ${fit.totalGain(2.0)} -> ${fit.totalGain(15.0)}")

        val offered = (1L..8L).count { fitAccel(analysisOf(SimulatedPlayer(), it)).recommended != null }
        assertTrue(offered <= 1, "$offered of 8 flat simulated players were offered a curve")
        assertTrue(abs(ln(fit.sensitivityFactor)) < 0.35, "factor ${fit.sensitivityFactor}")
    }

    private companion object {
        const val DPI = 800.0
    }
}
