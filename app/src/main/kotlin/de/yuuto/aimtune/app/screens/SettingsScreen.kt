package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.setup.GAME_CUSTOM
import de.yuuto.aimtune.app.setup.SetupDraft
import de.yuuto.aimtune.app.setup.SetupStep
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.app.ui.onChange
import de.yuuto.aimtune.core.score.ScoreMetric
import de.yuuto.aimtune.storage.AppSettings
import java.util.Locale

private const val LABEL_WIDTH = 300f
private const val FIELD_WIDTH = 300f
private const val CONTROL_HEIGHT = 56f
private const val ROW_GAP = 28f
private const val COLUMN_GAP = 160f
private const val WEIGHTS_WIDTH = 640f
private const val SLIDER_ROW_GAP = 28f
private const val SEGMENT_WIDTH = 100f
private const val SEGMENT_GAP = 8f
private const val FOOTER_BUTTON_HEIGHT = 64f
private val ROUND_LENGTHS = listOf(30, 45, 60, 90)

private val METRIC_NAMES = mapOf(
    ScoreMetric.ACCURACY to "Accuracy",
    ScoreMetric.TIME_TO_HIT to "Time to hit",
    ScoreMetric.TRACKING to "Tracking",
    ScoreMetric.CONSISTENCY to "Consistency",
)

/** Edit game, DPI, sensitivity, round length and score weights; aim style and pad via "Run setup again". */
class SettingsScreen(ctx: AppContext) : UiScreen(ctx) {
    private var draft = SetupDraft.fromSettings(ctx.settings.current)
    private var roundSeconds = ctx.settings.current.roundSeconds
    private val weights = ctx.settings.current.metricWeights.toMutableMap()
    private var message: Pair<String, Boolean>? = null
    private var liveCm: Label? = null
    private var totalLabel: Label? = null

    override fun build(): Actor {
        val left = Table()
        left.add(ui.label("Settings", Styles.HEADING)).left().padBottom(ROW_GAP + 8f).colspan(2).row()
        addRow(left, "Game profile", gameSelect())
        if (draft.gameId == GAME_CUSTOM) addRow(left, "Game yaw", textField(draft.customYawText) { draft = draft.copy(customYawText = it) })
        if (draft.gameId == GAME_CUSTOM) addRow(left, "Horizontal FOV", textField(draft.customFovText) { draft = draft.copy(customFovText = it) })
        addRow(left, "Mouse DPI", textField(draft.dpiText) { draft = draft.copy(dpiText = it) })
        addRow(left, "In-game sensitivity", sensitivityControl())
        addRow(left, "Round length", roundLengthButtons())
        val runSetup = ui.button("Run setup again", Styles.SECONDARY) { ctx.showOnboarding(SetupStep.DPI, afterFinish = ctx::showSettings) }
        addRow(left, "Aim style and mousepad", Table().also { it.add(runSetup).height(CONTROL_HEIGHT) })

        val content = Table()
        content.add(left).top().left()
        content.add(weightsBlock()).top().left().padLeft(COLUMN_GAP).padTop(ui.tokens.footerHeight)
        return ui.frame(null, content, footer())
    }

    override fun onEscape() = ctx.showMenu()

    private fun footer(): Table {
        val footer = Table()
        footer.add(ui.button("Back", Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        message?.let { (text, ok) -> footer.add(ui.label(text, if (ok) Styles.OK else Styles.CAUTION)).right().padRight(32f) }
        footer.add(ui.button("Reset to defaults", Styles.TEXT) { reset() }).height(FOOTER_BUTTON_HEIGHT).padRight(16f)
        footer.addButton(ui.button("Save", Styles.PRIMARY) { save() }, minWidth = 180f)
        return footer
    }

    private fun addRow(table: Table, name: String, control: Actor) {
        table.add(ui.label(name, Styles.BODY_MUTED)).left().width(LABEL_WIDTH).padBottom(ROW_GAP)
        table.add(control).left().padBottom(ROW_GAP).row()
    }

    /** A number field in a fixed-size cell, for the label/control rows. */
    private fun textField(text: String, onText: (String) -> Unit): Table =
        Table().also { it.add(rawField(text, onText)).width(FIELD_WIDTH).height(CONTROL_HEIGHT) }

    private fun rawField(text: String, onText: (String) -> Unit): TextField {
        val field = TextField(text, ui.skin, Styles.INPUT)
        field.setTextFieldFilter { _, c -> c.isDigit() || c == '.' || c == ',' }
        field.maxLength = MAX_INPUT_CHARS
        field.onChange {
            message = null
            onText(field.text)
            liveCm?.setText(liveText())
        }
        return field
    }

    private fun gameSelect(): Table {
        val select = SelectBox<String>(ui.skin)
        val options = ctx.profiles.profiles.map { it.id to it.name } + (GAME_CUSTOM to "Custom yaw…")
        select.setItems(*options.map { it.second }.toTypedArray())
        select.selected = options.firstOrNull { it.first == draft.gameId }?.second
        select.onChange {
            val id = options.first { it.second == select.selected }.first
            draft = draft.copy(gameId = id)
            message = null
            rebuild()
        }
        val table = Table()
        table.add(select).width(FIELD_WIDTH + 80f).height(CONTROL_HEIGHT)
        val yaw = draft.profile(ctx.profiles)?.yawDegreesPerCount
        table.add(ui.label(yaw?.let { "yaw ${SetupDraft.formatNumber(it)}" } ?: "", Styles.MONO_MUTED)).padLeft(16f)
        return table
    }

    private fun sensitivityControl(): Table {
        val table = Table()
        table.add(rawField(draft.sensitivityText) { draft = draft.copy(sensitivityText = it) }).width(FIELD_WIDTH).height(CONTROL_HEIGHT)
        liveCm = ui.label(liveText(), Styles.MONO_MUTED).also { table.add(it).padLeft(24f) }
        return table
    }

    private fun liveText(): String = draft.currentCm360(ctx.profiles)?.let { "= %.1f cm/360°".format(Locale.ROOT, it) } ?: ""

    private fun roundLengthButtons(): Table {
        val table = Table()
        ROUND_LENGTHS.forEach { seconds ->
            val button: TextButton = ui.button("$seconds s", Styles.TILE) {
                roundSeconds = seconds
                message = null
                rebuild()
            }
            button.setProgrammaticChangeEvents(false)
            button.isChecked = roundSeconds == seconds
            table.add(button).width(SEGMENT_WIDTH).height(CONTROL_HEIGHT).padRight(SEGMENT_GAP)
        }
        return table
    }

    private fun weightsBlock(): Table {
        val block = Table()
        block.add(ui.caps("Metric weights")).left().padBottom(8f).row()
        block.add(ui.label("How each metric counts toward the round score.", Styles.CAPTION_MUTED)).left().padBottom(32f).row()
        METRIC_NAMES.forEach { (metric, name) -> block.add(weightRow(metric, name)).left().width(WEIGHTS_WIDTH).padBottom(SLIDER_ROW_GAP).row() }
        totalLabel = ui.label(totalText(), Styles.MONO_MUTED).also { block.add(it).left() }
        return block
    }

    private fun weightRow(metric: ScoreMetric, name: String): Table {
        val row = Table()
        val value = ui.label(percentText(metric), Styles.MONO)
        row.add(ui.label(name, Styles.BODY)).left().expandX()
        row.add(value).right().row()
        val slider = Slider(0f, 100f, 1f, false, ui.skin)
        slider.value = (weights[metric.name] ?: 0.0).toFloat()
        slider.onChange {
            weights[metric.name] = slider.value.toDouble()
            message = null
            value.setText(percentText(metric))
            totalLabel?.setText(totalText())
        }
        row.add(slider).growX().colspan(2).padTop(12f)
        return row
    }

    private fun percentText(metric: ScoreMetric) = "%.0f %%".format(Locale.ROOT, weights[metric.name] ?: 0.0)

    private fun totalText() = "Total %.0f %%".format(Locale.ROOT, weights.values.sum())

    private fun save() {
        val problem = listOf(SetupStep.DPI, SetupStep.GAME).firstNotNullOfOrNull { draft.issue(it, ctx.profiles) }
        if (problem != null) {
            message = problem.message to false
            rebuild()
            return
        }
        val base: AppSettings = ctx.settings.current.copy(roundSeconds = roundSeconds, metricWeights = weights.toMap())
        val error = ctx.settings.save(draft.toSettings(base, ctx.profiles))
        if (error == null) {
            ctx.showMenu()
        } else {
            message = error to false
            rebuild()
        }
    }

    private fun reset() {
        roundSeconds = AppSettings.DEFAULT_ROUND_SECONDS
        weights.clear()
        weights.putAll(AppSettings.DEFAULT_WEIGHTS)
        message = "Round length and weights reset. Press Save to keep them." to true
        rebuild()
    }

    private companion object {
        const val MAX_INPUT_CHARS = 10
    }
}
