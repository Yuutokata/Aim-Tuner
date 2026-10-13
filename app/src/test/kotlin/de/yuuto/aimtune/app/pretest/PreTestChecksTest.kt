package de.yuuto.aimtune.app.pretest

import de.yuuto.aimtune.input.windows.PointerAcceleration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreTestChecksTest {
    private fun rows(acceleration: PointerAcceleration?, confirmed: Boolean = false, raw: Boolean = true) =
        PreTestChecks.rows(acceleration, confirmed, "GLFW raw motion (fallback)", raw).associateBy { it.id }

    @Test
    fun `there is one row each for pointer acceleration raw accel and input`() {
        assertEquals(listOf("pointer", "rawaccel", "input"), PreTestChecks.rows(PointerAcceleration(0), false, "x", true).map { it.id })
    }

    @Test
    fun `pointer acceleration off is fine and on needs attention`() {
        assertEquals(CheckLevel.OK, rows(PointerAcceleration(0)).getValue("pointer").level)
        assertEquals("OFF", rows(PointerAcceleration(0)).getValue("pointer").state)
        val on = rows(PointerAcceleration(1)).getValue("pointer")
        assertEquals(CheckLevel.ATTENTION, on.level)
        assertEquals("ON", on.state)
        assertTrue("Enhance pointer precision" in on.detail)
    }

    @Test
    fun `an unreadable pointer setting is unknown, not an error`() {
        assertEquals(CheckLevel.UNKNOWN, rows(null).getValue("pointer").level)
    }

    @Test
    fun `raw accel needs a manual confirmation and says why`() {
        val open = rows(PointerAcceleration(0)).getValue("rawaccel")
        assertEquals(CheckLevel.ATTENTION, open.level)
        assertTrue("Cannot be detected" in open.detail)
        assertEquals(CheckLevel.OK, rows(PointerAcceleration(0), confirmed = true).getValue("rawaccel").level)
    }

    @Test
    fun `the fallback input source is flagged`() {
        assertEquals("RAW", rows(PointerAcceleration(0)).getValue("input").state)
        val fallback = rows(PointerAcceleration(0), raw = false).getValue("input")
        assertEquals(CheckLevel.ATTENTION, fallback.level)
        assertTrue("GLFW" in fallback.detail)
    }
}
