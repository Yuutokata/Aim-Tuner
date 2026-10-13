package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.accel.AccelPhase
import de.yuuto.aimtune.app.accel.AccelRun
import de.yuuto.aimtune.app.ui.Styles
import de.yuuto.aimtune.app.ui.addButton
import de.yuuto.aimtune.app.ui.button
import de.yuuto.aimtune.app.ui.caps
import de.yuuto.aimtune.app.ui.frame
import de.yuuto.aimtune.app.ui.label
import de.yuuto.aimtune.app.ui.progressBar

private const val BAR_WIDTH = 640f
private const val TEXT_WIDTH = 1000f
private const val FOOTER_BUTTON_HEIGHT = 64f

/**
 * Shown before every calibration or test round: how far the run is and what is going on, never which
 * condition the next test round has and never a verdict before the test has decided.
 */
class AccelBetweenScreen(ctx: AppContext, private val run: AccelRun) : UiScreen(ctx) {
    private val calibrating = run.phase == AccelPhase.CALIBRATION
    private val done = if (calibrating) run.calibrationDone else run.testRoundsDone
    private val total = if (calibrating) run.calibrationRounds else run.maxTestRounds

    override fun build(): Actor {
        val content = Table()
        content.add(ui.label(heading(), Styles.HEADING)).left().padBottom(40f).row()
        content.add(ui.caps(if (calibrating) "Calibration" else "Blind test of the curve")).left().padBottom(12f).row()
        content.add(ui.progressBar(done.toFloat() / total, BAR_WIDTH)).left().padBottom(12f).row()
        content.add(ui.label(progressText(), Styles.BODY_MUTED)).left().padBottom(40f).row()
        content.add(ui.label(explanation(), Styles.CAPTION_MUTED, wrap = true)).left().width(TEXT_WIDTH)

        val footer = Table()
        footer.add(ui.button("Stop", Styles.SECONDARY) { ctx.showMenu() }).left().height(FOOTER_BUTTON_HEIGHT).padLeft(8f)
        footer.add().expandX()
        if (run.canAnalyseNow) {
            footer.addButton(ui.button("Analyse now", Styles.SECONDARY) { analyseNow() }, minWidth = 220f).padRight(16f)
        }
        footer.addButton(ui.button(if (done == 0) "Start" else "Next round", Styles.PRIMARY) { ctx.showAccelRound(run) }, minWidth = 220f)
        return ui.frame(null, content, footer)
    }

    private fun analyseNow() {
        run.analyseNow()
        ctx.showAccelStep(run)
    }

    override fun onEnter() = ctx.showAccelRound(run)

    override fun onEscape() = ctx.showMenu()

    private fun heading() = when {
        done == 0 && calibrating -> "Calibration"
        done == 0 -> "Blind test of the curve"
        else -> "Round $done complete"
    }

    private fun progressText() =
        if (calibrating) "$done of $total calibration rounds" else "$done of up to $total rounds, pairs of plain and curve"

    private fun explanation() = if (calibrating) {
        "Play as you normally would at your recommended sensitivity. The rounds mix flicks, small adjustments and target switching, " +
            "because the curve is judged by how your first movements miss at different speeds. " +
            "From ${AccelRun.MIN_CALIBRATION_ROUNDS} rounds on you can analyse early; more rounds make the result more reliable."
    } else {
        "Every pair has one round with your plain sensitivity and one with the curve, in random order, with identical targets. " +
            "You are not told which is which. The test ends as soon as it is clear, or after ${run.maxTestRounds / 2} pairs."
    }
}
