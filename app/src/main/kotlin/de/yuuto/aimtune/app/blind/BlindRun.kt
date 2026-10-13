package de.yuuto.aimtune.app.blind

import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.scenario.ScenarioSchedule
import de.yuuto.aimtune.core.score.MetricWeights

/**
 * A blind test run in progress: the optimizer-backed [test], the key that groups this run's rounds
 * into one stored session, and the settings every round shares. [nextRoundConfig] gives the trainer
 * config of the next round: the scenario comes from the [schedule], the sensitivity from the
 * optimizer and stays hidden from the user.
 */
class BlindRun(
    val test: BlindTest,
    val runKey: Long,
    private val baseConfig: TrainerConfig,
    private var nextSeed: Long,
    private val schedule: ScenarioSchedule = ScenarioSchedule(setOf(ScenarioKind.FLICK), MetricWeights.DEFAULT),
) {
    /** Current sensitivity setting of the user (shown after the test, as "now"). */
    val currentCm360: Double get() = baseConfig.cm360

    val profile get() = baseConfig.profile

    val dpi: Double get() = baseConfig.dpi

    /** Config of the next round; asks the optimizer, so it may wait for the analysis of the last round. */
    fun nextRoundConfig(): TrainerConfig =
        baseConfig.copy(cm360 = test.nextCm360(), seed = nextSeed++, scenario = schedule.kindFor(test.roundsPlayed))

    /** "Round 4 / 10", or just "Round 12" once the planned rounds are used up. */
    fun nextRoundLabel(): String {
        val number = test.roundsPlayed + 1
        return if (number <= test.plannedRounds) "Round $number / ${test.plannedRounds}" else "Round $number"
    }

    fun completedRoundLabel(): String {
        val number = test.roundsPlayed
        return if (number <= test.plannedRounds) "Round $number / ${test.plannedRounds}" else "Round $number"
    }
}
