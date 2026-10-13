package de.yuuto.aimtune.app

import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.storage.SessionKind

/**
 * A run of rounds the trainer screen plays on behalf of something else (the blind test, the
 * acceleration steps): it gives the HUD label, says how the rounds are stored, takes the finished
 * recording, and decides what is shown after a round. Without a session the trainer is the free one.
 */
interface RoundSession {
    /** Text for the HUD, such as "Round 4 / 10"; never the sensitivity or the condition of a test. */
    val label: String

    /** Rounds of one run are stored together; this is the key that groups them. */
    val runKey: Long

    val sessionKind: SessionKind

    /** Stored as a blind round, i.e. played without seeing the sensitivity. */
    val storeBlind: Boolean get() = true

    /** The finished round, handed over on the render thread. */
    fun onRoundFinished(config: TrainerConfig, recording: RoundRecording)

    /** Shows what comes after a finished round. */
    fun showNext()

    /** Esc between rounds. */
    fun stop()
}
