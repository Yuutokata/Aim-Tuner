package de.yuuto.aimtune.app

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaunchOptionsTest {
    private fun parse(vararg args: String) = parseLaunchOptions(arrayOf(*args))

    @Test
    fun `no arguments give the defaults`() {
        val o = parse()
        assertNull(o.seed)
        assertEquals(SourceKind.AUTO, o.source)
        assertTrue(o.vsync)
        assertNull(o.screenshotDir)
    }

    @Test
    fun `all options are parsed`() {
        val o = parse("--seed", "9", "--source", "glfw", "--no-vsync", "--screenshots", "out")
        assertEquals(9L, o.seed)
        assertEquals(SourceKind.GLFW, o.source)
        assertFalse(o.vsync)
        assertEquals(Paths.get("out"), o.screenshotDir)
    }

    @Test
    fun `invalid input is rejected with the option name`() {
        assertFailsWith<LaunchOptionsException> { parse("--seed", "abc") }
        assertFailsWith<LaunchOptionsException> { parse("--source", "x") }
        assertFailsWith<LaunchOptionsException> { parse("--seed") }
        val e = assertFailsWith<LaunchOptionsException> { parse("--dpi", "800") }
        assertTrue("--dpi" in e.message.orEmpty())
    }
}
