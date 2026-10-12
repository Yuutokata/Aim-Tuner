package de.yuuto.aimtune.app

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.badlogic.gdx.graphics.GL20
import de.yuuto.aimtune.app.input.GlfwMouseSource
import de.yuuto.aimtune.core.scenario.FlickConfig
import de.yuuto.aimtune.input.RawMouseSource
import de.yuuto.aimtune.input.windows.WindowsRawInputSource
import de.yuuto.aimtune.storage.AppPaths
import de.yuuto.aimtune.storage.Database
import org.lwjgl.glfw.GLFW

/** LibGDX entry point of the milestone-2 flick trainer. */
class AimTuneApp(private val options: LaunchOptions) : ApplicationAdapter() {
    private val log = System.getLogger("aimtune.app")
    private lateinit var source: RawMouseSource
    private lateinit var processor: RoundProcessor
    private lateinit var trainer: Trainer
    private lateinit var scene: TrainerScene
    private lateinit var hud: Hud

    /** Reports window focus to the mouse source; installed through the application configuration. */
    val windowListener = object : Lwjgl3WindowAdapter() {
        override fun focusGained() {
            if (::source.isInitialized) source.setWindowFocused(true)
        }

        override fun focusLost() {
            if (!::source.isInitialized) return
            // Hard rule: no mouse input while unfocused. A running round is discarded.
            source.setWindowFocused(false)
            trainer.abort()
            syncCursor()
        }
    }

    override fun create() {
        source = createSource()
        source.start()
        source.setWindowFocused(isWindowFocused())
        log.log(System.Logger.Level.INFO, "mouse source: ${source.name}")

        processor = openProcessor()
        trainer = Trainer(options, source) { recording ->
            val startedAtMs = System.currentTimeMillis() - options.roundSeconds * MILLIS_PER_SECOND
            processor.submit(recording, startedAtMs, blind = false)
        }
        val verticalFov = options.fov.verticalDegrees(options.fovReferenceAspect).toFloat()
        scene = TrainerScene(verticalFov, FlickConfig(durationNanos = 1).targetRadius.toFloat())
        hud = Hud(options)
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
                    Input.Keys.ESCAPE -> if (trainer.phase == Phase.RUNNING) trainer.abort() else Gdx.app.exit()
                    Input.Keys.F3 -> hud.showDebug = !hud.showDebug
                    else -> return false
                }
                syncCursor()
                return true
            }
        }
        resize(Gdx.graphics.width, Gdx.graphics.height)
    }

    override fun resize(width: Int, height: Int) {
        if (width == 0 || height == 0) return
        scene.resize(width, height)
        hud.resize()
    }

    override fun render() {
        trainer.update()
        syncCursor()
        Gdx.gl.glClearColor(Theme.bg.r, Theme.bg.g, Theme.bg.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        scene.render(trainer.view, trainer.activeTarget)
        hud.render(trainer, source, processor.report, Gdx.graphics.framesPerSecond)
    }

    override fun dispose() {
        scene.dispose()
        hud.dispose()
        source.close()
        processor.close()
    }

    private fun syncCursor() {
        val wanted = trainer.phase == Phase.RUNNING
        if (Gdx.input.isCursorCatched != wanted) Gdx.input.isCursorCatched = wanted
    }

    private fun isWindowFocused(): Boolean =
        GLFW.glfwGetWindowAttrib(windowHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE

    private fun windowHandle(): Long = (Gdx.graphics as Lwjgl3Graphics).window.windowHandle

    /** A storage failure (unwritable data dir, newer schema) must not stop the trainer; rounds are then analysed but not saved. */
    private fun openProcessor(): RoundProcessor {
        val database = try {
            Database.open(AppPaths.default().databaseFile)
        } catch (e: Exception) {
            log.log(System.Logger.Level.ERROR, "rounds will not be saved: ${e.message}", e)
            null
        }
        return RoundProcessor(database, options.dpi)
    }

    private fun createSource(): RawMouseSource {
        val isWindows = System.getProperty("os.name").startsWith("Windows")
        return when (options.source) {
            SourceKind.GLFW -> GlfwMouseSource(windowHandle())
            SourceKind.RAW -> {
                check(isWindows) { "--source raw needs Windows" }
                WindowsRawInputSource()
            }
            SourceKind.AUTO -> if (isWindows) WindowsRawInputSource() else GlfwMouseSource(windowHandle())
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
