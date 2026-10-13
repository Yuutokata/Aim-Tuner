package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.pretest.CheckLevel
import de.yuuto.aimtune.app.pretest.CheckRow
import de.yuuto.aimtune.app.pretest.PreTestChecks
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.app.ui.onChange
import de.yuuto.aimtune.input.windows.WindowsPointerSettings
import de.yuuto.aimtune.input.windows.WindowsRawInputSource

private const val DESCRIPTION_WIDTH = 900f
private const val ROW_WIDTH = 1100f
private const val ROW_PADDING = 28f
private const val ROW_GAP = 12f
private const val DETAIL_WIDTH = 820f
private const val FOOTER_BUTTON_HEIGHT = 64f

/**
 * Tells the user what could distort the measurement: Windows pointer acceleration (read from the
 * system), Raw Accel (confirmed by hand, it cannot be detected) and the mouse input source. It
 * only informs; the test can always be started.
 */
class PreTestScreen(ctx: AppContext) : UiScreen(ctx) {
    private var acceleration = WindowsPointerSettings.readAcceleration()

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label("Before you start", Styles.HEADING)).left().padBottom(24f).row()
        content.add(
            ui.label("Pointer acceleration changes sensitivity with hand speed and would distort the result. Turn it off for the test.", Styles.BODY_MUTED, wrap = true),
        ).left().width(DESCRIPTION_WIDTH).padBottom(48f).row()
        val rows = PreTestChecks.rows(acceleration, ctx.rawAccelConfirmed, ctx.source.name, ctx.source is WindowsRawInputSource)
        rows.forEach { content.add(checkRow(it)).left().width(ROW_WIDTH).padBottom(ROW_GAP).row() }

        val footer = Table()
        footer.add(ui.button("Back", Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        footer.addButton(ui.button("Re-check", Styles.SECONDARY) { recheck() }, minWidth = 180f).padRight(16f)
        footer.addButton(ui.button("Start test", Styles.PRIMARY) { ctx.startBlindTest() }, minWidth = 220f)
        return ui.frame(null, content, footer)
    }

    override fun onEscape() = ctx.showMenu()

    override fun onEnter() = ctx.startBlindTest()

    private fun recheck() {
        acceleration = WindowsPointerSettings.readAcceleration()
        rebuild()
    }

    private fun checkRow(row: CheckRow): Table {
        val table = Table()
        val attention = row.level == CheckLevel.ATTENTION
        table.background(ui.skin.getDrawable(if (attention) Styles.CARD_CAUTION else Styles.CARD))
        table.pad(ROW_PADDING)
        val text = Table()
        text.add(ui.label(row.title, Styles.BODY_LG)).left().row()
        text.add(ui.label(row.detail, Styles.CAPTION_MUTED, wrap = true)).left().width(DETAIL_WIDTH).padTop(4f).row()
        if (row.id == PreTestChecks.RAW_ACCEL) text.add(rawAccelCheckBox()).left().padTop(12f)
        table.add(text).left().expandX()
        val stateStyle = when (row.level) {
            CheckLevel.OK -> Styles.OK
            CheckLevel.ATTENTION -> Styles.CAUTION
            CheckLevel.UNKNOWN -> Styles.MONO_MUTED
        }
        table.add(ui.label(row.state, stateStyle)).right().top()
        return table
    }

    private fun rawAccelCheckBox(): CheckBox {
        val box = CheckBox("  I checked: Raw Accel is off or linear", ui.skin)
        box.isChecked = ctx.rawAccelConfirmed
        box.onChange {
            ctx.rawAccelConfirmed = box.isChecked
            rebuild()
        }
        return box
    }
}
