package de.yuuto.aimtune.core.accel

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RefineParameterTest {
    private val speeds = listOf(0.5, 5.0, 10.0, 15.0, 25.0, 40.0, 62.5, 100.0, 300.0)

    private fun profile(args: RawAccelArgs, outputDpi: Double = 600.0, deviceDpi: Double = 800.0) = RawAccelProfile("p", args, outputDpi, deviceDpi)

    private val user = profile(RawAccelArgs(RawAccelMode.CLASSIC, gain = true, inputOffset = 10.0, capX = 62.5, capY = 1.6, capMode = RawAccelCapMode.IN_OUT))
    private val classicOut = profile(RawAccelArgs(RawAccelMode.CLASSIC, gain = false, acceleration = 0.03, inputOffset = 1.0, capY = 1.5, capMode = RawAccelCapMode.OUTPUT))
    private val classicUncapped = profile(RawAccelArgs(RawAccelMode.CLASSIC, gain = true, acceleration = 0.004, capX = 0.0, capY = 0.0, capMode = RawAccelCapMode.OUTPUT))
    private val classicIn = profile(RawAccelArgs(RawAccelMode.CLASSIC, gain = false, acceleration = 0.03, inputOffset = 2.0, capX = 30.0, capMode = RawAccelCapMode.INPUT))
    private val naturalRising = profile(RawAccelArgs(RawAccelMode.NATURAL, gain = false, decayRate = 0.15, limit = 2.0, inputOffset = 2.0))
    private val naturalFalling = profile(RawAccelArgs(RawAccelMode.NATURAL, gain = true, decayRate = 0.1, limit = 0.6))
    private val lut = profile(RawAccelArgs(RawAccelMode.LUT, gain = false, lut = listOf(2f, 0.9f, 5f, 1.0f, 30f, 1.6f)))
    private val noAccel = profile(RawAccelArgs(RawAccelMode.NOACCEL))
    private val all = listOf(user, classicOut, classicUncapped, classicIn, naturalRising, naturalFalling, lut, noAccel)

    private fun RawAccelProfile.curveAt(v: Double) = curve.multiplier(v)

    @Test
    fun `a factor of one is the profile itself`() {
        for (p in all) for (parameter in RefineParameter.entries.filter { p.supports(it) }) assertSame(p, p.adjusted(parameter, 1.0), "$parameter of ${p.args.mode}")
    }

    @Test
    fun `every supported parameter stays valid over the whole searched range`() {
        for (p in all) for (parameter in RefineParameter.entries.filter { p.supports(it) }) {
            for (i in 0..20) {
                val factor = parameter.min + (parameter.max - parameter.min) * i / 20
                val adjusted = p.adjusted(parameter, factor) // RawAccelArgs refuses what Raw Accel's validation refuses
                for (v in speeds) assertTrue(adjusted.curveAt(v).isFinite() && adjusted.curveAt(v) > 0, "$parameter $factor at $v in ${p.args.mode}")
            }
        }
    }

    @Test
    fun `lut and noaccel only support the level`() {
        for (p in listOf(lut, noAccel)) {
            assertTrue(p.supports(RefineParameter.LEVEL))
            assertFalse(p.supports(RefineParameter.HIGH_GAIN))
            assertFalse(p.supports(RefineParameter.TRANSITION))
            assertFailsWith<IllegalArgumentException> { p.adjusted(RefineParameter.HIGH_GAIN, 1.1) }
        }
    }

    @Test
    fun `level scales the output dpi and leaves the curve alone`() {
        val adjusted = user.adjusted(RefineParameter.LEVEL, 1.15)
        assertEquals(690.0, adjusted.outputDpi, 1e-9)
        assertEquals(user.args, adjusted.args)
        assertEquals(user.totalMultiplier(30.0) * 1.15, adjusted.totalMultiplier(30.0), 1e-12)
        assertEquals(user.deviceDpi, adjusted.deviceDpi)
    }

    @Test
    fun `high gain scales the capped output of a classic profile`() {
        val adjusted = user.adjusted(RefineParameter.HIGH_GAIN, 1.25)
        assertEquals(1.0 + 0.6 * 1.25, adjusted.args.capY, 1e-12)
        assertEquals(user.args.capX, adjusted.args.capX)
        // The speed the cap sits at, and everything above it, carries the scaled gain; below the offset nothing changes.
        assertEquals(1.0, adjusted.curveAt(5.0), 1e-12)
        assertTrue(adjusted.curveAt(300.0) > user.curveAt(300.0))
        val falling = classicOut.copy(args = classicOut.args.copy(capY = 0.7))
        assertEquals(1.0 - 0.3 * 0.75, falling.adjusted(RefineParameter.HIGH_GAIN, 0.75).args.capY, 1e-12)
    }

    @Test
    fun `high gain of a velocity classic curve with an output cap scales the plateau`() {
        val plateau = classicOut.curveAt(400.0) - 1.0
        assertEquals(plateau * 1.3, classicOut.adjusted(RefineParameter.HIGH_GAIN, 1.3).curveAt(400.0) - 1.0, 1e-12)
    }

    @Test
    fun `high gain without a cap output scales the acceleration so that the output at the cap scales`() {
        // input cap: the plateau is the output at capX, which is proportional to acceleration^(exponent - 1)
        val plateau = classicIn.curveAt(100.0) - 1.0
        assertEquals(plateau * 0.8, classicIn.adjusted(RefineParameter.HIGH_GAIN, 0.8).curveAt(100.0) - 1.0, 1e-9)
        assertTrue(classicUncapped.adjusted(RefineParameter.HIGH_GAIN, 1.2).args.acceleration > classicUncapped.args.acceleration)
    }

    @Test
    fun `high gain of a natural profile scales the limit and keeps the speed of the rise`() {
        val adjusted = naturalRising.adjusted(RefineParameter.HIGH_GAIN, 1.25)
        assertEquals(1.0 + 1.0 * 1.25, adjusted.args.limit, 1e-12)
        val before = naturalRising.args.decayRate / (naturalRising.args.limit - 1.0)
        assertEquals(before, adjusted.args.decayRate / (adjusted.args.limit - 1.0), 1e-12)
        // the same relative position on the way to the limit at every speed
        for (v in listOf(5.0, 10.0, 25.0)) {
            val share = (naturalRising.curveAt(v) - 1.0) / (naturalRising.args.limit - 1.0)
            assertEquals(share, (adjusted.curveAt(v) - 1.0) / (adjusted.args.limit - 1.0), 1e-9)
        }
        val falling = naturalFalling.adjusted(RefineParameter.HIGH_GAIN, 1.33)
        assertTrue(falling.args.limit < naturalFalling.args.limit && falling.args.limit > 0.0)
    }

    @Test
    fun `a limit or cap output scaled towards zero stays above the floor`() {
        val steep = profile(RawAccelArgs(RawAccelMode.NATURAL, limit = 0.1))
        assertTrue(steep.adjusted(RefineParameter.HIGH_GAIN, 1.33).args.limit >= 0.05)
        val capLow = profile(RawAccelArgs(RawAccelMode.CLASSIC, gain = true, inputOffset = 2.0, capX = 30.0, capY = 0.1, capMode = RawAccelCapMode.IN_OUT))
        assertTrue(capLow.adjusted(RefineParameter.HIGH_GAIN, 1.33).args.capY >= 0.05)
    }

    @Test
    fun `transition moves the offset of a classic curve and the cap point with it when it grows`() {
        val later = user.adjusted(RefineParameter.TRANSITION, 2.0)
        assertEquals(20.0, later.args.inputOffset, 1e-12)
        assertEquals(125.0, later.args.capX, 1e-12)
        val earlier = user.adjusted(RefineParameter.TRANSITION, 0.5)
        assertEquals(5.0, earlier.args.inputOffset, 1e-12)
        assertEquals(62.5, earlier.args.capX, 1e-12)
        assertTrue(earlier.curveAt(8.0) > user.curveAt(8.0), "an earlier start rises sooner")
        assertTrue(abs(later.curveAt(8.0) - 1.0) < 1e-12)
    }

    @Test
    fun `transition of a natural profile divides the decay rate`() {
        assertEquals(0.075, naturalRising.adjusted(RefineParameter.TRANSITION, 2.0).args.decayRate, 1e-12)
        assertEquals(0.3, naturalRising.adjusted(RefineParameter.TRANSITION, 0.5).args.decayRate, 1e-12)
        assertTrue(naturalRising.adjusted(RefineParameter.TRANSITION, 2.0).curveAt(10.0) < naturalRising.curveAt(10.0), "a slower approach is lower at moderate speed")
    }

    @Test
    fun `a classic curve without an offset has no transition and an unchanged high gain is skipped`() {
        assertFalse(classicUncapped.supports(RefineParameter.TRANSITION))
        val flat = profile(RawAccelArgs(RawAccelMode.CLASSIC, gain = true, inputOffset = 2.0, capX = 30.0, capY = 1.0, capMode = RawAccelCapMode.IN_OUT))
        assertFalse(flat.supports(RefineParameter.HIGH_GAIN))
        assertFalse(profile(RawAccelArgs(RawAccelMode.NATURAL, limit = 1.0)).supports(RefineParameter.HIGH_GAIN))
    }

    @Test
    fun `several factors apply in the order of the parameters and skip unsupported ones`() {
        val adjusted = user.adjusted(mapOf(RefineParameter.TRANSITION to 2.0, RefineParameter.LEVEL to 0.9, RefineParameter.HIGH_GAIN to 1.2))
        assertEquals(540.0, adjusted.outputDpi, 1e-9)
        assertEquals(1.72, adjusted.args.capY, 1e-12)
        assertEquals(20.0, adjusted.args.inputOffset, 1e-12)
        assertSame(lut, lut.adjusted(mapOf(RefineParameter.HIGH_GAIN to 1.2)))
        assertEquals(lut.copy(outputDpi = lut.outputDpi * 1.1), lut.adjusted(mapOf(RefineParameter.HIGH_GAIN to 1.2, RefineParameter.LEVEL to 1.1)))
    }

    @Test
    fun `the played arm sits at the game sensitivity divided by the output dpi factor`() {
        val played = user.played(48.0)
        assertEquals(48.0 / 0.75, played.cm360, 1e-12)
        assertEquals(user.curve, played.curve)
        assertEquals(48.0 / 0.75 / 1.15, user.adjusted(RefineParameter.LEVEL, 1.15).played(48.0).cm360, 1e-12)
    }

    @Test
    fun `excluding the neutral value needs a real change and a range on one side of one`() {
        val p = RefineParameter.LEVEL
        assertTrue(p.excludesNeutral(1.1, 1.04, 1.16))
        assertTrue(p.excludesNeutral(0.9, 0.86, 0.96))
        assertFalse(p.excludesNeutral(1.1, 0.95, 1.16))
        assertFalse(p.excludesNeutral(1.01, 1.005, 1.02), "within the neutral band")
    }
}
