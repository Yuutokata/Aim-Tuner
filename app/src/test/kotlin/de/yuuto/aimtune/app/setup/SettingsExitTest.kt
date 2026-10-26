package de.yuuto.aimtune.app.setup

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsExitTest {
    @Test
    fun `leaving goes back to the screen that opened the settings`() {
        val calls = mutableListOf<String>()
        SettingsExit({ calls += "hub" }, { calls += "menu" }).leave()
        assertEquals(listOf("hub"), calls)
    }

    @Test
    fun `without a return target the menu opens as before`() {
        val calls = mutableListOf<String>()
        SettingsExit(null, { calls += "menu" }).leave()
        assertEquals(listOf("menu"), calls)
    }
}
