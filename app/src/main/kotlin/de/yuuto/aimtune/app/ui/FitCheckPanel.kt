package de.yuuto.aimtune.app.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.setup.SetupDraft
import de.yuuto.aimtune.core.units.doesTurnFitPad
import java.util.Locale
import kotlin.math.min

private const val PANEL_PADDING = 28f
private const val LINE_AREA_HEIGHT = 120f
private const val LINE_THICKNESS = 4f
private const val TICK_HEIGHT = 22f
private const val TICK_WIDTH = 3f

/**
 * "Fit check" of the mousepad step: draws the distance of one full turn against the pad width, in
 * the accent color when it fits and in the caution color (clipped to the pad) when it does not.
 */
class FitCheckPanel(private val ui: UiSkin, private val cm360: Double, private val padWidthCm: Double) : Table() {
    init {
        background(ui.skin.getDrawable(Styles.CARD))
        pad(PANEL_PADDING)
        add(TurnLine()).growX().height(LINE_AREA_HEIGHT).row()
        add(ui.label("%.1f cm for 360°".format(Locale.ROOT, cm360), Styles.MONO)).left().row()
        add().grow().row()
        add(ui.label("${SetupDraft.formatNumber(padWidthCm)} cm", Styles.MONO_MUTED)).right()
    }

    val fits: Boolean get() = doesTurnFitPad(cm360, padWidthCm)

    private inner class TurnLine : Actor() {
        override fun draw(batch: Batch, parentAlpha: Float) {
            val ratio = cm360 / padWidthCm
            val length = width * min(ratio, 1.0).toFloat()
            val color = if (fits) ui.tokens.accent else ui.tokens.caution
            val centerY = y + height / 2
            batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
            ui.whiteSolid.draw(batch, x, centerY - LINE_THICKNESS / 2, length, LINE_THICKNESS)
            ui.whiteSolid.draw(batch, x, centerY - TICK_HEIGHT / 2, TICK_WIDTH, TICK_HEIGHT)
            ui.whiteSolid.draw(batch, x + length - TICK_WIDTH, centerY - TICK_HEIGHT / 2, TICK_WIDTH, TICK_HEIGHT)
            batch.color = Color.WHITE
        }
    }
}
