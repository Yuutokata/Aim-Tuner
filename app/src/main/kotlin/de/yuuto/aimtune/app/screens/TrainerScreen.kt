package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.Hud
import de.yuuto.aimtune.app.Phase
import de.yuuto.aimtune.app.Trainer
import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.app.TrainerScene
import de.yuuto.aimtune.app.blind.BlindRun
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.scenario.FlickConfig

private const val MILLIS_PER_SECOND = 1000L

/**
 * The 3D flick trainer: room, targets, HUD and the round state machine. Without a [blind] run it is
 * the free trainer at the current sensitivity; with one it plays a single blind round at the
 * sensitivity in [config] (chosen by the optimizer, never shown) and then moves on to the
 * between-rounds screen.
 */
class TrainerScreen(private val ctx: AppContext, private val config: TrainerConfig, private val blind: BlindRun? = null) : Screen {
    private var blindRoundDone = false
    private val trainer = Trainer(config, ctx.source) { recording ->
        val startedAtMs = System.currentTimeMillis() - config.roundSeconds * MILLIS_PER_SECOND
        val gameId = ctx.settings.current.gameId
        if (blind == null) {
            ctx.processor.submit(recording, startedAtMs, blind = false, gameId = gameId)
        } else {
            ctx.processor.submit(recording, startedAtMs, blind = true, gameId = gameId, runKey = blind.runKey)
            blind.test.recordRound(config.cm360, computeRoundMetrics(recording))
            blindRoundDone = true
        }
    }
    private val scene = TrainerScene(config.profile.verticalFovDegrees.toFloat(), FlickConfig(durationNanos = 1).targetRadius.toFloat())
    private val hud = Hud(config, ctx.ui.tokens, roundLabel = blind?.nextRoundLabel())

    /** Exposed for the screenshot tour. */
    val roundPhase: Phase get() = trainer.phase

    /** Starts a round without a click, for the screenshot tour. */
    fun startRoundForTour() = trainer.startRound()

    override fun show() {
        ctx.gate.setAllowed(true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (button != Input.Buttons.LEFT) return false
                when (trainer.phase) {
                    Phase.READY, Phase.FINISHED -> trainer.startRound()
                    Phase.RUNNING -> trainer.fire()
                }
                syncCursor()
                return true
            }

            override fun keyDown(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.ESCAPE -> when {
                        trainer.phase == Phase.RUNNING -> trainer.abort()
                        blind != null -> ctx.stopBlindTest(blind)
                        else -> ctx.showMenu()
                    }
                    Input.Keys.F3 -> if (blind == null) hud.showDebug = !hud.showDebug
                    else -> return false
                }
                syncCursor()
                return true
            }
        }
        resize(Gdx.graphics.width, Gdx.graphics.height)
    }

    override fun render(delta: Float) {
        trainer.update()
        if (blindRoundDone && blind != null) {
            ctx.showBetweenRounds(blind)
            return
        }
        syncCursor()
        val bg = ctx.ui.tokens.bg
        Gdx.gl.glClearColor(bg.r, bg.g, bg.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        scene.render(trainer.view, trainer.activeTarget)
        hud.render(trainer, ctx.source, ctx.processor.report, Gdx.graphics.framesPerSecond, ctx.ui.skin.fonts)
    }

    override fun resize(width: Int, height: Int) {
        if (width == 0 || height == 0) return
        scene.resize(width, height)
        hud.resize()
    }

    /** Hard rule: no mouse input while unfocused; a running round is discarded. */
    fun onWindowFocusLost() {
        trainer.abort()
        syncCursor()
    }

    override fun pause() = Unit

    override fun resume() = Unit

    override fun hide() {
        trainer.abort()
        Gdx.input.isCursorCatched = false
        ctx.gate.setAllowed(false)
        Gdx.input.inputProcessor = null
    }

    override fun dispose() {
        scene.dispose()
        hud.dispose()
    }

    private fun syncCursor() {
        val wanted = trainer.phase == Phase.RUNNING
        if (Gdx.input.isCursorCatched != wanted) Gdx.input.isCursorCatched = wanted
    }
}
