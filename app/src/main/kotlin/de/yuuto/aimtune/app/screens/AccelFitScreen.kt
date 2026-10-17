package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.accel.LastCalibration
import de.yuuto.aimtune.app.accel.PriorCalibration
import de.yuuto.aimtune.app.accel.StrengthResult
import de.yuuto.aimtune.app.ui.ChartActor
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.accelChart
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.core.accel.AccelFit
import de.yuuto.aimtune.core.accel.AccelFitResult
import de.yuuto.aimtune.core.accel.AccelReason
import de.yuuto.aimtune.core.accel.ClassicCurve
import de.yuuto.aimtune.core.accel.CurveStrength
import de.yuuto.aimtune.core.accel.GainPoint
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.accel.PowerCurve
import de.yuuto.aimtune.storage.RawAccelExport
import java.nio.file.Path
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CARD_WIDTH = 900f
private const val CARD_PADDING = 32f
private const val CHART_HEIGHT = 300f

/** Widths of the two cards that sit side by side; together they stay narrow enough for windows that are not 16:9. */
internal const val SIDE_CARD_WIDTH = 780f
internal const val EXPORT_CARD_WIDTH = 600f

/**
 * What the screens that show a found curve have in common: the curve card, the reason text, the strength card of the
 * blind search and the Raw Accel card with its buttons (export, copy, blind test).
 */
abstract class AccelFitScreen(ctx: AppContext) : UiScreen(ctx) {
    private var exported: Result<Path>? = null

    /** The fitted curve with its parameters and the chart of the wanted gain per speed next to it. */
    protected fun curveCard(fit: AccelFit, points: List<GainPoint>, width: Float = CARD_WIDTH): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps(t("accel.analysis.curve_title", fit.family))).left().row()
        card.add(ui.label(parameters(fit), Styles.MONO, wrap = true)).left().width(width - 2 * CARD_PADDING).padTop(8f).row()
        card.add(
            ui.label(
                t("accel.analysis.curve_note", "%.2f".format(Locale.ROOT, fit.gainRange), "%.2f".format(Locale.ROOT, fit.sensitivityFactor)),
                Styles.CAPTION_MUTED, wrap = true,
            ),
        ).left().width(width - 2 * CARD_PADDING).padTop(12f).row()
        card.add(ui.caps(t("accel.analysis.chart_curve"))).left().padTop(24f).row()
        card.add(ChartActor(ui, accelChart(ui.tokens, points, fit))).left().width(width - 2 * CARD_PADDING).height(CHART_HEIGHT).padTop(8f).row()
        return card
    }

    /** What the blind strength search found: the best strength, its credible range and a warning when it is at the edge of the range. */
    protected fun searchCard(r: StrengthResult, width: Float = CARD_WIDTH): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps(t("accel.search.card_title"))).left().row()
        val percent = ((r.strength - 1.0) * 100.0).roundToInt()
        val text = when {
            !CurveStrength.isCurve(r.strength) -> t("accel.search.strength_plain")
            percent > 0 -> t("accel.search.strength_rising", percent)
            else -> t("accel.search.strength_falling", abs(percent))
        }
        card.add(ui.label(text, Styles.TITLE, wrap = true)).left().width(width - 2 * CARD_PADDING).padTop(8f).row()
        card.add(
            ui.label(t("accel.search.range", "%.2f".format(Locale.ROOT, r.low), "%.2f".format(Locale.ROOT, r.high), r.confidencePercent), Styles.CAPTION_MUTED, wrap = true),
        ).left().width(width - 2 * CARD_PADDING).padTop(12f).row()
        if (r.atEdge) {
            card.add(ui.label(t("accel.search.edge", "%.2f".format(Locale.ROOT, CurveStrength.MIN), "%.2f".format(Locale.ROOT, CurveStrength.MAX)), Styles.CAUTION, wrap = true))
                .left().width(width - 2 * CARD_PADDING).padTop(12f)
        }
        return card
    }

    /** The sentence under the heading of a search result. */
    protected fun searchExplanation(r: StrengthResult?, offered: Boolean): String {
        if (r == null) return t("accel.search.too_few")
        return when {
            offered -> t("accel.search.offer_note", r.rounds)
            r.low <= CurveStrength.NEUTRAL && r.high >= CurveStrength.NEUTRAL -> t("accel.search.reason_range")
            else -> t("accel.search.reason_plain")
        }
    }

    protected fun reasonText(result: AccelFitResult): String = when (result.reasonCode) {
        AccelReason.NOT_ENOUGH_MOVEMENTS -> t("accel.reason.not_enough", result.reasonValue)
        AccelReason.NARROW_SPEED_RANGE -> t("accel.reason.narrow_speed")
        AccelReason.NO_FIT -> t("accel.reason.no_fit")
        AccelReason.NOT_BETTER_THAN_NONE -> t("accel.reason.not_better")
        AccelReason.SMALL_CHANGE -> t("accel.reason.small_change", result.reasonValue)
        AccelReason.FITS -> t("accel.reason.fits", result.recommended?.family.orEmpty())
    }

    /**
     * The Raw Accel hints with [untestedKey] (the warning that the blind test has not confirmed this curve) and, once
     * exported, where the file went.
     */
    protected fun exportCard(untestedKey: String, width: Float = EXPORT_CARD_WIDTH): Table {
        val inner = width - 2 * CARD_PADDING
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps(t("accel.result.raw_accel_title"))).left().row()
        card.add(ui.label(t(untestedKey), Styles.CAUTION, wrap = true)).left().width(inner).padTop(8f).row()
        card.add(ui.label(t("accel.result.raw_accel_text"), Styles.CAPTION_MUTED, wrap = true)).left().width(inner).padTop(8f).row()
        if (!canTestBlind()) card.add(ui.label(t("accel.last.test_blocked"), Styles.CAPTION_MUTED, wrap = true)).left().width(inner).padTop(16f).row()
        exported?.let { result ->
            val text = result.fold({ t("accel.result.saved", it) }, { t("accel.result.save_failed", it.message.orEmpty()) })
            card.add(ui.label(text, if (result.isSuccess) Styles.OK else Styles.CAUTION, wrap = true)).left().width(inner).padTop(16f)
        }
        return card
    }

    /** Copy, export and blind-test buttons for [fit], found at [dpi] and [cm360]; they go into the footer after the Back button. */
    protected fun addCurveActions(footer: Table, fit: AccelFit, dpi: Double, cm360: Double) {
        footer.addButton(ui.button(t("accel.result.copy"), Styles.SECONDARY) { copySettings(fit, dpi) }, minWidth = 260f).padRight(16f)
        footer.addButton(ui.button(t("accel.result.export"), Styles.SECONDARY) { exportSettings(fit, dpi) }, minWidth = 300f).padRight(16f)
        val test = ui.button(t("accel.last.test"), Styles.PRIMARY) { if (canTestBlind()) ctx.startAccelTest(fit, LastCalibration(dpi, cm360, PriorCalibration.NONE)) }
        test.isDisabled = !canTestBlind()
        footer.addButton(test, minWidth = 300f)
    }

    /** The blind test compares with the plain mouse, so it needs the Raw Accel check of the intro and a mouse with report timestamps. */
    private fun canTestBlind() = ctx.rawAccelConfirmed && ctx.source.hasReportTimestamps

    private fun exportSettings(fit: AccelFit, dpi: Double) {
        exported = ctx.exportAccel(fit, dpi)
        rebuild()
    }

    private fun copySettings(fit: AccelFit, dpi: Double) {
        Gdx.app.clipboard.contents = RawAccelExport.settingsJson(fit.curve, dpi, fit.sensitivityFactor)
    }

    private fun number(format: String, value: Double) = format.format(Locale.ROOT, value)

    private fun parameters(fit: AccelFit): String = when (val curve = fit.curve) {
        is ClassicCurve -> t("accel.analysis.params_classic", number("%.4f", curve.acceleration), number("%.2f", curve.exponent), number("%.2f", curve.cap))
        is NaturalCurve -> t("accel.analysis.params_natural", number("%.3f", curve.decayRate), number("%.2f", curve.limit))
        is PowerCurve -> t("accel.analysis.params_power", number("%.4f", curve.scale), number("%.2f", curve.exponent))
        else -> ""
    }
}
