package de.yuuto.aimtune.app

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import de.yuuto.aimtune.app.accel.AccelPhase
import de.yuuto.aimtune.app.accel.AccelRun
import de.yuuto.aimtune.app.audio.FlashSounds
import de.yuuto.aimtune.app.audio.HitSounds
import de.yuuto.aimtune.app.accel.AccelSession
import de.yuuto.aimtune.app.blind.AxisSummary
import de.yuuto.aimtune.app.blind.BlindRun
import de.yuuto.aimtune.app.blind.BlindSession
import de.yuuto.aimtune.app.blind.BlindTest
import de.yuuto.aimtune.app.blind.PastRound
import de.yuuto.aimtune.app.screens.AccelAnalysisScreen
import de.yuuto.aimtune.app.screens.AccelBetweenScreen
import de.yuuto.aimtune.app.screens.AccelIntroScreen
import de.yuuto.aimtune.app.screens.AccelResultScreen
import de.yuuto.aimtune.app.screens.AccelSearchResultScreen
import de.yuuto.aimtune.app.history.HistoryEntry
import de.yuuto.aimtune.app.i18n.I18n
import de.yuuto.aimtune.app.i18n.Language
import de.yuuto.aimtune.app.history.buildHistory
import de.yuuto.aimtune.app.screens.BetweenRoundsScreen
import de.yuuto.aimtune.app.screens.CrosshairScreen
import de.yuuto.aimtune.app.screens.HistoryScreen
import de.yuuto.aimtune.app.screens.ShareDataScreen
import de.yuuto.aimtune.app.screens.StoredCounts
import de.yuuto.aimtune.app.screens.MainMenuScreen
import de.yuuto.aimtune.app.screens.OnboardingScreen
import de.yuuto.aimtune.app.screens.PreTestScreen
import de.yuuto.aimtune.app.screens.ResultsData
import de.yuuto.aimtune.app.screens.ResultsScreen
import de.yuuto.aimtune.app.screens.SettingsScreen
import de.yuuto.aimtune.app.screens.TrainerScreen
import de.yuuto.aimtune.app.screens.UiScreen
import de.yuuto.aimtune.app.setup.SetupDraft
import de.yuuto.aimtune.app.setup.SetupStep
import de.yuuto.aimtune.app.ui.UiHolder
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.score.FlashImportance
import de.yuuto.aimtune.core.scenario.ScenarioSchedule
import de.yuuto.aimtune.core.optimizer.OptimizationResult
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.units.AimStyle
import de.yuuto.aimtune.core.units.searchRange
import de.yuuto.aimtune.games.GameProfiles
import de.yuuto.aimtune.input.RawMouseSource
import de.yuuto.aimtune.storage.RawAccelExport
import de.yuuto.aimtune.storage.ResearchExportResult
import de.yuuto.aimtune.storage.SettingsException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * The services every screen shares, and the navigation between screens. Created once in
 * [AimTuneApp.create]; screens never construct each other.
 */
class AppContext(
    private val game: Game,
    val options: LaunchOptions,
    val ui: UiHolder,
    val profiles: GameProfiles,
    val settings: SettingsHolder,
    val processor: RoundProcessor,
    val source: RawMouseSource,
    val gate: MouseGate,
    /** Problems found at startup (unreadable settings or game files) to show on the main menu. */
    val startupProblems: List<String>,
    /** Where exported Raw Accel files go; null without a data folder. */
    val exportDir: Path? = null,
    /** Where research exports go; null without a data folder. */
    val researchDir: Path? = null,
) {
    private var nextSeed = options.seed ?: System.nanoTime()
    private val flashSoundsDelegate = lazy { FlashSounds() }
    private val hitSoundsDelegate = lazy { HitSounds() }

    /** The hit sound; created on first use for the same reason. */
    val hitSounds: HitSounds get() = hitSoundsDelegate.value

    /** The flash cue sounds; created on first use because loading them needs the audio device. */
    val flashSounds: FlashSounds get() = flashSoundsDelegate.value

    /** Frees what the audio device holds; call when the app closes. */
    fun disposeAudio() {
        if (flashSoundsDelegate.isInitialized()) flashSoundsDelegate.value.dispose()
        if (hitSoundsDelegate.isInitialized()) hitSoundsDelegate.value.dispose()
    }
    private var loadedI18n: I18n? = null
    private var languageOverride: Language? = null

    /** The texts in the language of the settings; reloaded when the language changes. */
    val i18n: I18n
        get() {
            val language = languageOverride ?: Language.fromCode(settings.current.language)
            return loadedI18n?.takeIf { it.language == language } ?: I18n.load(language).also { loadedI18n = it }
        }

    /** The user's confirmation, for this run, that Raw Accel is off or linear (it cannot be detected). */
    var rawAccelConfirmed = false

    val isSetUp: Boolean
        get() = SetupDraft.fromSettings(settings.current).let { draft -> SetupStep.entries.all { draft.issue(it, profiles) == null } }

    /** Config for a trainer session at the current sensitivity, or null until setup is complete. */
    fun trainerConfig(): TrainerConfig? = trainerConfigFor(settings.current, profiles, nextSeed.also { nextSeed++ })

    /** Shows the UI in [language] before it is saved (the settings screen); cleared when the menu or settings open again. */
    fun previewLanguage(language: Language) {
        languageOverride = language
    }

    fun showMenu() {
        languageOverride = null
        switchTo(MainMenuScreen(this))
    }

    /** Start test: onboarding on first use, then the pre-test notice. */
    fun showStart() = if (isSetUp) showPreTest() else showOnboarding(afterFinish = ::showPreTest)

    fun showOnboarding(startStep: SetupStep = SetupStep.DPI, afterFinish: () -> Unit = ::showMenu) =
        switchTo(OnboardingScreen(this, SetupDraft.fromSettings(settings.current), startStep, afterFinish))

    fun showPreTest() = switchTo(PreTestScreen(this))

    fun showSettings() {
        languageOverride = null
        if (isSetUp) switchTo(SettingsScreen(this)) else showOnboarding(afterFinish = ::showMenu)
    }

    fun showCrosshair() {
        languageOverride = null
        switchTo(CrosshairScreen(this))
    }

    /** Starts a new blind test run, merging the stored rounds of this game and DPI; onboarding first if needed. */
    fun startBlindTest() {
        val config = trainerConfig()
        val setup = settings.current
        val pad = setup.padWidthCm
        if (config == null || pad == null) return showOnboarding(afterFinish = ::showPreTest)
        val range = searchRange(setup.aimStyleOrNull() ?: AimStyle.MIXED, config.cm360, pad)
        val weights = metricWeights()
        val enabled = try {
            setup.enabledScenarios()
        } catch (e: SettingsException) {
            ScenarioKind.entries.toSet()
        }
        val past = processor.pastBlindRounds(setup.gameId, config.dpi)
        val seed = nextSeed++
        val test = BlindTest(range, weights, past, seed)
        val schedule = ScenarioSchedule(enabled, weights)
        showBlindRound(BlindRun(test, runKey = System.currentTimeMillis(), baseConfig = config, nextSeed = nextSeed++, schedule = schedule))
    }

    /** One blind round at the sensitivity the optimizer picked (hidden from the user). */
    fun showBlindRound(run: BlindRun) = switchTo(TrainerScreen(this, run.nextRoundConfig(), BlindSession(this, run)))

    fun showBetweenRounds(run: BlindRun) = switchTo(BetweenRoundsScreen(this, run))

    /** Ends the run and shows what it found; back to the menu when nothing was played or stored. */
    fun stopBlindTest(run: BlindRun) {
        val result = run.test.step().result
        val data = ResultsData(run.profile, run.dpi, run.currentCm360, result, run.test.totalRounds, run.test.sessionCount, run.test.scoredRounds(), run.test.axisSummary())
        run.test.close()
        if (run.test.totalRounds == 0) showMenu() else switchTo(ResultsScreen(this, data))
    }

    /** Results of the stored blind rounds, computed now; null until onboarding is complete. */
    fun showStoredResults() {
        val stored = storedAnalysis() ?: return showOnboarding(afterFinish = ::showMenu)
        val config = stored.config
        val data = ResultsData(
            config.profile, config.dpi, config.cm360, stored.result, stored.rounds.size, stored.rounds.map { it.sessionId }.distinct().size,
            BlindTest.scoreRounds(stored.weights, stored.rounds),
            AxisSummary.of(stored.rounds.map { it.axes }),
        )
        switchTo(ResultsScreen(this, data))
    }

    /** The stored blind rounds of the current game and DPI and what they say; null until onboarding is complete. */
    private fun storedAnalysis(): StoredAnalysis? {
        val config = trainerConfig() ?: return null
        val setup = settings.current
        val pad = setup.padWidthCm ?: return null
        val range = searchRange(setup.aimStyleOrNull() ?: AimStyle.MIXED, config.cm360, pad)
        val weights = metricWeights()
        val rounds = processor.pastBlindRounds(setup.gameId, config.dpi)
        return StoredAnalysis(config, weights, rounds, BlindTest.analyzeStored(range, weights, rounds))
    }

    private fun metricWeights(): MetricWeights = try {
        settings.current.toMetricWeights()
    } catch (e: SettingsException) {
        MetricWeights.DEFAULT
    }

    private class StoredAnalysis(val config: TrainerConfig, val weights: MetricWeights, val rounds: List<PastRound>, val result: OptimizationResult?)

    /** The sensitivity the acceleration steps start from: the optimizer's result over the stored blind rounds, if there is one. */
    fun accelBaseline(): AccelBaseline? {
        val stored = storedAnalysis() ?: return null
        val result = stored.result ?: return null
        return AccelBaseline(result.optimumCm360, result.confidencePercent, stored.rounds.size)
    }

    fun showAccel() = switchTo(AccelIntroScreen(this))

    /** The screen that exports the stored rounds for a researcher; the counts are read in the background. */
    fun showShareData() = switchTo(
        ShareDataScreen(this, CompletableFuture.supplyAsync { processor.storedHistory().let { StoredCounts(it.sessions.size, it.rounds.size) } }),
    )

    /** Writes the research file; fails with the reason when there is no data folder. */
    fun exportResearch(): CompletableFuture<Result<ResearchExportResult>> {
        val dir = researchDir ?: return CompletableFuture.completedFuture(Result.failure(IllegalStateException("no data folder to write to")))
        return processor.exportResearch(dir, APP_VERSION)
    }

    /** The history of stored sessions; the recommendation after each one is computed in the background. */
    fun showHistory() = switchTo(HistoryScreen(this, loadHistory()))

    private fun loadHistory(): CompletableFuture<List<HistoryEntry>> {
        val config = trainerConfig()
        val setup = settings.current
        val pad = setup.padWidthCm
        if (config == null || pad == null) return CompletableFuture.completedFuture(emptyList())
        val range = searchRange(setup.aimStyleOrNull() ?: AimStyle.MIXED, config.cm360, pad)
        val weights = metricWeights()
        return CompletableFuture.supplyAsync {
            val stored = processor.storedHistory()
            buildHistory(stored.sessions, stored.rounds, weights, range)
        }
    }

    /** Starts the acceleration steps at the recommended sensitivity; back to the intro if there is none yet. */
    fun startAccelRun(search: Boolean = false) {
        if (!source.hasReportTimestamps) return showAccel()
        val stored = storedAnalysis() ?: return showOnboarding(afterFinish = ::showMenu)
        val result = stored.result ?: return showAccel()
        val run = AccelRun(stored.config.copy(cm360 = result.optimumCm360), stored.weights, runKey = System.currentTimeMillis(), seed = nextSeed++, startWithSearch = search, flash = settings.current.flashImportance() != FlashImportance.NEVER)
        showAccelStep(run)
    }

    /** The screen that fits the run: a round while one is due, otherwise the analysis or the verdict. */
    fun showAccelStep(run: AccelRun) = switchTo(
        when (run.phase) {
            AccelPhase.CALIBRATION, AccelPhase.SEARCH, AccelPhase.TEST -> AccelBetweenScreen(this, run)
            AccelPhase.ANALYSIS -> AccelAnalysisScreen(this, run)
            AccelPhase.SEARCH_RESULT -> AccelSearchResultScreen(this, run)
            AccelPhase.DONE -> AccelResultScreen(this, run)
        },
    )

    fun showAccelRound(run: AccelRun) = switchTo(TrainerScreen(this, run.nextRoundConfig(), AccelSession(this, run)))

    /** Writes the Raw Accel settings.json of the tested curve; the path, or the reason it failed. */
    fun exportAccel(run: AccelRun): Result<Path> = runCatching {
        val candidate = checkNotNull(run.candidate) { "there is no curve to export" }
        val dir = checkNotNull(exportDir) { "no data folder to write to" }
        RawAccelExport.write(dir, candidate.curve, run.dpi, candidate.sensitivityFactor)
    }

    /** Number of stored blind rounds for the current setup, for the menu. */
    fun storedRoundCount(): Int {
        val config = trainerConfig() ?: return 0
        return processor.pastBlindRounds(settings.current.gameId, config.dpi).size
    }

    fun showTrainer() {
        val config = trainerConfig()
        if (config == null) showOnboarding(afterFinish = ::showPreTest) else switchTo(TrainerScreen(this, config))
    }

    /** Shows [screen] and disposes the one it replaces. */
    fun switchTo(screen: Screen) {
        val old = game.screen
        game.screen = screen
        old?.dispose()
    }

    /** Rebuilds the skin if the window size changed enough, and the current screen with it. */
    fun onResize() {
        val old = ui.updateFor(Gdx.graphics.backBufferHeight) ?: return
        (game.screen as? UiScreen)?.rebuild()
        old.dispose()
    }

    fun onWindowFocusLost() {
        (game.screen as? UiScreen)?.onWindowFocusLost()
        (game.screen as? TrainerScreen)?.onWindowFocusLost()
    }

    val currentScreen: Screen? get() = game.screen
}

/** The sensitivity the acceleration steps start from, with how sure the optimizer is about it. */
data class AccelBaseline(val cm360: Double, val confidencePercent: Int, val rounds: Int)

/** Written into research exports; keep in step with `appVersion` in app/build.gradle.kts. */
private const val APP_VERSION = "0.1.0"
