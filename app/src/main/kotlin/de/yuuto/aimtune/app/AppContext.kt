package de.yuuto.aimtune.app

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import de.yuuto.aimtune.app.screens.MainMenuScreen
import de.yuuto.aimtune.app.screens.OnboardingScreen
import de.yuuto.aimtune.app.screens.PreTestScreen
import de.yuuto.aimtune.app.screens.SettingsScreen
import de.yuuto.aimtune.app.screens.TrainerScreen
import de.yuuto.aimtune.app.screens.UiScreen
import de.yuuto.aimtune.app.setup.SetupDraft
import de.yuuto.aimtune.app.setup.SetupStep
import de.yuuto.aimtune.app.ui.UiHolder
import de.yuuto.aimtune.games.GameProfiles
import de.yuuto.aimtune.input.RawMouseSource

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
