package de.yuuto.aimtune.app

import de.yuuto.aimtune.core.units.FovType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LaunchOptionsTest {
    private fun parse(vararg args: String) = parseLaunchOptions(arrayOf(*args)) { 123L }

    @Test
    fun `parses required options and applies documented defaults`() {
        val o = parse("--dpi", "800", "--cm360", "40.5", "--fov", "103")
        assertEquals(800.0, o.dpi)
        assertEquals(40.5, o.cm360)
        assertEquals(103.0, o.fov.degrees)
        assertEquals(FovType.HORIZONTAL, o.fov.type)
        assertEquals(45, o.roundSeconds)
        assertEquals(123L, o.seed)
        assertEquals(SourceKind.AUTO, o.source)
        assertEquals(16.0 / 9.0, o.fovReferenceAspect)
        assert(o.vsync)
    }

    @Test
    fun `parses optional values and flags`() {
        val o = parse(
            "--dpi", "1600", "--cm360", "30", "--fov", "90", "--fov-type", "vertical", "--fov-aspect", "1.6",
            "--duration", "30", "--seed", "9", "--source", "glfw", "--no-vsync",
        )
        assertEquals(FovType.VERTICAL, o.fov.type)
        assertEquals(1.6, o.fovReferenceAspect)
        assertEquals(30, o.roundSeconds)
        assertEquals(9L, o.seed)
        assertEquals(SourceKind.GLFW, o.source)
        assertFalse(o.vsync)
    }

    @Test
    fun `missing required option is reported by name`() {
        val e = assertFailsWith<LaunchOptionsException> { parse("--dpi", "800", "--fov", "103") }
        assert("--cm360" in e.message.orEmpty())
    }

    @Test
    fun `rejects invalid values`() {
        assertFailsWith<LaunchOptionsException> { parse("--dpi", "abc", "--cm360", "40", "--fov", "103") }
        assertFailsWith<LaunchOptionsException> { parse("--dpi", "-1", "--cm360", "40", "--fov", "103") }
        assertFailsWith<LaunchOptionsException> { parse("--dpi", "800", "--cm360", "40", "--fov", "200") }
        assertFailsWith<LaunchOptionsException> { parse("--dpi", "800", "--cm360", "40", "--fov", "90", "--source", "x") }
        assertFailsWith<LaunchOptionsException> { parse("--dpi", "800", "--cm360", "40", "--fov", "90", "--bogus", "1") }
        assertFailsWith<LaunchOptionsException> { parse("--dpi", "800", "--cm360") }
    }
}
