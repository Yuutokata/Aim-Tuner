package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.core.accel.AccelFit
import de.yuuto.aimtune.core.accel.AccelSpec
import de.yuuto.aimtune.core.accel.CurveStrength
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.accel.NoAccel
import de.yuuto.aimtune.core.accel.strengthAccel
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.storage.RoundRow
import de.yuuto.aimtune.storage.SessionKind
import de.yuuto.aimtune.storage.scoreInputs
import kotlin.math.abs

/** What a stored blind strength search found, computed again from its rounds. [offer] is the curve when the search excluded "no curve". */
data class LastSearch(val dpi: Double, val cm360: Double, val rounds: Int, val result: StrengthResult?) {
    val offer: AccelFit? get() = result?.takeIf { it.excludesPlain }?.let { strengthAccel(it.strength) }
}

/**
 * Finds the rounds of a blind strength search among stored rounds and runs the search's analysis on them again. A round
 * stores the curve it was played with, and the search's curves are natural curves with a fixed decay whose limit is the
 * strength, so the strength can be read back from the stored round.
 */
object StoredSearches {
    private const val DECAY_TOLERANCE = 1e-9
    private const val SAME_STRENGTH = 1e-9
    private const val MIN_DISTINCT_STRENGTHS = 4

    /** The strength a round was played with, or null when its curve is not one of the search's (e.g. a fitted curve of the error analysis). */
    fun strengthOf(accel: String?): Double? {
        if (accel == null) return CurveStrength.NEUTRAL
        val curve = try {
            AccelSpec.decode(accel)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return when {
            curve === NoAccel -> CurveStrength.NEUTRAL
            curve is NaturalCurve && abs(curve.decayRate - CurveStrength.DECAY) < DECAY_TOLERANCE && curve.inputOffset == 0.0 -> curve.limit
            else -> null
        }
    }

    /**
     * The search rounds of one stored session, oldest first. A session of the search kind is all search. Older sessions of
     * kind `accel_ab` hold the search and the blind test that followed it in one list: the test is the end of the session
     * where rounds come in pairs of one plain and one curve round with the same strength (the search varies the strength),
     * and what is before it is the search, if it has at least [StrengthSearch.MIN_ROUNDS] rounds and several strengths.
     */
    fun searchRounds(kind: SessionKind, rounds: List<RoundRow>): List<RoundRow> = when (kind) {
        SessionKind.ACCEL_SEARCH -> rounds
        SessionKind.ACCEL_AB -> legacySearchRounds(rounds)
        SessionKind.SENSITIVITY -> emptyList()
    }

    private fun legacySearchRounds(rounds: List<RoundRow>): List<RoundRow> {
        val strengths = rounds.map { strengthOf(it.accel) }
        val usable = strengths.indexOfFirst { it == null }.let { if (it < 0) rounds.size else it }
        val values = strengths.take(usable).map { checkNotNull(it) }
        // A test that was stopped inside a pair leaves one round over, so the pairs may start one round before the end.
        val oddTail = testStart(values, values.size - 1).takeIf { it < values.size - 1 } ?: values.size
        val searchEnd = minOf(testStart(values, values.size), oddTail)
        val search = rounds.take(searchEnd)
        val distinct = values.take(searchEnd).filter(CurveStrength::isCurve).map { Math.round(it * 1000) }.toSet().size
        return if (search.size >= StrengthSearch.MIN_ROUNDS && distinct >= MIN_DISTINCT_STRENGTHS) search else emptyList()
    }

    /** Where the pairs of one plain and one curve round with one strength, going back from [end], begin. */
    private fun testStart(strengths: List<Double>, end: Int): Int {
        var i = end
        var curve: Double? = null
        while (i >= 2) {
            val a = strengths[i - 2]
            val b = strengths[i - 1]
            if (CurveStrength.isCurve(a) == CurveStrength.isCurve(b)) break
            val strength = if (CurveStrength.isCurve(a)) a else b
            if (curve != null && abs(strength - curve) > SAME_STRENGTH) break
            curve = strength
            i -= 2
        }
        return i
    }

    /** The result of the search over [rounds] (non-empty, from [searchRounds]) played with [dpi]. */
    fun summarize(dpi: Double, rounds: List<RoundRow>, weights: MetricWeights): LastSearch {
        val search = StrengthSearch(weights, seed = 0L, plannedRounds = rounds.size)
        rounds.forEach { search.record(checkNotNull(strengthOf(it.accel)), it.scenario, it.scoreInputs()) }
        return LastSearch(dpi, rounds.first().cm360, rounds.size, search.result())
    }
}
