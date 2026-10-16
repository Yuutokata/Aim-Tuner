package de.yuuto.aimtune.app.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import de.yuuto.aimtune.core.scenario.ShotResult
import java.nio.file.Files

/** Plays the hit sound with a pitch that follows the streak; without an audio device nothing plays and the app goes on. */
class HitSounds : Disposable {
    private val log = System.getLogger("aimtune.audio")
    private val combo = HitCombo()
    private val sound: Sound? = try {
        val file = Files.createTempFile("aimtune-hit", ".wav")
        try {
            Files.write(file, FlashSoundSynth.toWav(HitSoundSynth.samples()))
            Gdx.audio.newSound(Gdx.files.absolute(file.toString()))
        } finally {
            runCatching { Files.deleteIfExists(file) }
        }
    } catch (e: Exception) {
        log.log(System.Logger.Level.WARNING, "hit sound is not available: ${e.message}")
        null
    }

    fun onShot(result: ShotResult) {
        combo.onShot(result)?.let { pitch -> sound?.play(VOLUME, pitch, 0f) }
    }

    /** A new round starts a new streak. */
    fun reset() = combo.reset()

    override fun dispose() {
        sound?.dispose()
    }

    private companion object {
        const val VOLUME = 0.7f
    }
}
