package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.core.optimizer.OptimizationResult
import de.yuuto.aimtune.games.GameProfile
import java.util.Locale
import kotlin.math.roundToInt

private const val COLUMN_GAP = 96f
private const val CARD_WIDTH = 520f
private const val CARD_PADDING = 32f
private const val UNIT_GAP = 16f
private const val UNIT_BASELINE_LIFT = 28f
private const val FOOTER_BUTTON_HEIGHT = 64f

/** What the results screen shows; built by [AppContext] from a finished run or from stored rounds. */
data class ResultsData(
    val profile: GameProfile,
    val dpi: Double,
    val currentCm360: Double,
    val result: OptimizationResult?,
    val rounds: Int,
    val sessions: Int,
)

/**
 * Results · Sensitivity: the recommended in-game sensitivity, its cm/360° with the credible range,
 * and the confidence. The score chart of the design (frame 1j) comes with the results milestone.
 */
class ResultsScreen(ctx: AppContext, private val data: ResultsData) : UiScreen(ctx) {
    private val recommendedSensitivity: String? = data.result?.let { data.profile.formatSensitivity(data.profile.sensitivityFor(data.dpi, it.optimumCm360)) }

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label("Results", Styles.HEADING)).left().padBottom(48f).row()
        val result = data.result
        if (result == null || recommendedSensitivity == null) {
            content.add(ui.label(emptyText(), Styles.BODY_MUTED, wrap = true)).left().width(CARD_WIDTH * 2)
        } else {
            val columns = Table()
            columns.add(recommendation(result, recommendedSensitivity)).top().left()
            columns.add(confidenceCard(result)).top().left().width(CARD_WIDTH).padLeft(COLUMN_GAP)
            content.add(columns).left()
        }
        return ui.frame(null, content, footer(recommendedSensitivity))
    }

    override fun onEscape() = ctx.showMenu()

    override fun onEnter() = ctx.showStart()

    private fun emptyText() =
        if (data.rounds == 0) "No blind rounds yet. Start a test to get a recommendation." else "${data.rounds} rounds are not enough for an estimate yet. Play a few more."

    private fun recommendation(result: OptimizationResult, sensitivity: String): Table {
        val block = Table()
        block.add(ui.caps("Recommended sensitivity")).left().padBottom(16f).row()
        block.add(ui.label(sensitivity, Styles.NUMBER_XL)).left().row()
        block.add(ui.label("in-game sensitivity · ${data.profile.name} at ${formatDpi()} DPI", Styles.CAPTION_MUTED)).left().padBottom(32f).row()
        val cm = Table()
        cm.add(ui.label("%.1f".format(Locale.ROOT, result.optimumCm360), Styles.NUMBER_LG)).bottom()
        cm.add(ui.label("cm/360°", Styles.NUMBER_MD).also { it.setAlignment(Align.left) }).bottom().padLeft(UNIT_GAP).padBottom(UNIT_BASELINE_LIFT / 2)
        block.add(cm).left().padBottom(8f).row()
        val change = ((result.optimumCm360 / data.currentCm360 - 1.0) * 100.0).roundToInt()
        val range = "Range %.1f–%.1f · now %.1f (%+d %%)".format(Locale.ROOT, result.intervalLowCm360, result.intervalHighCm360, data.currentCm360, change)
        block.add(ui.label(range, Styles.MONO_MUTED)).left()
        return block
    }

    private fun confidenceCard(result: OptimizationResult): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps("Confidence")).left().row()
        card.add(ui.label("${result.confidencePercent} %", Styles.NUMBER_LG)).left().padTop(8f).row()
        card.add(ui.label("${data.rounds} rounds · ${data.sessions} ${if (data.sessions == 1) "session" else "sessions"}", Styles.CAPTION_MUTED)).left().padTop(4f).row()
        val advice = when {
            result.atRangeEdge -> "The best value is at the edge of the range that was tested, so a better one may lie outside it."
            result.recommendMoreRounds ->
                "More rounds would make this more certain: about ${result.roundsToTarget} more ${if (result.roundsToTarget == 1) "round" else "rounds"}."
            else -> "More rounds are unlikely to change this noticeably."
        }
        card.add(ui.label(advice, Styles.BODY_MUTED, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(16f)
        return card
    }

    private fun footer(sensitivity: String?): Table {
        val footer = Table()
        footer.add(ui.button("Back", Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        if (sensitivity != null) {
            footer.addButton(ui.button("Copy $sensitivity", Styles.SECONDARY) { Gdx.app.clipboard.contents = sensitivity }, minWidth = 220f).padRight(16f)
        }
        footer.addButton(ui.button("Continue testing", Styles.PRIMARY) { ctx.showStart() }, minWidth = 260f)
        return footer
    }

    private fun formatDpi(): String = if (data.dpi == data.dpi.toLong().toDouble()) data.dpi.toLong().toString() else data.dpi.toString()
}
