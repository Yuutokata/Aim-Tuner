package de.yuuto.aimtune.app

import de.yuuto.aimtune.core.metrics.RoundMetrics
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.storage.Database
import de.yuuto.aimtune.storage.RoundRepository
import de.yuuto.aimtune.storage.SessionKind
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
 * waits for segmentation or disk. One session is created per app run, on the first finished round.
 * Without a [database] rounds are still analysed but not stored.
 */
class RoundProcessor(private val database: Database?, private val dpi: Double) : AutoCloseable {
    private val log = System.getLogger("aimtune.rounds")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "aimtune-round-processor").apply { isDaemon = true } }
    private val repository = database?.let(::RoundRepository)

    // Only touched on the executor thread.
    private var sessionId: Long? = null

    @Volatile
    var report: RoundReport? = null
        private set

    fun submit(recording: RoundRecording, startedAtMs: Long, blind: Boolean) {
        report = RoundReport.Pending
        executor.execute {
            report = try {
                val metrics = computeRoundMetrics(recording)
                RoundReport.Done(metrics, store(recording, metrics, startedAtMs, blind))
            } catch (e: Exception) {
                log.log(System.Logger.Level.ERROR, "round analysis failed", e)
                RoundReport.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Returns null on success or a short reason; storage problems must not lose the analysis. */
    private fun store(recording: RoundRecording, metrics: RoundMetrics, startedAtMs: Long, blind: Boolean): String? {
        val repo = repository ?: return "storage unavailable"
        return try {
            val session = sessionId ?: repo.createSession(startedAtMs, gameId = null, dpi = dpi, kind = SessionKind.SENSITIVITY).also { sessionId = it }
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
