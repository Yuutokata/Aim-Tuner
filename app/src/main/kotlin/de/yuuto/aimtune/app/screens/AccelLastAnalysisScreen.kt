package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.accel.LastCalibration
import de.yuuto.aimtune.app.ui.ChartActor
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.accelChart
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.core.accel.AccelAnalysis
import de.yuuto.aimtune.core.accel.fitAccel
import de.yuuto.aimtune.storage.RawAccelExport
import java.nio.file.Path
import java.util.Locale

private const val TEXT_WIDTH = 1000f
private const val CARD_WIDTH = 900f
private const val EXPORT_WIDTH = 740f
private const val CARD_PADDING = 32f
private const val CHART_HEIGHT = 300f
private const val FOOTER_BUTTON_HEIGHT = 64f

/**
 * The analysis of the stored calibration rounds, as it would come out now: the curve that fits (or why none does) and the
 * wanted gain per speed. It is computed again from the stored rounds, so it is available on any day without a new run.
 */
class AccelLastAnalysisScreen(ctx: AppContext, private val last: LastCalibration?) : AccelFitScreen(ctx) {
    private val analysis = last?.data?.takeIf { it.rounds > 0 }?.let { AccelAnalysis(it.samples) }
    private val fit = analysis?.let(::fitAccel)
    private var exported: Result<Path>? = null

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label(t("accel.last.title"), Styles.HEADING)).left().padBottom(32f).row()
        if (last == null || analysis == null || fit == null) {
            content.add(ui.label(t("accel.last.none"), Styles.BODY_MUTED, wrap = true)).left().width(TEXT_WIDTH)
        } else {
            val note = t("accel.last.note", last.data.rounds, analysis.used.size, "%.1f".format(Locale.ROOT, last.cm360))
            content.add(ui.label(note, Styles.CAPTION_MUTED, wrap = true)).left().width(TEXT_WIDTH).padBottom(16f).row()
            content.add(ui.label(reasonText(fit), Styles.BODY_MUTED, wrap = true)).left().width(TEXT_WIDTH).padBottom(24f).row()
            val recommended = fit.recommended
            if (recommended != null) {
                // The export card sits next to the curve, so the screen stays within the window.
                content.add(curveCard(recommended, fit.points)).left().top().width(CARD_WIDTH)
                content.add(exportCard()).left().top().width(EXPORT_WIDTH).padLeft(24f).row()
            } else if (fit.points.size >= 2) {
                content.add(ui.caps(t("accel.analysis.chart_plain"))).left().padBottom(8f).row()
                content.add(ChartActor(ui, accelChart(ui.tokens, fit.points, null))).left().width(CARD_WIDTH).height(CHART_HEIGHT).row()
            }
        }

        val footer = Table()
        footer.add(ui.button(t("common.back"), Styles.TEXT) { ctx.showAccel() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        if (fit?.recommended != null) {
            footer.addButton(ui.button(t("accel.result.copy"), Styles.SECONDARY) { copy() }, minWidth = 260f).padRight(16f)
            footer.addButton(ui.button(t("accel.result.export"), Styles.PRIMARY) { export() }, minWidth = 300f)
        }
        return ui.frame(null, content, footer)
    }

    /** The Raw Accel hints and, once exported, where the file went. Same texts as after the blind test, plus the note that this curve was not tested. */
    private fun exportCard(): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps(t("accel.result.raw_accel_title"))).left().row()
        card.add(ui.label(t("accel.last.untested"), Styles.CAUTION, wrap = true)).left().width(EXPORT_WIDTH - 2 * CARD_PADDING).padTop(8f).row()
        card.add(ui.label(t("accel.result.raw_accel_text"), Styles.CAPTION_MUTED, wrap = true)).left().width(EXPORT_WIDTH - 2 * CARD_PADDING).padTop(8f).row()
        exported?.let { result ->
            val text = result.fold({ t("accel.result.saved", it) }, { t("accel.result.save_failed", it.message.orEmpty()) })
            card.add(ui.label(text, if (result.isSuccess) Styles.OK else Styles.CAUTION, wrap = true)).left().width(EXPORT_WIDTH - 2 * CARD_PADDING).padTop(16f)
        }
        return card
    }

    private fun export() {
        exported = ctx.exportAccel(fit?.recommended, checkNotNull(last).dpi)
        rebuild()
    }

    private fun copy() {
        val curve = fit?.recommended ?: return
        Gdx.app.clipboard.contents = RawAccelExport.settingsJson(curve.curve, checkNotNull(last).dpi, curve.sensitivityFactor)
    }

    override fun onEscape() = ctx.showAccel()
}
