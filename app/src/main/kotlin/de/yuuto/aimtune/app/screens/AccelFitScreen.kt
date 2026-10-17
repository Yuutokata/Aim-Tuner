package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.ui.ChartActor
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.accelChart
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.core.accel.AccelFit
import de.yuuto.aimtune.core.accel.AccelFitResult
import de.yuuto.aimtune.core.accel.AccelReason
import de.yuuto.aimtune.core.accel.ClassicCurve
import de.yuuto.aimtune.core.accel.GainPoint
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.accel.PowerCurve
import java.util.Locale

private const val CARD_WIDTH = 900f
private const val CARD_PADDING = 32f
private const val CHART_HEIGHT = 300f

/** What the screens that show the result of a calibration analysis have in common: the curve card and the reason text. */
abstract class AccelFitScreen(ctx: AppContext) : UiScreen(ctx) {
    /** The fitted curve with its parameters and the chart of the wanted gain per speed next to it. */
    protected fun curveCard(fit: AccelFit, points: List<GainPoint>): Table {
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
        card.add(ChartActor(ui, accelChart(ui.tokens, points, fit))).left().width(CARD_WIDTH - 2 * CARD_PADDING).height(CHART_HEIGHT).padTop(8f).row()
        return card
    }

    protected fun reasonText(result: AccelFitResult): String = when (result.reasonCode) {
        AccelReason.NOT_ENOUGH_MOVEMENTS -> t("accel.reason.not_enough", result.reasonValue)
        AccelReason.NARROW_SPEED_RANGE -> t("accel.reason.narrow_speed")
        AccelReason.NO_FIT -> t("accel.reason.no_fit")
        AccelReason.NOT_BETTER_THAN_NONE -> t("accel.reason.not_better")
        AccelReason.SMALL_CHANGE -> t("accel.reason.small_change", result.reasonValue)
        AccelReason.FITS -> t("accel.reason.fits", result.recommended?.family.orEmpty())
    }

    private fun number(format: String, value: Double) = format.format(Locale.ROOT, value)

    private fun parameters(fit: AccelFit): String = when (val curve = fit.curve) {
        is ClassicCurve -> t("accel.analysis.params_classic", number("%.4f", curve.acceleration), number("%.2f", curve.exponent), number("%.2f", curve.cap))
        is NaturalCurve -> t("accel.analysis.params_natural", number("%.3f", curve.decayRate), number("%.2f", curve.limit))
        is PowerCurve -> t("accel.analysis.params_power", number("%.4f", curve.scale), number("%.2f", curve.exponent))
        else -> ""
    }
}
