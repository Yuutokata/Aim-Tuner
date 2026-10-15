package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.accel.AccelRun
import de.yuuto.aimtune.app.accel.StrengthResult
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.core.accel.CurveStrength
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TEXT_WIDTH = 1000f
private const val CARD_WIDTH = 900f
private const val CARD_PADDING = 32f
private const val FOOTER_BUTTON_HEIGHT = 64f

/**
 * What the blind strength search found: the strength that scored best with its credible range, and whether
 * it stands clearly on one side of "no curve". Nothing is exported here; a found strength still goes
 * through the blind A/B test against plain sensitivity.
 */
class AccelSearchResultScreen(ctx: AppContext, private val run: AccelRun) : UiScreen(ctx) {
    private val result: StrengthResult? = run.searchResult

    override fun build(): Actor {
        val offer = run.searchOffer
        val content = Table()
        content.add(ui.label(if (offer != null) t("accel.search.found") else t("accel.search.none"), Styles.HEADING)).left().padBottom(32f).row()
        content.add(ui.label(explanation(offer != null), Styles.BODY_MUTED, wrap = true)).left().width(TEXT_WIDTH).padBottom(32f).row()
        result?.let { content.add(resultCard(it)).left().width(CARD_WIDTH).row() }

        val footer = Table()
        footer.add(ui.button(if (offer == null) t("common.back") else t("accel.analysis.not_now"), Styles.TEXT) { leave() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        if (offer != null) {
            footer.addButton(ui.button(t("accel.analysis.test_blind"), Styles.PRIMARY) { test() }, minWidth = 260f)
        }
        return ui.frame(null, content, footer)
    }

    override fun onEscape() = leave()

    override fun onEnter() {
        if (run.searchOffer != null) test()
    }

    private fun leave() {
        run.finish()
        ctx.showMenu()
    }

    private fun test() {
        run.startSearchTest()
        ctx.showAccelStep(run)
    }

    private fun explanation(offered: Boolean): String {
        val r = result ?: return t("accel.search.too_few")
        return when {
            offered -> t("accel.search.offer_note", r.rounds)
            r.low <= CurveStrength.NEUTRAL && r.high >= CurveStrength.NEUTRAL -> t("accel.search.reason_range")
            else -> t("accel.search.reason_plain")
        }
    }

    private fun resultCard(r: StrengthResult): Table {
        val card = Table()
        card.background(ui.skin.getDrawable(Styles.CARD))
        card.pad(CARD_PADDING)
        card.add(ui.caps(t("accel.search.card_title"))).left().row()
        val percent = ((r.strength - 1.0) * 100.0).roundToInt()
        val text = when {
            !CurveStrength.isCurve(r.strength) -> t("accel.search.strength_plain")
            percent > 0 -> t("accel.search.strength_rising", percent)
            else -> t("accel.search.strength_falling", abs(percent))
        }
        card.add(ui.label(text, Styles.TITLE, wrap = true)).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(8f).row()
        card.add(
            ui.label(t("accel.search.range", number(r.low), number(r.high), r.confidencePercent), Styles.CAPTION_MUTED, wrap = true),
        ).left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(12f).row()
        if (r.atEdge) {
            card.add(ui.label(t("accel.search.edge", number(CurveStrength.MIN), number(CurveStrength.MAX)), Styles.CAUTION, wrap = true))
                .left().width(CARD_WIDTH - 2 * CARD_PADDING).padTop(12f)
        }
        return card
    }

    private fun number(value: Double) = "%.2f".format(Locale.ROOT, value)
}
