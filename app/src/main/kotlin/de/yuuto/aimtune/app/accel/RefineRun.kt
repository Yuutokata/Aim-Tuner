package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.app.i18n.UiText
import de.yuuto.aimtune.core.accel.RawAccelProfile
import de.yuuto.aimtune.core.accel.RefineEngine
import de.yuuto.aimtune.core.accel.RefinePhase
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.score.scoreInputs

/**
 * "Refine my Raw Accel curve" as the app runs it: the import has happened (the import screen is the IMPORT phase), this holds
 * the rest. [RefineEngine] is the logic (warm-up, stages, test), this class the glue to [TrainerConfig] and the HUD label.
 * [baseConfig] carries the player's game sensitivity in `cm360` and the DPI of the settings, which equals the file's. Rounds
 * are played at the profile's own cm/360 (the game sensitivity divided by output DPI / device DPI) with its curve; nothing
 * here reveals the factor of a search round or the arm of a test round.
 */
class RefineRun(
    private val baseConfig: TrainerConfig,
    weights: MetricWeights,
    val runKey: Long,
    seed: Long,
    /** The file the profile came from; the export patches a copy of it. */
    val source: RefineSource,
    val profile: RawAccelProfile,
    flash: Boolean,
    stageRounds: Int = 12,
) {
    val engine = RefineEngine(profile, baseConfig.cm360, weights, seed, flash, stageRounds = stageRounds)

    val phase: RefinePhase get() = engine.phase

    val dpi: Double get() = baseConfig.dpi

    val exportName: String get() = "${profile.name} (AimTune)"

    val label: UiText
        get() = when (engine.phase) {
            RefinePhase.WARMUP -> UiText("refine.label.warmup", engine.warmupPlayed + 1, engine.warmupRounds)
            RefinePhase.SEARCH -> UiText("refine.label.search", engine.stageNumber + 1, engine.stages.size, engine.searchPlayed + 1, engine.searchPlanned)
            RefinePhase.TEST -> UiText("refine.label.test", engine.testRoundsDone + 1)
            else -> UiText("")
        }

    fun nextRoundConfig(): TrainerConfig {
        val round = engine.nextRound()
        return baseConfig.copy(cm360 = round.cm360, scenario = round.scenario, seed = round.seed, accel = round.curve)
    }

    fun recordRound(recording: RoundRecording) = engine.record(computeRoundMetrics(recording).scoreInputs())

    /** The fields the refined profile changes against the imported one. */
    fun changes(): List<FieldChange> = RefineDiff.changes(profile, engine.candidate)
}
