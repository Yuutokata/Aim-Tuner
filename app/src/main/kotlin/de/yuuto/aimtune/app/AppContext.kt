package de.yuuto.aimtune.app

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import de.yuuto.aimtune.app.blind.BlindRun
import de.yuuto.aimtune.app.blind.BlindTest
import de.yuuto.aimtune.app.blind.PastRound
import de.yuuto.aimtune.app.screens.BetweenRoundsScreen
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
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.units.AimStyle
import de.yuuto.aimtune.core.units.searchRange
import de.yuuto.aimtune.games.GameProfiles
import de.yuuto.aimtune.input.RawMouseSource
import de.yuuto.aimtune.storage.SettingsException

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
) {
    private var nextSeed = options.seed ?: System.nanoTime()

    /** The user's confirmation, for this run, that Raw Accel is off or linear (it cannot be detected). */
    var rawAccelConfirmed = false

    val isSetUp: Boolean
        get() = SetupDraft.fromSettings(settings.current).let { draft -> SetupStep.entries.all { draft.issue(it, profiles) == null } }

    /** Config for a trainer session at the current sensitivity, or null until setup is complete. */
    fun trainerConfig(): TrainerConfig? = trainerConfigFor(settings.current, profiles, nextSeed.also { nextSeed++ })

    fun showMenu() = switchTo(MainMenuScreen(this))

    /** Start test: onboarding on first use, then the pre-test notice. */
    fun showStart() = if (isSetUp) showPreTest() else showOnboarding(afterFinish = ::showPreTest)

    fun showOnboarding(startStep: SetupStep = SetupStep.DPI, afterFinish: () -> Unit = ::showMenu) =
        switchTo(OnboardingScreen(this, SetupDraft.fromSettings(settings.current), startStep, afterFinish))

    fun showPreTest() = switchTo(PreTestScreen(this))

    fun showSettings() = if (isSetUp) switchTo(SettingsScreen(this)) else showOnboarding(afterFinish = ::showMenu)

    /** Starts a new blind test run, merging the stored rounds of this game and DPI; onboarding first if needed. */
    fun startBlindTest() {
        val config = trainerConfig()
        val setup = settings.current
        val pad = setup.padWidthCm
        if (config == null || pad == null) return showOnboarding(afterFinish = ::showPreTest)
        val range = searchRange(setup.aimStyleOrNull() ?: AimStyle.MIXED, config.cm360, pad)
        val weights = try {
            setup.toMetricWeights()
        } catch (e: SettingsException) {
            MetricWeights.DEFAULT
        }
        val past = processor.pastBlindRounds(setup.gameId, config.dpi)
        val seed = nextSeed++
        val test = BlindTest(range, weights, past, seed)
        showBlindRound(BlindRun(test, runKey = System.currentTimeMillis(), baseConfig = config, nextSeed = nextSeed++))
    }

    /** One blind round at the sensitivity the optimizer picked (hidden from the user). */
    fun showBlindRound(run: BlindRun) = switchTo(TrainerScreen(this, run.nextRoundConfig(), run))

    fun showBetweenRounds(run: BlindRun) = switchTo(BetweenRoundsScreen(this, run))

    /** Ends the run and shows what it found; back to the menu when nothing was played or stored. */
    fun stopBlindTest(run: BlindRun) {
        val result = run.test.step().result
        val data = ResultsData(run.profile, run.dpi, run.currentCm360, result, run.test.totalRounds, run.test.sessionCount)
        run.test.close()
        if (run.test.totalRounds == 0) showMenu() else switchTo(ResultsScreen(this, data))
    }

    /** Results of the stored blind rounds, computed now; null until onboarding is complete. */
    fun showStoredResults() {
        val config = trainerConfig() ?: return showOnboarding(afterFinish = ::showMenu)
        val setup = settings.current
        val pad = setup.padWidthCm ?: return showOnboarding(afterFinish = ::showMenu)
        val range = searchRange(setup.aimStyleOrNull() ?: AimStyle.MIXED, config.cm360, pad)
        val weights = try {
            setup.toMetricWeights()
        } catch (e: SettingsException) {
            MetricWeights.DEFAULT
        }
        val rounds: List<PastRound> = processor.pastBlindRounds(setup.gameId, config.dpi)
        val result = BlindTest.analyzeStored(range, weights, rounds)
        val data = ResultsData(config.profile, config.dpi, config.cm360, result, rounds.size, rounds.map { it.sessionId }.distinct().size)
        switchTo(ResultsScreen(this, data))
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
