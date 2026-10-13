package de.yuuto.aimtune.core.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchRangeTest {
    @Test
    fun `styles give lower and narrower ranges for wrist than for arm`() {
        val wrist = searchRange(AimStyle.WRIST, currentCm360 = 28.0, padWidthCm = 90.0)
        val arm = searchRange(AimStyle.ARM, currentCm360 = 60.0, padWidthCm = 90.0)
        assertTrue(wrist.maxCm360 < arm.maxCm360)
        assertTrue(wrist.minCm360 < arm.minCm360)
        assertTrue(wrist.maxCm360 - wrist.minCm360 < arm.maxCm360 - arm.minCm360)
    }

    @Test
    fun `default table follows the onboarding mockup`() {
        val r = searchRange(AimStyle.MIXED, currentCm360 = 40.0, padWidthCm = 90.0)
        assertEquals(30.0, r.minCm360, 1e-9)
        assertEquals(50.0, r.maxCm360, 1e-9)
    }

    @Test
    fun `current value is always inside the range`() {
        for (style in AimStyle.entries) {
            for (current in listOf(8.0, 20.0, 41.0, 90.0, 200.0)) {
                val r = searchRange(style, current, padWidthCm = 45.0)
                assertTrue(current in r, "$style $current -> $r")
            }
        }
    }

    @Test
    fun `a narrow pad caps the upper bound`() {
        val big = searchRange(AimStyle.ARM, currentCm360 = 50.0, padWidthCm = 90.0)
        val small = searchRange(AimStyle.ARM, currentCm360 = 50.0, padWidthCm = 40.0)
        assertTrue(small.maxCm360 < big.maxCm360)
        assertTrue(small.maxCm360 <= 40.0 + 1e-9 || 50.0 in small)
    }

    @Test
    fun `range keeps a minimum width even when the pad cap is tight`() {
        val r = searchRange(AimStyle.ARM, currentCm360 = 46.0, padWidthCm = 30.0)
        assertTrue(r.maxCm360 / r.minCm360 >= SearchRange.MIN_RATIO - 1e-9)
    }

    @Test
    fun `a current value beyond the pad is still included and flagged as not fitting`() {
        assertFalse(doesTurnFitPad(cm360 = 60.0, padWidthCm = 45.0))
        assertTrue(doesTurnFitPad(cm360 = 43.3, padWidthCm = 45.0))
        assertTrue(60.0 in searchRange(AimStyle.MIXED, currentCm360 = 60.0, padWidthCm = 45.0))
    }

    @Test
    fun `the table is configurable`() {
        val table = SearchRangeTable(mapOf(AimStyle.WRIST to (10.0 to 20.0), AimStyle.MIXED to (20.0 to 40.0), AimStyle.ARM to (40.0 to 80.0)))
        val r = searchRange(AimStyle.WRIST, currentCm360 = 15.0, padWidthCm = 90.0, table = table)
        assertEquals(10.0, r.minCm360, 1e-9)
        assertEquals(20.0, r.maxCm360, 1e-9)
    }

    @Test
    fun `invalid input is rejected`() {
        assertFailsWith<IllegalArgumentException> { searchRange(AimStyle.MIXED, 0.0, 45.0) }
        assertFailsWith<IllegalArgumentException> { searchRange(AimStyle.MIXED, 40.0, -1.0) }
        assertFailsWith<IllegalArgumentException> { SearchRangeTable(mapOf(AimStyle.WRIST to (20.0 to 10.0))) }
    }

    @Test
    fun `log midpoint lies between the bounds`() {
        val r = SearchRange(20.0, 80.0)
        assertEquals(40.0, r.geometricMid, 1e-9)
    }
}
