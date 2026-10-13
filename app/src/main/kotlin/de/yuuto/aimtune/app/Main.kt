package de.yuuto.aimtune.app

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import de.yuuto.aimtune.app.ui.Tokens
import kotlin.system.exitProcess

private const val WINDOW_WIDTH = 1280
private const val WINDOW_HEIGHT = 720
private const val SCREENSHOT_WIDTH = 1920
private const val SCREENSHOT_HEIGHT = 1080
private const val IDLE_FPS = 30
private const val DEPTH_BITS = 24
private const val MSAA_SAMPLES = 4

fun main(args: Array<String>) {
    val options = try {
        parseLaunchOptions(args)
    } catch (e: LaunchOptionsException) {
        System.err.println("error: ${e.message}\n\n${LaunchOptions.USAGE}")
        exitProcess(2)
    }
    val app = AimTuneApp(options)
    val screenshots = options.screenshotDir != null
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("AimTune")
        setWindowedMode(if (screenshots) SCREENSHOT_WIDTH else WINDOW_WIDTH, if (screenshots) SCREENSHOT_HEIGHT else WINDOW_HEIGHT)
        // The screenshot tour renders in a hidden window and does not wait for vertical sync.
        useVsync(options.vsync && !screenshots)
        setInitialVisible(!screenshots)
        setIdleFPS(IDLE_FPS)
        setBackBufferConfig(8, 8, 8, 8, DEPTH_BITS, 0, MSAA_SAMPLES)
        setInitialBackgroundColor(Tokens.load().bg)
        setWindowListener(app.windowListener)
    }
    Lwjgl3Application(app, config)
}
