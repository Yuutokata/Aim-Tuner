package de.yuuto.aimtune.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import de.yuuto.aimtune.input.RawMouseSource
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Minimal HUD in the design's type (Inter / JetBrains Mono) and colors; the Scene2D skin replaces
 * it in M4. All layout is in framebuffer pixels (not window units) so it stays sharp on scaled
 * displays. The debug overlay exists to verify raw input during M2.
 */
class Hud(private val options: LaunchOptions) : Disposable {
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val camera = OrthographicCamera()
    private var fonts = Fonts(Gdx.graphics.backBufferHeight)
    private var fontsHeight = Gdx.graphics.backBufferHeight
    private var width = 0f
    private var height = 0f
    private var scale = 1f
    var showDebug = true

    fun resize() {
        width = Gdx.graphics.backBufferWidth.toFloat()
        height = Gdx.graphics.backBufferHeight.toFloat()
        scale = height / REFERENCE_HEIGHT
        camera.setToOrtho(false, width, height)
        // Rebuild the fonts only when the size changed noticeably; resizing a window fires many events.
        if (kotlin.math.abs(height - fontsHeight) / fontsHeight > FONT_REBUILD_THRESHOLD) {
            fonts.dispose()
            fonts = Fonts(height.roundToInt())
            fontsHeight = height.roundToInt()
        }
    }

    fun render(trainer: Trainer, source: RawMouseSource, fps: Int) {
        camera.update()
        if (trainer.phase == Phase.RUNNING) drawCrosshair()
        batch.projectionMatrix = camera.combined
        batch.begin()
        when (trainer.phase) {
            Phase.READY -> drawReady()
            Phase.RUNNING -> drawRunning(trainer)
            Phase.FINISHED -> drawFinished(trainer)
        }
        if (showDebug) drawDebug(trainer, source, fps)
        batch.end()
    }

    private fun drawCrosshair() {
        val cx = width / 2
        val cy = height / 2
        val gap = CROSSHAIR_GAP * scale
        val len = CROSSHAIR_LENGTH * scale
        val t = (CROSSHAIR_THICKNESS * scale).coerceAtLeast(1f)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Theme.text
        shapes.rect(cx - gap - len, cy - t / 2, len, t)
        shapes.rect(cx + gap, cy - t / 2, len, t)
        shapes.rect(cx - t / 2, cy - gap - len, t, len)
        shapes.rect(cx - t / 2, cy + gap, t, len)
        shapes.end()
    }

    private fun drawReady() {
        text(fonts.title, "AimTune", width / 2, height * 0.62f, Theme.accentSoft, Align.center)
        text(fonts.body, "Click to start a ${options.roundSeconds} s flick round", width / 2, height * 0.54f, Theme.text, Align.center)
        text(
            fonts.mono,
            "%.1f cm/360°  ·  %.0f DPI  ·  FOV %.0f° %s".format(Locale.ROOT, options.cm360, options.dpi, options.fov.degrees, options.fov.type.name.lowercase()),
            width / 2, height * 0.49f, Theme.textMuted, Align.center,
        )
        text(fonts.body, "Esc quits  ·  F3 toggles debug", width / 2, height * 0.44f, Theme.textMuted, Align.center)
    }

    private fun drawRunning(trainer: Trainer) {
        val margin = MARGIN * scale
        val seconds = (trainer.timeLeftNanos() / 1_000_000_000L).toInt()
        text(fonts.monoLarge, "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60), width / 2, height - margin, Theme.text, Align.center)
        text(fonts.label, "FLICK", margin, height - margin, Theme.textMuted, Align.left)
        text(fonts.mono, "Hits ${trainer.hits}   Misses ${trainer.misses}", width - margin, height - margin, Theme.text, Align.right)
        text(fonts.body, "Esc aborts the round", margin, margin + fonts.body.lineHeight, Theme.textMuted, Align.left)
    }

    private fun drawFinished(trainer: Trainer) {
        val s = trainer.summary ?: return
        val ttk = s.meanTimeToHitNanos?.let { "%.0f ms".format(Locale.ROOT, it / 1e6) } ?: "-"
        text(fonts.title, "Round complete", width / 2, height * 0.62f, Theme.accentSoft, Align.center)
        text(fonts.mono, "Hits ${s.hits} / ${s.shots}   Accuracy %.0f %%".format(Locale.ROOT, s.accuracy * 100), width / 2, height * 0.54f, Theme.text, Align.center)
        text(fonts.mono, "Avg time to hit $ttk", width / 2, height * 0.49f, Theme.text, Align.center)
        text(fonts.body, "Click for the next round  ·  Esc quits", width / 2, height * 0.44f, Theme.textMuted, Align.center)
    }

    private fun drawDebug(trainer: Trainer, source: RawMouseSource, fps: Int) {
        val last = trainer.lastEvent
        val lines = listOf(
            "source: ${source.name}",
            "events: ${source.eventsReceived}  rate: %.0f/s".format(Locale.ROOT, trainer.rate.ratePerSecond(trainer.nowNanos())),
            "last: ${last?.let { "dx=${it.dx} dy=${it.dy}" } ?: "-"}",
            "yaw %.2f  pitch %.2f".format(Locale.ROOT, trainer.view.yawDegrees, trainer.view.pitchDegrees),
            "deg/count %.5f  seed ${options.seed}  fps $fps".format(Locale.ROOT, trainer.degreesPerCountValue),
        )
        val margin = MARGIN * scale
        var y = height - margin - fonts.monoLarge.lineHeight
        lines.forEach {
            text(fonts.mono, it, margin, y, Theme.textMuted, Align.left)
            y -= fonts.mono.lineHeight
        }
    }

    /** With a target width of 0, [align] is relative to [x] itself (centered or right-aligned at x). */
    private fun text(font: BitmapFont, s: String, x: Float, y: Float, color: Color, align: Int) {
        font.color = color
        font.draw(batch, s, x, y, 0f, align, false)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        fonts.dispose()
    }

    private companion object {
        const val REFERENCE_HEIGHT = 1080f
        const val FONT_REBUILD_THRESHOLD = 0.08f
        const val MARGIN = 72f
        const val CROSSHAIR_GAP = 6f
        const val CROSSHAIR_LENGTH = 12f
        const val CROSSHAIR_THICKNESS = 2f
    }
}
