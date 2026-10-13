package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AccelBaseline
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.accel.AccelRun
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
class AccelIntroScreen(ctx: AppContext) : UiScreen(ctx) {
    private val baseline: AccelBaseline? = ctx.accelBaseline()
    private val pointerAcceleration = WindowsPointerSettings.readAcceleration()

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label("Acceleration", Styles.HEADING)).left().padBottom(24f).row()
        content.add(
            ui.label(
                "Some players aim better when the sensitivity depends on hand speed. AimTune looks for such a curve in " +
                    "${AccelRun.DEFAULT_CALIBRATION_ROUNDS} calibration rounds, then lets you test it blind against your plain sensitivity. " +
                    "The curve is only simulated here; if it is clearly better you can export a settings file for Raw Accel.",
                Styles.BODY_MUTED, wrap = true,
            ),
        ).left().width(TEXT_WIDTH).padBottom(40f).row()
        content.add(baselineCard()).left().width(CARD_WIDTH).padBottom(24f).row()
        content.add(rawAccelCheck()).left()

        val footer = Table()
        footer.add(ui.button("Back", Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        val start = ui.button("Start calibration", Styles.PRIMARY) { if (canStart()) ctx.startAccelRun() }
        start.isDisabled = !canStart()
        footer.addButton(start, minWidth = 280f)
        return ui.frame(null, content, footer)
    }

    override fun onEscape() = ctx.showMenu()

    override fun onEnter() {
        if (canStart()) ctx.startAccelRun()
    }

    private fun canStart() = baseline != null && ctx.rawAccelConfirmed

    private fun baselineCard(): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(if (baseline == null) Styles.CARD_CAUTION else Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps("Starting point")).left().row()
        if (baseline == null) {
            card.add(ui.label("No sensitivity yet", Styles.TITLE)).left().padTop(8f).row()
            card.add(ui.label("Play a blind test first, so there is a sensitivity to start from.", Styles.CAPTION_MUTED, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(4f)
        } else {
            card.add(ui.label("%.1f cm/360°".format(Locale.ROOT, baseline.cm360), Styles.NUMBER_MD)).left().padTop(8f).row()
            val note = "Result of ${baseline.rounds} blind rounds, confidence ${baseline.confidencePercent} %." +
                if (baseline.confidencePercent < LOW_CONFIDENCE) " That is low: a longer sensitivity test first gives a better start." else ""
            card.add(ui.label(note, Styles.CAPTION_MUTED, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(4f)
        }
        return card
    }

    private fun rawAccelCheck(): Table {
        val block = Table()
        val box = CheckBox("  I checked: Raw Accel is off or linear", ui.skin)
        box.isChecked = ctx.rawAccelConfirmed
        box.onChange {
            ctx.rawAccelConfirmed = box.isChecked
            rebuild()
        }
        block.add(box).left().row()
        val windows = if (pointerAcceleration?.enabled == true) "Windows pointer acceleration is on; turn it off, it would distort the calibration." else ""
        if (windows.isNotEmpty()) block.add(ui.label(windows, Styles.CAUTION, wrap = true)).left().width(TEXT_WIDTH).padTop(8f)
        return block
    }

    private companion object {
        const val LOW_CONFIDENCE = 50
    }
}
