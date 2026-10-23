package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.RoundSession
import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.app.i18n.UiText
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.storage.SessionKind

/** The refine rounds as a [RoundSession]; stored as their own kind, which searches and calibrations ignore. */
class RefineSession(private val ctx: AppContext, private val run: RefineRun) : RoundSession {
    override val label: UiText get() = run.label
    override val runKey: Long get() = run.runKey
    override val sessionKind: SessionKind get() = SessionKind.ACCEL_REFINE

    override fun onRoundFinished(config: TrainerConfig, recording: RoundRecording) = run.recordRound(recording)

    override fun showNext() = ctx.showRefineStep(run)

    override fun stop() = ctx.showMenu()
}
