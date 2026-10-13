package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.history.HistoryEntry
import de.yuuto.aimtune.app.ui.ChartActor
import de.yuuto.aimtune.app.ui.ChartDots
import de.yuuto.aimtune.app.ui.ChartLine
import de.yuuto.aimtune.app.ui.ChartSpec
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.app.ui.onChange
import de.yuuto.aimtune.storage.SessionKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CompletableFuture

private const val FOOTER_BUTTON_HEIGHT = 64f
private const val CHART_HEIGHT = 300f
private const val FILTER_WIDTH = 320f
private const val ROWS_SHOWN = 6
private const val ROW_GAP = 8f
private val COLUMN_WIDTHS = floatArrayOf(270f, 280f, 130f, 190f, 190f, 220f)
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private const val ALL_GAMES = "All games"

/**
 * Stored sessions: the recommended cm/360° after each sensitivity session as a chart and a table of
 * the newest sessions, filterable by game. The recommendations are computed in the background
 * ([entriesFuture]) because every session needs its own model fit; the screen fills in when they are done.
 */
class HistoryScreen(ctx: AppContext, private val entriesFuture: CompletableFuture<List<HistoryEntry>>) : UiScreen(ctx) {
    private var entries: List<HistoryEntry>? = null
    private var gameFilter: String? = null

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label("History", Styles.HEADING)).left().padBottom(24f).row()
        val all = entries
        when {
            all == null -> content.add(ui.label("Reading the stored sessions…", Styles.BODY_MUTED)).left()
            all.isEmpty() -> content.add(ui.label("No sessions yet. Play a test first.", Styles.BODY_MUTED)).left()
            else -> fill(content, all)
        }
        val footer = Table()
        footer.add(ui.button("Back", Styles.TEXT) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        return ui.frame(null, content, footer)
    }

    override fun render(delta: Float) {
        if (entries == null && entriesFuture.isDone) {
            entries = runCatching { entriesFuture.get() }.getOrDefault(emptyList())
            rebuild()
        }
        super.render(delta)
    }

    override fun onEscape() = ctx.showMenu()

    private fun fill(content: Table, all: List<HistoryEntry>) {
        content.add(filterRow(all)).left().padBottom(16f).row()
        val shown = all.filter { gameFilter == null || it.gameId == gameFilter }
        val recommended = shown.filter { it.cm360 != null }
        if (recommended.size >= 2) {
            content.add(ui.caps("Recommended cm/360° after each sensitivity session")).left().padBottom(8f).row()
            content.add(ChartActor(ui, trendChart(recommended))).growX().height(CHART_HEIGHT).padBottom(16f).row()
        }
        content.add(table(shown)).left().top()
    }

    private fun filterRow(all: List<HistoryEntry>): Table {
        val games = all.map { it.gameId }.distinct()
        val row = Table()
        val select = SelectBox<String>(ui.skin)
        val names = listOf(ALL_GAMES) + games.map(::gameName)
        select.setItems(*names.toTypedArray())
        select.selected = if (gameFilter == null) ALL_GAMES else gameName(gameFilter)
        select.onChange {
            gameFilter = if (select.selectedIndex == 0) null else games[select.selectedIndex - 1]
            rebuild()
        }
        row.add(select).width(FILTER_WIDTH)
        return row
    }

    private fun table(shown: List<HistoryEntry>): Table {
        val table = Table()
        val headers = listOf("Date", "Game", "Rounds", "cm/360°", "Confidence", "Type")
        headers.forEachIndexed { i, text -> table.add(ui.caps(text)).left().width(COLUMN_WIDTHS[i]).padBottom(ROW_GAP) }
        table.row()
        shown.asReversed().take(ROWS_SHOWN).forEach { entry ->
            val cells = listOf(
                DATE_FORMAT.format(Instant.ofEpochMilli(entry.startedAtMs).atZone(ZoneId.systemDefault())),
                gameName(entry.gameId),
                entry.rounds.toString(),
                entry.cm360?.let { "%.1f".format(Locale.ROOT, it) } ?: "–",
                entry.confidencePercent?.let { "$it %" } ?: "–",
                if (entry.kind == SessionKind.ACCEL_AB) "Accel A/B" else "Sensitivity",
            )
            cells.forEachIndexed { i, text -> table.add(ui.label(text, Styles.MONO)).left().width(COLUMN_WIDTHS[i]).padBottom(ROW_GAP) }
            table.row()
        }
        if (shown.size > ROWS_SHOWN) table.add(ui.label("${shown.size - ROWS_SHOWN} older sessions not shown", Styles.CAPTION_MUTED)).left().colspan(headers.size)
        return table
    }

    private fun trendChart(recommended: List<HistoryEntry>): ChartSpec {
        val xs = DoubleArray(recommended.size) { it + 1.0 }
        val ys = DoubleArray(recommended.size) { checkNotNull(recommended[it].cm360) }
        val pad = maxOf((ys.max() - ys.min()) * Y_PADDING, MIN_Y_PADDING)
        val ticks = (1..recommended.size).filter { recommended.size <= MAX_TICKS || it % (recommended.size / MAX_TICKS + 1) == 0 }.map { it.toDouble() }
        return ChartSpec(
            xMin = 0.5,
            xMax = recommended.size + 0.5,
            yMin = ys.min() - pad,
            yMax = ys.max() + pad,
            xTicks = ticks,
            formatX = { "#%d".format(Locale.ROOT, it.toInt()) },
            lines = listOf(ChartLine(xs, ys, ui.tokens.accent(300))),
            dots = listOf(ChartDots(xs, ys, ui.tokens.accent(300))),
        )
    }

    private fun gameName(id: String?): String = id?.let { ctx.profiles.find(it)?.name ?: it } ?: "–"

    private companion object {
        const val Y_PADDING = 0.25
        const val MIN_Y_PADDING = 1.0
        const val MAX_TICKS = 10
    }
}
