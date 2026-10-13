package de.yuuto.aimtune.app.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport
import de.yuuto.aimtune.app.AppContext
import de.yuuto.aimtune.app.ui.UiSkin
import kotlin.math.min

private const val MAX_FRAME_SECONDS = 1f / 30f

/**
 * Base of the Scene2D screens. The stage works in design units (1080 high, see docs/DESIGN.md)
 * whatever the window size; [build] creates the widgets and is called again when the skin is
 * rebuilt for a new window size, so screens must keep their state outside the widgets.
 */
abstract class UiScreen(protected val ctx: AppContext) : Screen {
    protected val stage = Stage(ScreenViewport())
    protected val ui: UiSkin get() = ctx.ui.skin

    /** A text of the UI in the language of the settings. */
    protected fun t(key: String, vararg args: Any?): String = ctx.i18n.t(key, *args)

    /** Creates the screen's widget tree (normally via [de.yuuto.aimtune.app.ui.frame]). */
    protected abstract fun build(): Actor

    protected open fun onEscape() {}

    protected open fun onEnter() {}

    /** Extra keyboard shortcuts; return true if [keycode] was used. */
    protected open fun onKey(keycode: Int): Boolean = false

    /** Called when the window loses focus. */
    open fun onWindowFocusLost() {}

    fun rebuild() {
        stage.clear()
        stage.addActor(build())
    }

    override fun show() {
        stage.addCaptureListener(object : InputListener() {
            override fun keyDown(event: InputEvent, keycode: Int): Boolean {
                val used = when (keycode) {
                    Input.Keys.ESCAPE -> true.also { onEscape() }
                    Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> true.also { onEnter() }
                    else -> onKey(keycode)
                }
                if (used) event.stop()
                return used
            }
        })
        ctx.gate.setAllowed(false)
        updateViewport()
        rebuild()
        Gdx.input.inputProcessor = stage
    }

    override fun render(delta: Float) {
        val bg = ui.tokens.bg
        Gdx.gl.glClearColor(bg.r, bg.g, bg.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(min(delta, MAX_FRAME_SECONDS))
        stage.draw()
    }

    override fun resize(width: Int, height: Int) = updateViewport()

    private fun updateViewport() {
        if (Gdx.graphics.height == 0) return
        (stage.viewport as ScreenViewport).unitsPerPixel = ui.tokens.screenHeight / Gdx.graphics.height
        stage.viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)
    }

    override fun pause() = Unit

    override fun resume() = Unit

    override fun hide() {
        stage.unfocusAll()
        if (Gdx.input.inputProcessor === stage) Gdx.input.inputProcessor = null
    }

    override fun dispose() = stage.dispose()
}
