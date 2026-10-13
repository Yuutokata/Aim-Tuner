package de.yuuto.aimtune.app

import de.yuuto.aimtune.app.blind.PastRound
import de.yuuto.aimtune.core.metrics.RoundMetrics
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.storage.Database
import de.yuuto.aimtune.storage.RoundLogFile
import de.yuuto.aimtune.storage.RoundRepository
import de.yuuto.aimtune.storage.SessionKind
import de.yuuto.aimtune.storage.scoreInputs
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** What the HUD shows about the most recent round's analysis. */
sealed interface RoundReport {
    data object Pending : RoundReport

    /** [saveError] is null when the round was stored, or the reason it was not. */
    data class Done(val metrics: RoundMetrics, val saveError: String?) : RoundReport

    data class Failed(val message: String) : RoundReport
}

/**
 * Computes metrics and stores finished rounds on a background thread so the render thread never
 * waits for segmentation or disk. A session is created on the first finished round and a new one
 * whenever DPI, game or blind test run change. Without a [database] rounds are still analysed but not stored.
 * With an [exportDir] every round is also written there as a raw event log, so real sessions can
 * become regression fixtures; a failing export is logged and never affects the round.
 */
class RoundProcessor(private val database: Database?, private val exportDir: Path? = null) : AutoCloseable {
    private val log = System.getLogger("aimtune.rounds")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "aimtune-round-processor").apply { isDaemon = true } }
    private val repository = database?.let(::RoundRepository)

    // Only touched on the executor thread.
    private var sessionId: Long? = null
    private var sessionKey: Triple<Double, String?, Long?>? = null

    @Volatile
    var report: RoundReport? = null
        private set

    /** [runKey] separates the rounds of one blind test run into their own session; null keeps them with the game and DPI. */
    fun submit(recording: RoundRecording, startedAtMs: Long, blind: Boolean, gameId: String?, runKey: Long? = null) {
        report = RoundReport.Pending
        executor.execute {
            export(recording, startedAtMs)
            report = try {
                val metrics = computeRoundMetrics(recording)
                RoundReport.Done(metrics, store(recording, metrics, startedAtMs, blind, gameId, runKey))
            } catch (e: Exception) {
                log.log(System.Logger.Level.ERROR, "round analysis failed", e)
                RoundReport.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * The stored blind rounds of this game and DPI from all sessions, for merging into a new test.
     * Reads on the storage thread (so it never overlaps a save) and waits for the answer; empty
     * without a database or if reading fails.
     */
    fun pastBlindRounds(gameId: String?, dpi: Double): List<PastRound> {
        val repo = repository ?: return emptyList()
        return try {
            executor.submit<List<PastRound>> {
                repo.blindFlickRounds(gameId, dpi).map { PastRound(it.sessionId, it.cm360, it.scoreInputs()) }
            }.get()
        } catch (e: Exception) {
            log.log(System.Logger.Level.ERROR, "reading earlier rounds failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun export(recording: RoundRecording, startedAtMs: Long) {
        val dir = exportDir ?: return
        try {
            RoundLogFile.write(recording, dir.resolve("round-$startedAtMs-seed${recording.seed}.json"))
        } catch (e: Exception) {
            log.log(System.Logger.Level.ERROR, "exporting round log failed: ${e.message}", e)
        }
    }

    /** Returns null on success or a short reason; storage problems must not lose the analysis. */
    private fun store(recording: RoundRecording, metrics: RoundMetrics, startedAtMs: Long, blind: Boolean, gameId: String?, runKey: Long?): String? {
        val repo = repository ?: return "storage unavailable"
        return try {
            val key = Triple(recording.dpi, gameId, runKey)
            val session = sessionId.takeIf { sessionKey == key }
                ?: repo.createSession(startedAtMs, gameId, recording.dpi, SessionKind.SENSITIVITY).also {
                    sessionId = it
                    sessionKey = key
                }
            repo.saveRound(session, recording, startedAtMs, blind, metrics)
            null
        } catch (e: Exception) {
            log.log(System.Logger.Level.ERROR, "saving round failed", e)
            e.message ?: e.javaClass.simpleName
        }
    }

    /** Waits for queued rounds to finish, then closes the database. */
    override fun close() {
        executor.shutdown()
        if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
            log.log(System.Logger.Level.WARNING, "round processing did not finish before shutdown")
        }
        database?.close()
    }

    private companion object {
        const val SHUTDOWN_WAIT_SECONDS = 10L
    }
}
