package de.yuuto.aimtune.app

import de.yuuto.aimtune.core.accel.AccelProcessor
import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.math.Vec3
import de.yuuto.aimtune.core.math.ViewAngles
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.RoundSummary
import de.yuuto.aimtune.core.scenario.Scenario
import de.yuuto.aimtune.core.scenario.ShotResult
import de.yuuto.aimtune.core.scenario.Target
import de.yuuto.aimtune.core.scenario.summarize
import de.yuuto.aimtune.core.units.degreesPerCount
import de.yuuto.aimtune.input.RawMouseSource

enum class Phase { READY, RUNNING, FINISHED }

/**
 * Round state machine of the trainer. Free of rendering calls; everything runs on the render
 * thread. Pausing mid-round is not supported: aborting returns to READY and discards the round,
 * so a measured round is never interrupted silently. A completed round is handed to
 * [onRoundFinished] as a [RoundRecording].
 */
class Trainer(
    private val config: TrainerConfig,
    private val source: RawMouseSource,
    private val onRoundFinished: (RoundRecording) -> Unit = {},
) {
    private val log = System.getLogger("aimtune.trainer")
    private val degreesPerCount = degreesPerCount(config.dpi, config.cm360)
    private val clock = source.clock
    private val roundNanos = config.roundSeconds * 1_000_000_000L
    private var roundStartNanos = 0L
    private var roundSeed = 0L
    private var nextSeed = config.seed
    private var scenario: Scenario? = null
    private val recordedMouse = ArrayList<MouseEvent>()
    private var accelProcessor: AccelProcessor? = null

    var phase = Phase.READY
        private set
    var view = ViewAngles()
        private set
    var summary: RoundSummary? = null
        private set
    var lastEvent: MouseEvent? = null
        private set
    val rate = EventRateMeter()

    val degreesPerCountValue: Double get() = degreesPerCount
    val activeTargets: List<Target> get() = if (phase == Phase.RUNNING) scenario?.targets.orEmpty() else emptyList()
    val hits: Int get() = scenario?.let { summarize(it.events).hits } ?: 0
    val misses: Int get() = scenario?.let { summarize(it.events).misses } ?: 0

    fun nowNanos(): Long = clock.nowNanos()

    fun timeLeftNanos(): Long =
        if (phase == Phase.RUNNING) (roundStartNanos + roundNanos - nowNanos()).coerceAtLeast(0L) else 0L

    fun startRound() {
        roundSeed = nextSeed++
        log.log(System.Logger.Level.INFO, "starting ${config.scenario.id} round, seed $roundSeed")
        source.drain { }
        view = ViewAngles()
        summary = null
        recordedMouse.clear()
        accelProcessor = config.accel?.let { AccelProcessor(it, config.dpi) }
        scenario = config.scenario.create(roundNanos, roundSeed)
        roundStartNanos = nowNanos()
        scenario?.start(roundStartNanos)
        phase = Phase.RUNNING
    }

    /** Aborting (Esc, focus loss) discards the running round. */
    fun abort() {
        if (phase != Phase.RUNNING) return
        log.log(System.Logger.Level.INFO, "round aborted")
        scenario = null
        recordedMouse.clear()
        phase = Phase.READY
    }

    /** Applies queued mouse input and ends the round when its time is up. Call once per frame. */
    fun update() {
        applyPendingMouse()
        val s = scenario ?: return
        if (phase != Phase.RUNNING) return
        val now = nowNanos()
        s.update(now)
        if (s.isFinished(now)) finishRound(s)
    }

    fun fire(): ShotResult {
        val s = scenario ?: return ShotResult.IGNORED
        if (phase != Phase.RUNNING) return ShotResult.IGNORED
        // Bring the view and moving targets up to date first so the shot uses the latest state.
        applyPendingMouse()
        val now = nowNanos()
        s.update(now)
        return s.fire(Vec3.ZERO, view.direction(), now)
    }

    private fun finishRound(s: Scenario) {
        val end = roundStartNanos + roundNanos
        summary = summarize(s.events)
        phase = Phase.FINISHED
        onRoundFinished(
            RoundRecording(
                scenarioId = s.id,
                seed = roundSeed,
                dpi = config.dpi,
                cm360 = config.cm360,
                startNanos = roundStartNanos,
                endNanos = end,
                // Reports that arrived after the round's end belong to no round.
                mouseEvents = recordedMouse.filter { it.timeNanos <= end },
                targetEvents = s.events.toList(),
                accel = config.accel,
            ),
        )
    }

    private fun applyPendingMouse() {
        source.drain { event ->
            lastEvent = event
            rate.record(event.timeNanos)
            if (phase == Phase.RUNNING) {
                val scale = accelProcessor?.scaleFor(event) ?: 1.0
                view = view.rotatedByCounts(event.dx * scale, event.dy * scale, degreesPerCount)
                recordedMouse += event
            }
        }
    }
}
