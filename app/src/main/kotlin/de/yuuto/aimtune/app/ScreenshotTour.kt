package de.yuuto.aimtune.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils
import de.yuuto.aimtune.app.accel.AccelPhase
import de.yuuto.aimtune.app.accel.LastCalibration
import de.yuuto.aimtune.app.accel.PriorCalibration
import de.yuuto.aimtune.app.accel.sampleAccelRun
import de.yuuto.aimtune.app.blind.sampleBlindRun
import de.yuuto.aimtune.app.history.HistoryEntry
import de.yuuto.aimtune.app.screens.HistoryScreen
import de.yuuto.aimtune.app.screens.ShareDataScreen
import de.yuuto.aimtune.app.screens.StoredCounts
import de.yuuto.aimtune.app.screens.TrainerScreen
import de.yuuto.aimtune.app.setup.SetupStep
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.storage.SessionKind
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

private const val FRAMES_BEFORE_CAPTURE = 4
private const val OPAQUE = 255.toByte()
private const val BYTES_PER_PIXEL = 4

/**
 * Developer tool (`--screenshots <dir>`): shows every screen with sample data, saves a PNG of
 * each and exits. It runs on in-memory settings and never touches the user's data. Used to check
 * layouts without a person clicking through the app, and later for the README screenshots.
 */
class ScreenshotTour(private val ctx: AppContext, private val dir: Path, private val exit: () -> Unit) {
    private class Step(val name: String, val show: () -> Unit)

    private val log = System.getLogger("aimtune.screenshots")
    private val steps = listOf(
        Step("menu") { ctx.showMenu() },
        Step("onboarding-1-dpi") { ctx.showOnboarding(SetupStep.DPI) },
        Step("onboarding-2-game") { ctx.showOnboarding(SetupStep.GAME) },
        Step("onboarding-3-style") { ctx.showOnboarding(SetupStep.STYLE) },
        Step("onboarding-4-pad") { ctx.showOnboarding(SetupStep.PAD) },
        Step("pretest") { ctx.showPreTest() },
        Step("settings") { ctx.showSettings() },
        Step("crosshair") { ctx.showCrosshair() },
        Step("trainer-ready") { ctx.showTrainer() },
        Step("trainer-running") { (ctx.currentScreen as? TrainerScreen)?.startRoundForTour() },
        Step("micro-ready") { showScenario(ScenarioKind.MICRO) },
        Step("micro-running") { (ctx.currentScreen as? TrainerScreen)?.startRoundForTour() },
        Step("tracking-ready") { showScenario(ScenarioKind.TRACKING) },
        Step("tracking-running") { (ctx.currentScreen as? TrainerScreen)?.startRoundForTour() },
        Step("switching-ready") { showScenario(ScenarioKind.SWITCHING) },
        Step("switching-running") { (ctx.currentScreen as? TrainerScreen)?.startRoundForTour() },
        Step("accel-intro") { ctx.showAccel() },
        Step("accel-last") { accelSample(AccelPhase.ANALYSIS)?.let { ctx.showLastAccelAnalysis(LastCalibration(it.dpi, it.baseCm360, PriorCalibration(it.calibrationDone, it.calibrationSamples))) } },
        Step("accel-calibration") { accelSample(AccelPhase.CALIBRATION)?.let(ctx::showAccelStep) },
        Step("accel-analysis") { accelSample(AccelPhase.ANALYSIS)?.let(ctx::showAccelStep) },
        Step("accel-search") { accelSample(AccelPhase.SEARCH)?.let(ctx::showAccelStep) },
        Step("accel-search-result") { accelSample(AccelPhase.SEARCH_RESULT)?.let(ctx::showAccelStep) },
        Step("accel-test") { accelSample(AccelPhase.TEST)?.let(ctx::showAccelStep) },
        Step("accel-result") { accelSample(AccelPhase.DONE)?.let(ctx::showAccelStep) },
        Step("blind-ready") { sample?.let(ctx::showBlindRound) },
        Step("blind-running") { (ctx.currentScreen as? TrainerScreen)?.startRoundForTour() },
        Step("between-rounds") { sample?.let(ctx::showBetweenRounds) },
        Step("results") { sample?.let(ctx::stopBlindTest) },
        Step("history") { ctx.switchTo(HistoryScreen(ctx, CompletableFuture.completedFuture(sampleHistory()))) },
        Step("share-data") { ctx.switchTo(ShareDataScreen(ctx, CompletableFuture.completedFuture(StoredCounts(3, 42)))) },
    )
    private val sample by lazy { ctx.trainerConfig()?.let(::sampleBlindRun) }
    private var index = -1
    private var frames = 0

    private fun sampleHistory(): List<HistoryEntry> {
        val day = 24L * 60 * 60 * 1000
        val start = System.currentTimeMillis() - 9 * day
        val trend = listOf(47.5 to 31, 45.8 to 44, 44.1 to 52, 44.9 to 63, 43.6 to 71, 43.9 to 78, 43.4 to 83)
        return trend.mapIndexed { i, (cm, confidence) ->
            HistoryEntry(i + 1L, start + i * day, "valorant", SessionKind.SENSITIVITY, 10, cm, confidence)
        } + HistoryEntry(8, start + 8 * day, "valorant", SessionKind.ACCEL_AB, 14, null, null)
    }

    private fun accelSample(until: AccelPhase) = ctx.trainerConfig()?.let { sampleAccelRun(it, until) }

    private fun showScenario(kind: ScenarioKind) {
        ctx.trainerConfig()?.let { ctx.switchTo(TrainerScreen(ctx, it.copy(scenario = kind))) }
    }

    /** Call after every rendered frame. */
    fun afterRender() {
        if (index < 0) {
            Files.createDirectories(dir)
            advance()
            return
        }
        if (++frames < FRAMES_BEFORE_CAPTURE) return
        capture(steps[index].name)
        advance()
    }

    private fun advance() {
        index++
        frames = 0
        if (index >= steps.size) {
            log.log(System.Logger.Level.INFO, "screenshots saved to $dir")
            exit()
            return
        }
        steps[index].show()
    }

    private fun capture(name: String) {
        val width = Gdx.graphics.backBufferWidth
        val height = Gdx.graphics.backBufferHeight
        val pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true)
        // The frame buffer's alpha is not meaningful; make the whole image opaque, as shown on screen.
        for (i in BYTES_PER_PIXEL - 1 until pixels.size step BYTES_PER_PIXEL) pixels[i] = OPAQUE
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
        BufferUtils.copy(pixels, 0, pixmap.pixels, pixels.size)
        PixmapIO.writePNG(Gdx.files.absolute(dir.resolve("$name.png").toAbsolutePath().toString()), pixmap)
        pixmap.dispose()
        log.log(System.Logger.Level.INFO, "saved $name.png (${width}x$height)")
    }
}
