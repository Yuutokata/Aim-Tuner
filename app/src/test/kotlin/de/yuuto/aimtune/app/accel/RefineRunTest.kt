package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.app.TrainerConfig
import de.yuuto.aimtune.core.accel.RefinePhase
import de.yuuto.aimtune.core.accel.RawAccelCurve
import de.yuuto.aimtune.core.score.MetricWeights
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
import de.yuuto.aimtune.games.GameProfiles
import de.yuuto.aimtune.storage.RawAccelExport
import de.yuuto.aimtune.storage.SessionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefineRunTest {
    private val config = TrainerConfig(800.0, 55.0, checkNotNull(GameProfiles.load().find("valorant")), roundSeconds = 20, seed = 0)
    private val text = checkNotNull(sampleRefineText())
    private val source = RefineImport.parse(text, "sample").getOrThrow()

    private fun profile(dpi: Double = 800.0) = RefineImport.choose(source, "default", dpi)

    @Test
    fun `the user profile imports at the settings dpi and is refused at another one`() {
        assertNotNull(profile().profile)
        val other = profile(1600.0)
        assertNull(other.profile)
        assertEquals(800, other.dpiMismatch)
    }

    @Test
    fun `an unreadable text is a failure with a reason`() {
        assertTrue(RefineImport.parse("not json", "x").isFailure)
    }

    @Test
    fun `rounds play the profile at its own cm360 with its curve, warm-up first`() {
        val imported = checkNotNull(profile().profile)
        val run = RefineRun(config, MetricWeights.DEFAULT, 1, 2, source, imported, flash = false)
        val first = run.nextRoundConfig()
        assertEquals(55.0 / 0.75, first.cm360, 1e-9)
        assertEquals(imported.curve, first.accel)
        assertTrue(first.accel is RawAccelCurve)
        assertEquals(800.0, first.dpi)
    }

    @Test
    fun `the run reaches the first stage result and the export name is the profile name with a suffix`() {
        val run = checkNotNull(sampleRefineRun(config))
        assertEquals(RefinePhase.STAGE_RESULT, run.phase)
        assertNotNull(run.engine.currentResult)
        assertEquals("default (AimTune)", run.exportName)
        val player = SimulatedPlayer()
        run.engine.continueToNextStage()
        val next = run.nextRoundConfig()
        run.recordRound(HeadlessRound.of(next.scenario, player, next.dpi, next.cm360, next.seed, next.roundSeconds, accel = next.accel))
        assertEquals(1, run.engine.searchPlayed)
    }

    @Test
    fun `refine rounds are a session kind of their own that stored searches ignore`() {
        assertEquals("accel_refine", SessionKind.ACCEL_REFINE.id)
        assertEquals(SessionKind.ACCEL_REFINE, SessionKind.fromId("accel_refine"))
        assertTrue(StoredSearches.searchRounds(SessionKind.ACCEL_REFINE, emptyList()).isEmpty())
    }

    @Test
    fun `a refined profile exports as a patched copy that keeps the original and lists its changes`() {
        val imported = checkNotNull(profile().profile)
        val refined = imported.copy(outputDpi = imported.outputDpi * 1.1, args = imported.args.copy(capY = 1.7))
        val changes = RefineDiff.changes(imported, refined)
        assertEquals(listOf("Output DPI", "Cap / Jump y"), changes.map { it.field })
        assertEquals("600", changes[0].old)
        assertEquals("660", changes[0].new)
        val root = RawAccelExport.patch(source.file.root, "default", refined.args, refined.outputDpi, "default (AimTune)")
        assertEquals(listOf("default", "default (AimTune)"), de.yuuto.aimtune.storage.RawAccelImport.parse(root.toString()).profileNames)
    }
}
