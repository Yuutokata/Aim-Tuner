package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.RoundSession
import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.app.i18n.UiText
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.storage.SessionKind

/** The acceleration calibration and A/B rounds as a [RoundSession]; they are stored as their own kind of session. */
class AccelSession(private val ctx: AppContext, private val run: AccelRun) : RoundSession {
    override val label: UiText get() = run.label
    override val runKey: Long get() = run.runKey
    override val sessionKind = SessionKind.ACCEL_AB

    override fun onRoundFinished(config: TrainerConfig, recording: RoundRecording) = run.recordRound(recording)

    override fun showNext() = ctx.showAccelStep(run)

    override fun stop() = ctx.showMenu()
}
