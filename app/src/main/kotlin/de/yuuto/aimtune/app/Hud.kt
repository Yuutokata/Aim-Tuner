package de.yuuto.aimtune.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import java.util.Locale

/**
 * Minimal HUD with the default font; the Scene2D skin and Inter/JetBrains Mono fonts from the
 * design tokens replace it in M4. The debug overlay exists to verify raw input during M2.
 */
class Hud(private val options: LaunchOptions, private val sourceName: String) : Disposable {
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val font = BitmapFont()
    private val camera = OrthographicCamera()
    var showDebug = true

    fun resize(width: Int, height: Int) {
        camera.setToOrtho(false, width.toFloat(), height.toFloat())
        font.data.setScale((height / REFERENCE_HEIGHT).coerceAtLeast(1f))
    }

    fun render(trainer: Trainer, source: de.yuuto.aimtune.input.RawMouseSource, fps: Int) {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        camera.update()
        if (trainer.phase == Phase.RUNNING) drawCrosshair(w, h)
        batch.projectionMatrix = camera.combined
        batch.begin()
        when (trainer.phase) {
            Phase.READY -> drawReady(w, h)
            Phase.RUNNING -> drawRunning(trainer, w, h)
            Phase.FINISHED -> drawFinished(trainer, w, h)
        }
        if (showDebug) drawDebug(trainer, source, fps, h)
        batch.end()
    }

    private fun drawCrosshair(w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val gap = CROSSHAIR_GAP * (h / REFERENCE_HEIGHT)
        val len = CROSSHAIR_LENGTH * (h / REFERENCE_HEIGHT)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Theme.text
        val t = (h / REFERENCE_HEIGHT).coerceAtLeast(1f)
        shapes.rect(cx - gap - len, cy - t / 2, len, t)
        shapes.rect(cx + gap, cy - t / 2, len, t)
        shapes.rect(cx - t / 2, cy - gap - len, t, len)
        shapes.rect(cx - t / 2, cy + gap, t, len)
        shapes.end()
    }

    private fun drawReady(w: Float, h: Float) {
        text("AimTune", w / 2, h * 0.62f, Theme.accentSoft, centered = true, scale = 2f)
        text("Click to start a ${options.roundSeconds} s flick round", w / 2, h * 0.52f, Theme.text, centered = true)
        text(
            "%.1f cm/360  |  %.0f DPI  |  FOV %.0f %s".format(Locale.ROOT, options.cm360, options.dpi, options.fov.degrees, options.fov.type.name.lowercase()),
            w / 2, h * 0.46f, Theme.textMuted, centered = true,
        )
        text("Esc quits  |  F3 toggles debug", w / 2, h * 0.40f, Theme.textMuted, centered = true)
    }

    private fun drawRunning(trainer: Trainer, w: Float, h: Float) {
        val seconds = (trainer.timeLeftNanos() / 1_000_000_000L).toInt()
        text("%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60), w / 2, h - MARGIN, Theme.text, centered = true, scale = 1.6f)
        text("FLICK", MARGIN, h - MARGIN, Theme.textMuted)
        text("Hits ${trainer.hits}   Misses ${trainer.misses}", w - MARGIN, h - MARGIN, Theme.text, align = Align.right)
        text("Esc aborts the round", MARGIN, MARGIN + font.lineHeight, Theme.textMuted)
    }

    private fun drawFinished(trainer: Trainer, w: Float, h: Float) {
        val s = trainer.summary ?: return
        text("Round complete", w / 2, h * 0.62f, Theme.accentSoft, centered = true, scale = 2f)
        text("Hits ${s.hits} / ${s.shots}   Accuracy %.0f %%".format(Locale.ROOT, s.accuracy * 100), w / 2, h * 0.52f, Theme.text, centered = true)
        val ttk = s.meanTimeToHitNanos?.let { "%.0f ms".format(Locale.ROOT, it / 1e6) } ?: "-"
        text("Avg time to hit $ttk", w / 2, h * 0.46f, Theme.text, centered = true)
        text("Click for the next round  |  Esc quits", w / 2, h * 0.40f, Theme.textMuted, centered = true)
    }

    private fun drawDebug(trainer: Trainer, source: de.yuuto.aimtune.input.RawMouseSource, fps: Int, h: Float) {
        val last = trainer.lastEvent
        val lines = listOf(
            "source: ${source.name}",
            "events: ${source.eventsReceived}  rate: %.0f/s".format(Locale.ROOT, trainer.rate.ratePerSecond(trainer.nowNanos())),
            "last: ${last?.let { "dx=${it.dx} dy=${it.dy}" } ?: "-"}",
            "yaw %.2f  pitch %.2f".format(Locale.ROOT, trainer.view.yawDegrees, trainer.view.pitchDegrees),
            "deg/count %.5f  seed ${options.seed}  fps $fps".format(Locale.ROOT, trainer.degreesPerCountValue),
        )
        var y = h - MARGIN - font.lineHeight * 2
        lines.forEach {
            text(it, MARGIN, y, Theme.textMuted)
            y -= font.lineHeight
        }
    }

    private fun text(s: String, x: Float, y: Float, color: com.badlogic.gdx.graphics.Color, centered: Boolean = false, align: Int = Align.left, scale: Float = 1f) {
        val base = font.data.scaleX
        font.data.setScale(base * scale)
        font.color = color
        // A target width of 0 aligns the text relative to x itself (centered or right-aligned at x).
        font.draw(batch, s, x, y, 0f, if (centered) Align.center else align, false)
        font.data.setScale(base)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }

    private companion object {
        const val REFERENCE_HEIGHT = 540f
        const val MARGIN = 24f
        const val CROSSHAIR_GAP = 4f
        const val CROSSHAIR_LENGTH = 8f
    }
}
