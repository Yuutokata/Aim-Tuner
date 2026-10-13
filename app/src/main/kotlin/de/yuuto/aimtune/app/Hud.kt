package de.yuuto.aimtune.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import de.yuuto.aimtune.app.ui.Tokens
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.input.RawMouseSource
import java.util.Locale

/**
 * The trainer's on-screen text: round timer, hits, crosshair and the round summary. Drawn in
 * design units (1080 high, like the Scene2D screens) with the shared [Fonts] and token colors.
 * The debug overlay exists to verify raw input. With a [roundLabel] the HUD is the blind one:
 * it shows "Round 4 / 10" and never the sensitivity (no debug overlay either, it prints deg/count).
 */
class Hud(private val config: TrainerConfig, private val tokens: Tokens, private val roundLabel: String? = null) : Disposable {
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val camera = OrthographicCamera()
    private var width = 0f
    private var height = tokens.screenHeight
    var showDebug = roundLabel == null

    private val text = tokens.text
    private val muted = tokens.neutral(500)
    private val highlight = tokens.accent(400)

    fun resize() {
        height = tokens.screenHeight
        width = height * Gdx.graphics.width / Gdx.graphics.height
        camera.setToOrtho(false, width, height)
    }

    fun render(trainer: Trainer, source: RawMouseSource, report: RoundReport?, fps: Int, fonts: Fonts) {
        camera.update()
        if (trainer.phase == Phase.RUNNING) drawCrosshair()
        batch.projectionMatrix = camera.combined
        batch.begin()
        when (trainer.phase) {
            Phase.READY -> drawReady(fonts)
            Phase.RUNNING -> drawRunning(trainer, fonts)
            Phase.FINISHED -> drawFinished(trainer, report, fonts)
        }
        if (showDebug) drawDebug(trainer, source, fps, fonts)
        batch.end()
    }

    private fun drawCrosshair() {
        val cx = width / 2
        val cy = height / 2
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = text
        shapes.rect(cx - CROSSHAIR_GAP - CROSSHAIR_LENGTH, cy - CROSSHAIR_THICKNESS / 2, CROSSHAIR_LENGTH, CROSSHAIR_THICKNESS)
        shapes.rect(cx + CROSSHAIR_GAP, cy - CROSSHAIR_THICKNESS / 2, CROSSHAIR_LENGTH, CROSSHAIR_THICKNESS)
        shapes.rect(cx - CROSSHAIR_THICKNESS / 2, cy - CROSSHAIR_GAP - CROSSHAIR_LENGTH, CROSSHAIR_THICKNESS, CROSSHAIR_LENGTH)
        shapes.rect(cx - CROSSHAIR_THICKNESS / 2, cy + CROSSHAIR_GAP, CROSSHAIR_THICKNESS, CROSSHAIR_LENGTH)
        shapes.end()
    }

    private fun drawReady(fonts: Fonts) {
        if (roundLabel != null) {
            drawBlindReady(roundLabel, fonts)
            return
        }
        draw(fonts.title, "${config.profile.name}  ·  ${config.roundSeconds} s ${config.scenario.displayName.lowercase()} round", width / 2, height * 0.62f, highlight, Align.center)
        draw(fonts.bodyLg, "Click to start", width / 2, height * 0.54f, text, Align.center)
        draw(
            fonts.mono,
            "%.1f cm/360°  ·  %.0f DPI  ·  FOV %.0f° %s".format(Locale.ROOT, config.cm360, config.dpi, config.profile.fov.degrees, config.profile.fov.type.name.lowercase()),
            width / 2, height * 0.49f, muted, Align.center,
        )
        draw(fonts.body, "Esc back to the menu  ·  F3 toggles debug", width / 2, height * 0.44f, muted, Align.center)
    }

    private fun drawBlindReady(label: String, fonts: Fonts) {
        draw(fonts.title, label, width / 2, height * 0.62f, highlight, Align.center)
        draw(fonts.bodyLg, "Click to start", width / 2, height * 0.54f, text, Align.center)
        draw(fonts.body, "${config.roundSeconds} s ${config.scenario.displayName.lowercase()} round  ·  your sensitivity stays hidden until the test ends", width / 2, height * 0.49f, muted, Align.center)
        draw(fonts.body, instruction(), width / 2, height * 0.44f, muted, Align.center)
        draw(fonts.body, "Esc stops the test", width / 2, height * 0.40f, muted, Align.center)
    }

    private fun instruction(): String = when (config.scenario) {
        ScenarioKind.FLICK -> "Hit each target as it appears."
        ScenarioKind.MICRO -> "Small targets appear close to each other. Click precisely."
        ScenarioKind.TRACKING -> "Keep the crosshair on the moving target. No clicking needed."
        ScenarioKind.SWITCHING -> "Several targets are visible. Hit them one after another."
    }

    private fun drawRunning(trainer: Trainer, fonts: Fonts) {
        val seconds = (trainer.timeLeftNanos() / 1_000_000_000L).toInt()
        draw(fonts.numberMd, "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60), width / 2, height - MARGIN, text, Align.center)
        draw(fonts.label, config.scenario.displayName.uppercase(), MARGIN, height - MARGIN, muted, Align.left)
        val shots = if (config.scenario.usesClicks) "Hits ${trainer.hits}   Misses ${trainer.misses}" else ""
        val right = roundLabel ?: shots
        draw(fonts.mono, right, width - MARGIN, height - MARGIN, text, Align.right)
        draw(fonts.body, "Esc aborts the round", MARGIN, MARGIN + fonts.body.lineHeight, muted, Align.left)
    }

    private fun drawFinished(trainer: Trainer, report: RoundReport?, fonts: Fonts) {
        val s = trainer.summary ?: return
        draw(fonts.title, "Round complete", width / 2, height * 0.66f, highlight, Align.center)
        if (config.scenario.usesClicks) {
            val ttk = s.meanTimeToHitNanos?.let { "%.0f ms".format(Locale.ROOT, it / 1e6) } ?: "-"
            draw(fonts.mono, "Hits ${s.hits} / ${s.shots}   Accuracy %.0f %%".format(Locale.ROOT, s.accuracy * 100), width / 2, height * 0.58f, text, Align.center)
            draw(fonts.mono, "Avg time to hit $ttk", width / 2, height * 0.54f, text, Align.center)
        }
        drawReport(report, height * 0.48f, fonts)
        draw(fonts.body, "Click for the next round  ·  Esc back to the menu", width / 2, height * 0.30f, muted, Align.center)
    }

    /** Analysis lines under the round summary: first-movement error, corrections, path efficiency, save state. */
    private fun drawReport(report: RoundReport?, top: Float, fonts: Fonts) {
        val lines = when (report) {
            null, RoundReport.Pending -> listOf("Analysing round …" to muted)
            is RoundReport.Failed -> listOf("Analysis failed: ${report.message}" to tokens.caution)
            is RoundReport.Done -> reportLines(report)
        }
        var y = top
        lines.forEach { (line, color) ->
            draw(fonts.mono, line, width / 2, y, color, Align.center)
            y -= fonts.mono.lineHeight
        }
    }

    private fun reportLines(report: RoundReport.Done): List<Pair<String, Color>> {
        val m = report.metrics
        val error = m.meanFirstError?.let {
            "First move %+.0f %% (%s)".format(Locale.ROOT, it * 100, if (it >= 0) "overshoot" else "undershoot")
        }
        return listOfNotNull(
            m.trackingOnTargetRatio?.let { "On target %.0f %%".format(Locale.ROOT, it * 100) to text },
            m.trackingMeanDistanceDegrees?.let { "Mean distance %.2f°".format(Locale.ROOT, it) to text },
            error?.let { it to text },
            m.meanCorrectiveSubmovements?.let { "Corrections %.1f per target".format(Locale.ROOT, it) to text },
            m.meanPathEfficiency?.let { "Path efficiency %.0f %%".format(Locale.ROOT, it * 100) to text },
            (if (report.saveError == null) "Saved" else "Not saved: ${report.saveError}") to
                (if (report.saveError == null) muted else tokens.caution),
        )
    }

    private fun drawDebug(trainer: Trainer, source: RawMouseSource, fps: Int, fonts: Fonts) {
        val last = trainer.lastEvent
        val lines = listOf(
            "source: ${source.name}",
            "events: ${source.eventsReceived}  rate: %.0f/s".format(Locale.ROOT, trainer.rate.ratePerSecond(trainer.nowNanos())),
            "last: ${last?.let { "dx=${it.dx} dy=${it.dy}" } ?: "-"}",
            "yaw %.2f  pitch %.2f".format(Locale.ROOT, trainer.view.yawDegrees, trainer.view.pitchDegrees),
            "deg/count %.5f  seed ${config.seed}  fps $fps".format(Locale.ROOT, trainer.degreesPerCountValue),
        )
        var y = height - MARGIN - fonts.numberMd.lineHeight
        lines.forEach {
            draw(fonts.mono, it, MARGIN, y, muted, Align.left)
            y -= fonts.mono.lineHeight
        }
    }

    /** With a target width of 0, [align] is relative to [x] itself (centered or right-aligned at x). */
    private fun draw(font: BitmapFont, s: String, x: Float, y: Float, color: Color, align: Int) {
        font.color = color
        font.draw(batch, s, x, y, 0f, align, false)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
    }

    private companion object {
        const val MARGIN = 72f
        const val CROSSHAIR_GAP = 6f
        const val CROSSHAIR_LENGTH = 12f
        const val CROSSHAIR_THICKNESS = 2f
    }
}
