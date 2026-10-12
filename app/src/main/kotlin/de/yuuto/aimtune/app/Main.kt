package de.yuuto.aimtune.app

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import kotlin.system.exitProcess

private const val WINDOW_WIDTH = 1280
private const val WINDOW_HEIGHT = 720
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
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("AimTune")
        setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT)
        useVsync(options.vsync)
        setIdleFPS(IDLE_FPS)
        setBackBufferConfig(8, 8, 8, 8, DEPTH_BITS, 0, MSAA_SAMPLES)
        setInitialBackgroundColor(Theme.bg)
        setWindowListener(app.windowListener)
    }
    Lwjgl3Application(app, config)
}
