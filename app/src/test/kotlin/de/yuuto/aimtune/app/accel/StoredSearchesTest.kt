package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.RoundProcessor
import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.core.accel.AccelSpec
import de.yuuto.aimtune.core.accel.CurveStrength
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.games.GameProfiles
import de.yuuto.aimtune.storage.Database
import de.yuuto.aimtune.storage.RoundRow
import de.yuuto.aimtune.storage.SessionKind
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredSearchesTest {
    @TempDir
    lateinit var dir: Path

    private fun row(id: Long, strength: Double?, accel: String? = strength?.let { s -> CurveStrength.curve(s)?.let(AccelSpec::encode) }) =
        RoundRow(id, 1, "flick", id, 34.4, true, id, emptyMap(), accel)

    /** Strengths of a search that wanders, so no two rounds are alike. */
    private fun searchStrengths(n: Int) = (0 until n).map { 0.75 + 0.02 * it }

    private fun rows(strengths: List<Double>) = strengths.mapIndexed { i, s -> row(i.toLong(), s) }

    /** Pairs of one plain and one curve round with one strength, the order alternating like the blind test's. */
    private fun testPairs(strength: Double, pairs: Int) = (0 until pairs).flatMap { if (it % 2 == 0) listOf(1.0, strength) else listOf(strength, 1.0) }

    @Test
    fun `the strength is read back from the stored curve`() {
        assertEquals(1.0, StoredSearches.strengthOf(null))
        assertEquals(0.8, StoredSearches.strengthOf(AccelSpec.encode(NaturalCurve(CurveStrength.DECAY, 0.8))))
        assertNull(StoredSearches.strengthOf(AccelSpec.encode(NaturalCurve(0.3, 0.8))), "another decay is a fitted curve, not the search's")
        assertNull(StoredSearches.strengthOf("garbage"))
    }

    @Test
    fun `a session of the search kind is all search`() {
        val rounds = rows(searchStrengths(5))
        assertEquals(rounds, StoredSearches.searchRounds(SessionKind.ACCEL_SEARCH, rounds))
        assertTrue(StoredSearches.searchRounds(SessionKind.SENSITIVITY, rounds).isEmpty())
    }

    @Test
    fun `an older session holds the search followed by the blind test, and only the search is taken`() {
        val search = searchStrengths(24)
        val all = rows(search + testPairs(0.7, 8))
        assertEquals(24, StoredSearches.searchRounds(SessionKind.ACCEL_AB, all).size)
        val stoppedInsidePair = rows(search + testPairs(0.7, 8) + listOf(1.0))
        assertEquals(24, StoredSearches.searchRounds(SessionKind.ACCEL_AB, stoppedInsidePair).size)
        assertEquals(18, StoredSearches.searchRounds(SessionKind.ACCEL_AB, rows(searchStrengths(18))).size, "a search without a test is all search")
    }

    @Test
    fun `calibration, short and test-only sessions are no search`() {
        assertTrue(StoredSearches.searchRounds(SessionKind.ACCEL_AB, rows(List(15) { 1.0 })).isEmpty(), "plain rounds only")
        assertTrue(StoredSearches.searchRounds(SessionKind.ACCEL_AB, rows(searchStrengths(10))).isEmpty(), "fewer rounds than a search may end with")
        assertTrue(StoredSearches.searchRounds(SessionKind.ACCEL_AB, rows(testPairs(0.7, 10))).isEmpty(), "one strength only")
        val fitted = rows(List(10) { 1.0 }) + (10 until 30).map { row(it.toLong(), null, AccelSpec.encode(NaturalCurve(0.3, 0.8))) }
        assertTrue(StoredSearches.searchRounds(SessionKind.ACCEL_AB, fitted).isEmpty(), "a test of a fitted curve")
    }

    @Test
    fun `the stored search gives the strength the live search found`() {
        val config = TrainerConfig(800.0, 34.4, checkNotNull(GameProfiles.load().find("valorant")), roundSeconds = 30, seed = 0)
        val run = AccelRun(config, MetricWeights.DEFAULT, runKey = 1, seed = 5, startWithSearch = true)
        val processor = RoundProcessor(Database.open(dir.resolve("a.db")))
        var at = 0L
        while (run.phase == AccelPhase.SEARCH) {
            val next = run.nextRoundConfig()
            val round = HeadlessRound.of(next.scenario, fallingGainPlayer, next.dpi, next.cm360, next.seed, next.roundSeconds, accel = next.accel)
            processor.submit(round, startedAtMs = ++at, blind = true, gameId = "valorant", runKey = 1, kind = SessionKind.ACCEL_SEARCH)
            run.recordRound(round)
        }
        val live = assertNotNull(run.searchResult)

        val stored = assertNotNull(processor.lastSearch(MetricWeights.DEFAULT))
        processor.close()
        assertEquals(run.searchRounds, stored.rounds)
        assertEquals(34.4, stored.cm360)
        val result = assertNotNull(stored.result)
        assertTrue(abs(result.strength - live.strength) < 0.03, "stored ${result.strength}, live ${live.strength}")
        assertEquals(live.excludesPlain, result.excludesPlain)
        assertEquals(run.searchOffer != null, stored.offer != null)
    }

    @Test
    fun `without a stored search there is none`() {
        val processor = RoundProcessor(Database.open(dir.resolve("b.db")))
        assertNull(processor.lastSearch(MetricWeights.DEFAULT))
        assertNull(RoundProcessor(null).lastSearch(MetricWeights.DEFAULT))
        processor.close()
    }
}
