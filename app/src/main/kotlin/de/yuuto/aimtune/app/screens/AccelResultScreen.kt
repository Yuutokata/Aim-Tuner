package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.Gdx
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
import de.yuuto.aimtune.core.accel.AbStatus
import de.yuuto.aimtune.core.accel.AbVerdict
import de.yuuto.aimtune.storage.RawAccelExport
import java.nio.file.Path
import java.util.Locale

private const val TEXT_WIDTH = 1000f
private const val CARD_WIDTH = 900f
private const val CARD_PADDING = 32f
private const val FOOTER_BUTTON_HEIGHT = 64f

/**
 * The verdict of the blind test. Only when the curve was measurably better is a Raw Accel file
 * offered; otherwise the plain sensitivity stays what AimTune recommends.
 */
class AccelResultScreen(ctx: AppContext, private val run: AccelRun) : UiScreen(ctx) {
    private val verdict: AbVerdict? = run.verdict
    private var exported: Result<Path>? = null

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label(heading(), Styles.HEADING)).left().padBottom(32f).row()
        content.add(ui.label(explanation(), Styles.BODY_MUTED, wrap = true)).left().width(TEXT_WIDTH).padBottom(32f).row()
        if (verdict?.status == AbStatus.ACCEL_BETTER) content.add(exportCard()).left().width(CARD_WIDTH).row()

        val footer = Table()
        footer.add(ui.button("Back to menu", Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        if (verdict?.status == AbStatus.ACCEL_BETTER) {
            footer.addButton(ui.button("Copy settings.json", Styles.SECONDARY) { copy() }, minWidth = 260f).padRight(16f)
            footer.addButton(ui.button("Export for Raw Accel", Styles.PRIMARY) { export() }, minWidth = 300f)
        }
        return ui.frame(null, content, footer)
    }

    override fun onEscape() = ctx.showMenu()

    private fun heading() = when (verdict?.status) {
        AbStatus.ACCEL_BETTER -> "The curve is better for you"
        AbStatus.ACCEL_WORSE -> "The curve was worse"
        AbStatus.NO_DIFFERENCE -> "No measurable difference"
        else -> "Test stopped"
    }

    private fun explanation(): String {
        val v = verdict ?: return "The blind test was not finished, so there is no verdict."
        val numbers = "After ${v.pairs} pairs the curve scored %+.1f points against your plain sensitivity (confidence interval %+.1f to %+.1f).".format(
            Locale.ROOT, v.meanDifference, v.lowerBound, v.upperBound,
        )
        return when (v.status) {
            AbStatus.ACCEL_BETTER -> "$numbers That is clearly better, so the curve can be exported."
            AbStatus.ACCEL_WORSE -> "$numbers Stay with your plain sensitivity; nothing is exported."
            AbStatus.NO_DIFFERENCE -> "$numbers Neither is clearly better, so plain sensitivity is the simpler choice; nothing is exported."
            AbStatus.NEED_MORE_ROUNDS -> numbers
        }
    }

    private fun exportCard(): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps("Raw Accel")).left().row()
        card.add(
            ui.label(
                "AimTune never touches Raw Accel or your games. Export writes a settings.json; put it next to rawaccel.exe (replacing the " +
                    "one there, keep a copy) or import it in Raw Accel, then apply it there. Keep your in-game sensitivity as it is.",
                Styles.CAPTION_MUTED, wrap = true,
            ),
        ).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(8f).row()
        exported?.let { result ->
            val text = result.fold({ "Saved to $it" }, { "Could not save: ${it.message}" })
            card.add(ui.label(text, if (result.isSuccess) Styles.OK else Styles.CAUTION, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(16f)
        }
        return card
    }

    private fun export() {
        exported = ctx.exportAccel(run)
        rebuild()
    }

    private fun copy() {
        val candidate = run.candidate ?: return
        Gdx.app.clipboard.contents = RawAccelExport.settingsJson(candidate.curve, run.dpi, candidate.sensitivityFactor)
    }
}
