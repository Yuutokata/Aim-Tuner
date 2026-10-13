package de.yuuto.aimtune.app

import de.yuuto.aimtune.app.setup.SetupDraft
import de.yuuto.aimtune.app.setup.SetupStep
import de.yuuto.aimtune.games.GameProfile
import de.yuuto.aimtune.games.GameProfiles
import de.yuuto.aimtune.storage.AppSettings

/** Everything one trainer session needs; [cm360] is the fixed sensitivity of the rounds until the optimizer (M5) varies it. */
data class TrainerConfig(
    val dpi: Double,
    val cm360: Double,
    val profile: GameProfile,
    val roundSeconds: Int,
    val seed: Long,
)

/** The trainer config for saved [settings], or null while onboarding is incomplete. */
fun trainerConfigFor(settings: AppSettings, profiles: GameProfiles, seed: Long): TrainerConfig? {
    val draft = SetupDraft.fromSettings(settings)
    if (SetupStep.entries.any { draft.issue(it, profiles) != null }) return null
    return TrainerConfig(
        dpi = checkNotNull(draft.dpi),
        cm360 = checkNotNull(draft.currentCm360(profiles)),
        profile = checkNotNull(draft.profile(profiles)),
        roundSeconds = settings.roundSeconds,
        seed = seed,
    )
}
