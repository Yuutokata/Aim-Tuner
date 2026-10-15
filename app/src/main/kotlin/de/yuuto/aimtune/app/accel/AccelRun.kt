package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.app.i18n.UiText
import de.yuuto.aimtune.core.accel.AbCondition
import de.yuuto.aimtune.core.accel.AbOutcome
import de.yuuto.aimtune.core.accel.AbStatus
import de.yuuto.aimtune.core.accel.AbVerdict
import de.yuuto.aimtune.core.accel.AccelAbPlan
import de.yuuto.aimtune.core.accel.AccelAnalysis
import de.yuuto.aimtune.core.accel.AccelFit
import de.yuuto.aimtune.core.accel.AccelFitResult
import de.yuuto.aimtune.core.accel.evaluateAb
import de.yuuto.aimtune.core.accel.fitAccel
import de.yuuto.aimtune.core.accel.trialAccel
import de.yuuto.aimtune.core.accel.movementSamples
import de.yuuto.aimtune.core.metrics.RoundMetrics
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.scoreInputs

enum class AccelPhase {
    /** Rounds with the plain sensitivity, to see how first movements miss at different speeds. */
    CALIBRATION,

    /** The calibration is done and the fit is available; the user decides whether to test a curve. */
    ANALYSIS,

    /** The blind A/B test of plain against the fitted curve. */
    TEST,

    /** The test has decided (or there was nothing to test). */
    DONE,
}

/**
 * The acceleration steps as one state machine without any UI: calibration rounds at the recommended
 * sensitivity, the analysis and fit, and the blind A/B test. [baseConfig] holds the sensitivity to
 * start from (the optimizer's recommendation). All click scenarios are played, because flicks alone
 * cover too narrow a range of hand speeds to judge a curve; tracking has no first movements.
 * Nothing here reveals which condition a test round has.
 */
class AccelRun(
    private val baseConfig: TrainerConfig,
    private val weights: MetricWeights,
    val runKey: Long,
    private val seed: Long,
    val calibrationRounds: Int = DEFAULT_CALIBRATION_ROUNDS,
    maxPairs: Int = AccelAbPlan.DEFAULT_MAX_PAIRS,
) {
    var phase = AccelPhase.CALIBRATION
        private set

    var fit: AccelFitResult? = null
        private set

    var verdict: AbVerdict? = null
        private set

    private val calibration = mutableListOf<RoundMetrics>()
    private val plan = AccelAbPlan(CLICK_SCENARIOS, seed, maxPairs)
    private val outcomes = mutableListOf<AbOutcome>()

    val baseCm360: Double get() = baseConfig.cm360

    val dpi: Double get() = baseConfig.dpi

    private var trial: AccelFit? = null

    /** The curve under test: the one the analysis recommended, or the mild trial curve the player asked for. */
    val candidate: AccelFit? get() = fit?.recommended ?: trial

    /** True when the curve under test is a hypothesis of the player's and not a finding of the analysis. */
    val isTrial: Boolean get() = fit?.recommended == null && trial != null

    /** Whether the wanted gain the analysis measured rises with speed; a guess for the direction of a trial curve. */
    val trialRises: Boolean
        get() {
            val points = fit?.points.orEmpty()
            return points.size < 2 || points.last().logGain >= points.first().logGain
        }

    val calibrationDone: Int get() = calibration.size

    val testRoundsDone: Int get() = outcomes.size

    val maxTestRounds: Int get() = 2 * plan.maxPairs

    /** HUD label: where the user is, without the condition of a test round. */
    val label: UiText
        get() = when (phase) {
            AccelPhase.CALIBRATION -> UiText("accel.label.calibration", calibration.size + 1, calibrationRounds)
            AccelPhase.TEST -> UiText("accel.label.test", outcomes.size + 1)
            else -> UiText("")
        }

    /** The config of the next round of the current phase. */
    fun nextRoundConfig(): TrainerConfig = when (phase) {
        AccelPhase.CALIBRATION -> baseConfig.copy(
            scenario = CLICK_SCENARIOS[calibration.size % CLICK_SCENARIOS.size],
            seed = seed * CALIBRATION_SEED_MIX + calibration.size,
            accel = null,
        )
        AccelPhase.TEST -> {
            val round = plan.round(outcomes.size)
            val candidate = checkNotNull(candidate) { "no curve to test" }
            val accelerated = round.condition == AbCondition.ACCEL
            baseConfig.copy(
                cm360 = if (accelerated) baseConfig.cm360 / candidate.sensitivityFactor else baseConfig.cm360,
                scenario = round.scenario,
                seed = round.seed,
                accel = if (accelerated) candidate.curve else null,
            )
        }
        AccelPhase.ANALYSIS, AccelPhase.DONE -> error("no round is due in phase $phase")
    }

    /** Takes a finished round of the current phase and moves on when the phase is complete. */
    fun recordRound(recording: RoundRecording) {
        val metrics = computeRoundMetrics(recording)
        when (phase) {
            AccelPhase.CALIBRATION -> {
                calibration += metrics
                if (calibration.size >= calibrationRounds) finishCalibration()
            }
            AccelPhase.TEST -> {
                val round = plan.round(outcomes.size)
                outcomes += AbOutcome(round.pair, round.condition, metrics.scoreInputs())
                if (outcomes.size % 2 == 0) decide()
            }
            AccelPhase.ANALYSIS, AccelPhase.DONE -> error("no round is due in phase $phase")
        }
    }

    /** True once enough calibration rounds are played to analyse early, before all of them. */
    val canAnalyseNow: Boolean get() = phase == AccelPhase.CALIBRATION && calibration.size >= minOf(MIN_CALIBRATION_ROUNDS, calibrationRounds)

    /** Ends the calibration with the rounds played so far; the analysis says if they were enough. */
    fun analyseNow() {
        check(canAnalyseNow) { "too few calibration rounds for an analysis" }
        finishCalibration()
    }

    /** Begins the blind test of the recommended curve. */
    fun startTest() {
        check(phase == AccelPhase.ANALYSIS && candidate != null) { "there is no curve to test" }
        phase = AccelPhase.TEST
    }

    /** Begins the blind test of a mild trial curve although the analysis found none. */
    fun startTrial(rising: Boolean = trialRises) {
        check(phase == AccelPhase.ANALYSIS && fit?.recommended == null) { "the analysis already has a curve, or it is not done" }
        trial = trialAccel(rising)
        phase = AccelPhase.TEST
    }

    /** Ends the run after the analysis when no curve was recommended or the user does not want to test it. */
    fun finish() {
        phase = AccelPhase.DONE
    }

    private fun finishCalibration() {
        fit = fitAccel(AccelAnalysis(movementSamples(calibration, dpi)))
        phase = AccelPhase.ANALYSIS
    }

    private fun decide() {
        val result = evaluateAb(outcomes, weights, maxPairs = plan.maxPairs)
        verdict = result
        if (result.status != AbStatus.NEED_MORE_ROUNDS) phase = AccelPhase.DONE
    }

    companion object {
        const val DEFAULT_CALIBRATION_ROUNDS = 15

        /** From this many rounds on the analysis can be started early; 8 rounds found rising curves far less often than 15 (docs/ASSESSMENT.md B3). */
        const val MIN_CALIBRATION_ROUNDS = 8
        private const val CALIBRATION_SEED_MIX = 1_000L

        /** Scenarios with a first movement per target; micro-adjust is slow, flicks and switching fast. */
        val CLICK_SCENARIOS = listOf(ScenarioKind.FLICK, ScenarioKind.SWITCHING, ScenarioKind.MICRO)
    }
}
