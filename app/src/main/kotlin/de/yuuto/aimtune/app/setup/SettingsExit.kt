package de.yuuto.aimtune.app.setup

/**
 * Where the settings screen goes when it is left, by Save, Back or Escape: back to the screen that opened it ([returnTo], e.g. the
 * Raw Accel hub) or to the main menu.
 */
class SettingsExit(val returnTo: (() -> Unit)?, private val menu: () -> Unit) {
    fun leave() = (returnTo ?: menu)()
}
