package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.UiSkin
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.core.score.FlashImportance

private const val SEGMENT_WIDTH = 220f
private const val SEGMENT_HEIGHT = 64f
private const val SEGMENT_GAP = 16f

/** Never / sometimes / often as three tile buttons, shared by the settings and the onboarding. [labels] are the texts in the user's language. */
internal fun UiSkin.flashImportanceButtons(
    labels: Map<FlashImportance, String>,
    selected: FlashImportance,
    onPick: (FlashImportance) -> Unit,
    segmentWidth: Float = SEGMENT_WIDTH,
): Table {
    val table = Table()
    FlashImportance.entries.forEach { importance ->
        val button: TextButton = button(labels.getValue(importance), Styles.TILE) { onPick(importance) }
        button.setProgrammaticChangeEvents(false)
        button.isChecked = importance == selected
        table.add(button).width(segmentWidth).height(SEGMENT_HEIGHT).padRight(SEGMENT_GAP)
    }
    return table
}
