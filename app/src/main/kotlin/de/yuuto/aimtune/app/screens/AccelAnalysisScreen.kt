package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.accel.AccelRun
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.core.accel.AccelFit
import de.yuuto.aimtune.core.accel.ClassicCurve
import de.yuuto.aimtune.core.accel.GainPoint
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.accel.PowerCurve
import java.util.Locale

private const val TEXT_WIDTH = 1000f
private const val CARD_WIDTH = 900f
private const val CARD_PADDING = 32f
private const val FOOTER_BUTTON_HEIGHT = 64f

/**
 * What the calibration found: either a curve that fits the way the first movements miss, offered for
 * a blind test, or the reason there is nothing to offer. Numbers only; the chart comes with M8.
 */
class AccelAnalysisScreen(ctx: AppContext, private val run: AccelRun) : UiScreen(ctx) {
    private val candidate: AccelFit? = run.candidate

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label(if (candidate == null) "No curve to try" else "A curve fits how you aim", Styles.HEADING)).left().padBottom(32f).row()
        val fit = checkNotNull(run.fit)
        content.add(ui.label(fit.reason, Styles.BODY_MUTED, wrap = true)).left().width(TEXT_WIDTH).padBottom(32f).row()
        if (candidate != null) content.add(curveCard(candidate)).left().width(CARD_WIDTH).padBottom(24f).row()
        if (candidate == null) {
            content.add(
                ui.label("Your plain sensitivity is what the data supports. Nothing is exported.", Styles.CAPTION_MUTED, wrap = true),
            ).left().width(TEXT_WIDTH)
        }

        val footer = Table()
        footer.add(ui.button(if (candidate == null) "Back" else "Not now", Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        if (candidate != null) {
            footer.addButton(ui.button("Test it blind", Styles.PRIMARY) { start() }, minWidth = 260f)
        }
        return ui.frame(null, content, footer)
    }

    override fun onEscape() = ctx.showMenu()

    override fun onEnter() {
        if (candidate != null) start()
    }

    private fun start() {
        run.startTest()
        ctx.showAccelStep(run)
    }

    private fun curveCard(fit: AccelFit): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps("Curve · Raw Accel ${fit.family}")).left().row()
        card.add(ui.label(parameters(fit), Styles.MONO, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(8f).row()
        card.add(
            ui.label(
                ("The gain changes by a factor of %.2f across your speeds. Sensitivity factor %.2f: it is already part of the exported file, " +
                    "your in-game sensitivity stays as it is.").format(Locale.ROOT, fit.gainRange, fit.sensitivityFactor),
                Styles.CAPTION_MUTED, wrap = true,
            ),
        ).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(12f).row()
        card.add(ui.caps("Wanted gain and curve by speed")).left().padTop(24f).row()
        val points = checkNotNull(run.fit).points
        sample(points).forEach { point ->
            val line = "%5.1f in/s   wanted %.2f   curve %.2f".format(Locale.ROOT, point.speed, point.gain, fit.totalGain(point.speed) / geometricMean(points, fit))
            card.add(ui.label(line, Styles.MONO_MUTED)).left().padTop(4f).row()
        }
        return card
    }

    private fun parameters(fit: AccelFit): String = when (val curve = fit.curve) {
        is ClassicCurve -> "acceleration %.4f · exponent %.2f · cap %.2f".format(Locale.ROOT, curve.acceleration, curve.exponent, curve.cap)
        is NaturalCurve -> "decay rate %.3f · limit %.2f".format(Locale.ROOT, curve.decayRate, curve.limit)
        is PowerCurve -> "scale %.4f · exponent %.2f".format(Locale.ROOT, curve.scale, curve.exponent)
        else -> ""
    }

    /** Three to five bins spread over the measured speeds. */
    private fun sample(points: List<GainPoint>): List<GainPoint> =
        if (points.size <= MAX_LINES) points else List(MAX_LINES) { points[it * (points.size - 1) / (MAX_LINES - 1)] }

    /** The fit's total gain relative to its average over the bins, the same normalization as the wanted gain. */
    private fun geometricMean(points: List<GainPoint>, fit: AccelFit): Double =
        Math.exp(points.sumOf { Math.log(fit.totalGain(it.speed)) * it.count } / points.sumOf { it.count })

    private companion object {
        const val MAX_LINES = 5
    }
}
