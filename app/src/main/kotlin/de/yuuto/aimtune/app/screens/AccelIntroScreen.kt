package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AccelBaseline
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.accel.AccelRun
import de.yuuto.aimtune.app.accel.StrengthSearch
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.app.ui.onChange
import de.yuuto.aimtune.input.windows.WindowsPointerSettings
import java.util.Locale

private const val TEXT_WIDTH = 1000f
private const val CARD_WIDTH = 760f
private const val CARD_PADDING = 32f
private const val FOOTER_BUTTON_HEIGHT = 64f

/**
 * Entry of the acceleration steps: what they are, the sensitivity they start from (the result of
 * the blind test), and the check that Raw Accel is off, since the calibration needs a plain mouse.
 */
class AccelIntroScreen(ctx: AppContext, private val baseline: AccelBaseline? = ctx.accelBaseline()) : UiScreen(ctx) {
    private val pointerAcceleration = WindowsPointerSettings.readAcceleration()

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label(t("accel.title"), Styles.HEADING)).left().padBottom(24f).row()
        content.add(
            ui.label(t("accel.intro", StrengthSearch.DEFAULT_ROUNDS, AccelRun.DEFAULT_CALIBRATION_ROUNDS), Styles.BODY_MUTED, wrap = true),
        ).left().width(TEXT_WIDTH).padBottom(16f).row()
        content.add(ui.label(t("accel.experimental"), Styles.CAUTION, wrap = true)).left().width(TEXT_WIDTH).padBottom(40f).row()
        content.add(baselineCard()).left().width(CARD_WIDTH).padBottom(24f).row()
        content.add(rawAccelCheck()).left().row()
        if (baseline != null && baseline.searchRounds > 0) content.add(priorSearchCheck(baseline.searchRounds)).left().padTop(24f).row()
        if (!ctx.source.hasReportTimestamps) {
            content.add(ui.label(t("accel.needs_raw_input", ctx.source.name), Styles.CAUTION, wrap = true)).left().width(TEXT_WIDTH).padTop(24f)
        }

        val footer = Table()
        footer.add(ui.button(t("common.back"), Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add(ui.button(t("accel.last"), Styles.SECONDARY) { ctx.showLastAccelAnalysis() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(16f)
        footer.add(ui.button(t("accel.last_search"), Styles.SECONDARY) { ctx.showLastSearch() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(16f)
        footer.add().expandX()
        val calibrate = ui.button(t("accel.start_calibration"), Styles.SECONDARY) { if (canStart()) ctx.startAccelRun() }
        calibrate.isDisabled = !canStart()
        footer.addButton(calibrate, minWidth = 340f).padRight(16f)
        val search = ui.button(t("accel.start_search"), Styles.PRIMARY) { if (canStart()) ctx.startAccelRun(search = true) }
        search.isDisabled = !canStart()
        footer.addButton(search, minWidth = 340f)
        return ui.frame(null, content, footer)
    }

    override fun onEscape() = ctx.showMenu()

    override fun onEnter() {
        if (canStart()) ctx.startAccelRun(search = true)
    }

    private fun canStart() = baseline != null && ctx.rawAccelConfirmed && ctx.source.hasReportTimestamps

    private fun baselineCard(): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(if (baseline == null) Styles.CARD_CAUTION else Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps(t("accel.baseline_title"))).left().row()
        if (baseline == null) {
            card.add(ui.label(t("accel.baseline_none"), Styles.TITLE)).left().padTop(8f).row()
            card.add(ui.label(t("accel.baseline_none_help"), Styles.CAPTION_MUTED, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(4f)
        } else {
            card.add(ui.label("%.1f cm/360°".format(Locale.ROOT, baseline.cm360), Styles.NUMBER_MD)).left().padTop(8f).row()
            val note = t("accel.baseline_note", baseline.rounds, baseline.confidencePercent) +
                if (baseline.confidencePercent < LOW_CONFIDENCE) " " + t("accel.baseline_low") else ""
            card.add(ui.label(note, Styles.CAPTION_MUTED, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(4f)
        }
        return card
    }

    private fun rawAccelCheck(): Table {
        val block = Table()
        val box = CheckBox("  " + t("pretest.raw_accel_checked"), ui.skin)
        box.isChecked = ctx.rawAccelConfirmed
        box.onChange {
            ctx.rawAccelConfirmed = box.isChecked
            rebuild()
        }
        block.add(box).left().row()
        val windows = if (pointerAcceleration?.enabled == true) t("accel.windows_on") else ""
        if (windows.isNotEmpty()) block.add(ui.label(windows, Styles.CAUTION, wrap = true)).left().width(TEXT_WIDTH).padTop(8f)
        return block
    }

    /** Lets the player start a strength search that ignores the stored ones (they are kept, not deleted). */
    private fun priorSearchCheck(rounds: Int): Table {
        val block = Table()
        val box = CheckBox("  " + t("accel.use_prior", rounds), ui.skin)
        box.isChecked = ctx.usePriorSearch
        box.onChange { ctx.usePriorSearch = box.isChecked }
        block.add(box).left().row()
        block.add(ui.label(t("accel.use_prior_help"), Styles.CAPTION_MUTED, wrap = true)).left().width(TEXT_WIDTH).padTop(8f)
        return block
    }

    private companion object {
        const val LOW_CONFIDENCE = 50
    }
}
