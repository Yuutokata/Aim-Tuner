package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.accel.AccelRun
import de.yuuto.aimtune.app.ui.ChartActor
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.accelChart
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.core.accel.AccelFit
import de.yuuto.aimtune.core.accel.AccelFitResult
import de.yuuto.aimtune.core.accel.AccelReason
import de.yuuto.aimtune.core.accel.ClassicCurve
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.accel.PowerCurve
import java.util.Locale

private const val TEXT_WIDTH = 1000f
private const val CARD_WIDTH = 900f
private const val CARD_PADDING = 32f
private const val FOOTER_BUTTON_HEIGHT = 64f
private const val CHART_HEIGHT = 300f

/**
 * What the calibration found: either a curve that fits the way the first movements miss, offered for
 * a blind test, or the reason there is nothing to offer. The chart shows the wanted gain per speed next to the curve.
 */
class AccelAnalysisScreen(ctx: AppContext, private val run: AccelRun) : UiScreen(ctx) {
    private val candidate: AccelFit? = run.candidate

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label(if (candidate == null) t("accel.analysis.no_curve") else t("accel.analysis.curve_found"), Styles.HEADING)).left().padBottom(32f).row()
        val fit = checkNotNull(run.fit)
        content.add(ui.label(reasonText(fit), Styles.BODY_MUTED, wrap = true)).left().width(TEXT_WIDTH).padBottom(16f).row()
        content.add(ui.label(t("accel.experimental"), Styles.CAPTION_MUTED, wrap = true)).left().width(TEXT_WIDTH).padBottom(32f).row()
        if (candidate != null) content.add(curveCard(candidate)).left().width(CARD_WIDTH).padBottom(24f).row()
        if (candidate == null && fit.points.size >= 2) {
            content.add(ui.caps(t("accel.analysis.chart_plain"))).left().padBottom(8f).row()
            content.add(ChartActor(ui, accelChart(ui.tokens, fit.points, null))).left().width(CARD_WIDTH).height(CHART_HEIGHT).padBottom(16f).row()
        }
        if (candidate == null) {
            content.add(
                ui.label(t("accel.analysis.plain_supported") + " " + t("accel.analysis.trial_hint"), Styles.CAPTION_MUTED, wrap = true),
            ).left().width(TEXT_WIDTH)
        }

        val footer = Table()
        footer.add(ui.button(if (candidate == null) t("common.back") else t("accel.analysis.not_now"), Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        if (candidate != null) {
            footer.addButton(ui.button(t("accel.analysis.test_blind"), Styles.PRIMARY) { start() }, minWidth = 260f)
        } else if (fit.points.size >= 2) {
            footer.addButton(ui.button(t("accel.analysis.try_falling"), Styles.SECONDARY) { startTrial(rising = false) }, minWidth = 260f).padRight(16f)
            footer.addButton(ui.button(t("accel.analysis.try_rising"), Styles.SECONDARY) { startTrial(rising = true) }, minWidth = 260f)
        }
        return ui.frame(null, content, footer)
    }

    override fun onEscape() = ctx.showMenu()

    override fun onEnter() {
        if (candidate != null) start()
    }

    private fun startTrial(rising: Boolean) {
        run.startTrial(rising)
        ctx.showAccelStep(run)
    }

    private fun start() {
        run.startTest()
        ctx.showAccelStep(run)
    }

    private fun curveCard(fit: AccelFit): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps(t("accel.analysis.curve_title", fit.family))).left().row()
        card.add(ui.label(parameters(fit), Styles.MONO, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(8f).row()
        card.add(
            ui.label(
                t("accel.analysis.curve_note", "%.2f".format(Locale.ROOT, fit.gainRange), "%.2f".format(Locale.ROOT, fit.sensitivityFactor)),
                Styles.CAPTION_MUTED, wrap = true,
            ),
        ).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(12f).row()
        card.add(ui.caps(t("accel.analysis.chart_curve"))).left().padTop(24f).row()
        card.add(ChartActor(ui, accelChart(ui.tokens, checkNotNull(run.fit).points, fit))).left().width(CARD_WIDTH - 2 * CARD_PADDING).height(CHART_HEIGHT).padTop(8f).row()
        return card
    }

    private fun number(format: String, value: Double) = format.format(Locale.ROOT, value)

    private fun reasonText(result: AccelFitResult): String = when (result.reasonCode) {
        AccelReason.NOT_ENOUGH_MOVEMENTS -> t("accel.reason.not_enough", result.reasonValue)
        AccelReason.NARROW_SPEED_RANGE -> t("accel.reason.narrow_speed")
        AccelReason.NO_FIT -> t("accel.reason.no_fit")
        AccelReason.NOT_BETTER_THAN_NONE -> t("accel.reason.not_better")
        AccelReason.SMALL_CHANGE -> t("accel.reason.small_change", result.reasonValue)
        AccelReason.FITS -> t("accel.reason.fits", result.recommended?.family.orEmpty())
    }

    private fun parameters(fit: AccelFit): String = when (val curve = fit.curve) {
        is ClassicCurve -> t("accel.analysis.params_classic", number("%.4f", curve.acceleration), number("%.2f", curve.exponent), number("%.2f", curve.cap))
        is NaturalCurve -> t("accel.analysis.params_natural", number("%.3f", curve.decayRate), number("%.2f", curve.limit))
        is PowerCurve -> t("accel.analysis.params_power", number("%.4f", curve.scale), number("%.2f", curve.exponent))
        else -> ""
    }
}
