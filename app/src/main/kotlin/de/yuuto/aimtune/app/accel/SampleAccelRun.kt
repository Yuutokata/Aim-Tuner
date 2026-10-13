package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import kotlin.math.exp

private const val SAMPLE_ROUND_SECONDS = 45
private const val SAMPLE_SEED = 3L
private const val CALIBRATION_ROUNDS_SHOWN = 6
private const val TEST_ROUNDS_SHOWN = 3

/** A simulated player who expects the gain to rise with the hand speed; Raw Accel's natural curve can deliver it. */
val risingGainPlayer = SimulatedPlayer(gainPreference = { speed -> 1.0 + 0.5 * (1.0 - exp(-speed / 6.0)) })

/**
 * An acceleration run played by the simulated [risingGainPlayer] up to [until], for the screenshot
 * tour and tests only: the analysis and verdict screens need a real fit to show. Never user data.
 */
fun sampleAccelRun(config: TrainerConfig, until: AccelPhase): AccelRun {
    val sampleConfig = config.copy(roundSeconds = SAMPLE_ROUND_SECONDS)
    val run = AccelRun(sampleConfig, MetricWeights.DEFAULT, runKey = 0L, seed = SAMPLE_SEED)
    fun playRound() {
        val next = run.nextRoundConfig()
        run.recordRound(
            HeadlessRound.of(next.scenario, risingGainPlayer, next.dpi, next.cm360, next.seed, next.roundSeconds, accel = next.accel),
        )
    }
    when (until) {
        AccelPhase.CALIBRATION -> repeat(CALIBRATION_ROUNDS_SHOWN) { playRound() }
        AccelPhase.ANALYSIS, AccelPhase.TEST, AccelPhase.DONE -> {
            while (run.phase == AccelPhase.CALIBRATION) playRound()
            if (until == AccelPhase.ANALYSIS || run.candidate == null) return run
            run.startTest()
            if (until == AccelPhase.TEST) repeat(TEST_ROUNDS_SHOWN) { playRound() } else while (run.phase == AccelPhase.TEST) playRound()
        }
    }
    return run
}
