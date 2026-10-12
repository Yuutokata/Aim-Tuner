package de.yuuto.aimtune.app

import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.math.Vec3
import de.yuuto.aimtune.core.math.ViewAngles
import de.yuuto.aimtune.core.scenario.FlickConfig
import de.yuuto.aimtune.core.scenario.FlickScenario
import de.yuuto.aimtune.core.scenario.RoundSummary
import de.yuuto.aimtune.core.scenario.Scenario
import de.yuuto.aimtune.core.scenario.ShotResult
import de.yuuto.aimtune.core.scenario.Target
import de.yuuto.aimtune.core.scenario.summarize
import de.yuuto.aimtune.core.units.degreesPerCount
import de.yuuto.aimtune.input.RawMouseSource

enum class Phase { READY, RUNNING, FINISHED }

/**
 * Round state machine of the milestone-2 trainer. Free of rendering calls; everything runs on the
 * render thread. Pausing mid-round is not supported: aborting returns to READY and discards the
 * round, so a measured round is never interrupted silently.
 */
class Trainer(private val options: LaunchOptions, private val source: RawMouseSource) {
    private val log = System.getLogger("aimtune.trainer")
    private val degreesPerCount = degreesPerCount(options.dpi, options.cm360)
    private val clock = source.clock
    private val roundNanos = options.roundSeconds * 1_000_000_000L
    private var roundStartNanos = 0L
    private var nextSeed = options.seed
    private var scenario: Scenario? = null

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
    val activeTarget: Target? get() = if (phase == Phase.RUNNING) scenario?.targets?.firstOrNull() else null
    val hits: Int get() = scenario?.let { summarize(it.events).hits } ?: 0
    val misses: Int get() = scenario?.let { summarize(it.events).misses } ?: 0

    fun nowNanos(): Long = clock.nowNanos()

    fun timeLeftNanos(): Long =
        if (phase == Phase.RUNNING) (roundStartNanos + roundNanos - nowNanos()).coerceAtLeast(0L) else 0L

    fun startRound() {
        val seed = nextSeed++
        log.log(System.Logger.Level.INFO, "starting flick round, seed $seed")
        discardPendingMouse()
        view = ViewAngles()
        summary = null
        scenario = FlickScenario(FlickConfig(durationNanos = roundNanos), seed)
        roundStartNanos = nowNanos()
        scenario?.start(roundStartNanos)
        phase = Phase.RUNNING
    }

    /** Aborting (Esc, focus loss) discards the running round. */
    fun abort() {
        if (phase != Phase.RUNNING) return
        log.log(System.Logger.Level.INFO, "round aborted")
        scenario = null
        phase = Phase.READY
    }

    /** Applies queued mouse input and ends the round when its time is up. Call once per frame. */
    fun update() {
        applyPendingMouse()
        val s = scenario ?: return
        if (phase == Phase.RUNNING && s.isFinished(nowNanos())) {
            summary = summarize(s.events)
            phase = Phase.FINISHED
        }
    }

    fun fire(): ShotResult {
        val s = scenario ?: return ShotResult.IGNORED
        if (phase != Phase.RUNNING) return ShotResult.IGNORED
        // Bring the view up to date first so the shot uses the latest mouse input.
        applyPendingMouse()
        return s.fire(Vec3.ZERO, view.direction(), nowNanos())
    }

    private fun applyPendingMouse() {
        source.drain { event ->
            lastEvent = event
            rate.record(event.timeNanos)
            if (phase == Phase.RUNNING) view = view.rotatedByCounts(event.dx, event.dy, degreesPerCount)
        }
    }

    private fun discardPendingMouse() = source.drain { }
}
