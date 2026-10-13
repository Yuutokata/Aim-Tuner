package de.yuuto.aimtune.core.units

import kotlin.math.sqrt

/** How the player moves the mouse during a flick. */
enum class AimStyle { WRIST, MIXED, ARM }

/** The cm/360° interval the optimizer searches. */
data class SearchRange(val minCm360: Double, val maxCm360: Double) {
    init {
        require(minCm360 > 0.0 && maxCm360 > minCm360) { "need 0 < min < max, was $minCm360..$maxCm360" }
    }

    operator fun contains(cm360: Double): Boolean = cm360 in minCm360..maxCm360

    /** Midpoint on the log scale the optimizer works on. */
    val geometricMid: Double get() = sqrt(minCm360 * maxCm360)

    companion object {
        /** The range never gets narrower than this ratio max/min, so there is always something to compare. */
        const val MIN_RATIO = 1.3
    }
}

/** Typical cm/360° window per aim style. Kept as data so it can be tuned or loaded from config later. */
class SearchRangeTable(val ranges: Map<AimStyle, Pair<Double, Double>>) {
    init {
        require(ranges.keys.containsAll(AimStyle.entries)) { "table needs an entry for every aim style" }
        require(ranges.values.all { (lo, hi) -> lo > 0.0 && hi > lo }) { "each entry needs 0 < min < max" }
    }

    companion object {
        /** Initial values from the onboarding mockup: wrist 20-35, mixed 30-50, arm 45-80 cm/360°. */
        val DEFAULT = SearchRangeTable(
            mapOf(AimStyle.WRIST to (20.0 to 35.0), AimStyle.MIXED to (30.0 to 50.0), AimStyle.ARM to (45.0 to 80.0)),
        )
    }
}

private const val CURRENT_VALUE_MARGIN = 1.1

/** A full turn must fit within the pad width (the player can still lift and reposition for more). */
fun doesTurnFitPad(cm360: Double, padWidthCm: Double): Boolean = cm360 <= padWidthCm

/**
 * The search range for an aim style: the table window, with its upper bound capped by the pad width
 * and kept at least [SearchRange.MIN_RATIO] wide, then widened if needed so [currentCm360] lies
 * strictly inside (the current setting is always a valid candidate, even when it does not fit the pad).
 */
fun searchRange(
    style: AimStyle,
    currentCm360: Double,
    padWidthCm: Double,
    table: SearchRangeTable = SearchRangeTable.DEFAULT,
): SearchRange {
    require(currentCm360.isFinite() && currentCm360 > 0.0) { "currentCm360 must be positive, was $currentCm360" }
    require(padWidthCm.isFinite() && padWidthCm > 0.0) { "padWidthCm must be positive, was $padWidthCm" }
    var (lo, hi) = table.ranges.getValue(style)
    hi = minOf(hi, padWidthCm)
    if (hi < lo * SearchRange.MIN_RATIO) lo = hi / SearchRange.MIN_RATIO
    if (currentCm360 <= lo) lo = currentCm360 / CURRENT_VALUE_MARGIN
    if (currentCm360 >= hi) hi = currentCm360 * CURRENT_VALUE_MARGIN
    return SearchRange(lo, hi)
}
