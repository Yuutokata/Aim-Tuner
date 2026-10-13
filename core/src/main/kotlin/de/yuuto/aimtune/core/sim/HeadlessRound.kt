package de.yuuto.aimtune.core.sim

import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.math.Vec3
import de.yuuto.aimtune.core.math.ViewAngles
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.FlickConfig
import de.yuuto.aimtune.core.scenario.FlickScenario
import de.yuuto.aimtune.core.scenario.Scenario
import de.yuuto.aimtune.core.units.degreesPerCount

private const val NANOS_PER_MS = 1_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val CLOCK_ORIGIN_NANOS = 5 * NANOS_PER_SECOND

/** Mixed into the round seed so the aimer's randomness is independent of the scenario's. */
const val AIMER_SEED_MIX = 0x5DEECE66DL

/**
 * Runs one round of a scenario against a [SimulatedPlayer] on a virtual 1000 Hz clock and returns
 * the recording the app would have saved. It repeats the trainer's per-frame order (apply the mouse,
 * then the shot), so the recording equals the one of the real trainer driven by a `SimulatedMouseSource`.
 */
object HeadlessRound {
    fun flick(
        player: SimulatedPlayer,
        dpi: Double,
        cm360: Double,
        seed: Long,
        roundSeconds: Int = 45,
        sessionOffsetNanos: Long = 0L,
    ): RoundRecording = run(
        player, dpi, cm360, seed, roundSeconds, sessionOffsetNanos,
        FlickScenario(FlickConfig(durationNanos = roundSeconds * NANOS_PER_SECOND), seed),
    )

    fun run(
        player: SimulatedPlayer,
        dpi: Double,
        cm360: Double,
        seed: Long,
        roundSeconds: Int,
        sessionOffsetNanos: Long,
        scenario: Scenario,
    ): RoundRecording {
        val aimer = SimulatedAimer(player, dpi, cm360, seed xor AIMER_SEED_MIX)
        val start = CLOCK_ORIGIN_NANOS
        val end = start + roundSeconds * NANOS_PER_SECOND
        val degPerCount = degreesPerCount(dpi, cm360)
        val mouse = ArrayList<MouseEvent>()
        var view = ViewAngles()
        var now = start
        scenario.start(now)
        aimer.reset(now, sessionOffsetNanos)
        while (now < end) {
            now += NANOS_PER_MS
            val step = aimer.step(now, scenario.targets.firstOrNull())
            if (step.dx != 0 || step.dy != 0) {
                mouse += MouseEvent(now, step.dx, step.dy)
                view = view.rotatedByCounts(step.dx, step.dy, degPerCount)
            }
            if (step.click) scenario.fire(Vec3.ZERO, view.direction(), now)
        }
        return RoundRecording(
            scenarioId = scenario.id,
            seed = seed,
            dpi = dpi,
            cm360 = cm360,
            startNanos = start,
            endNanos = end,
            mouseEvents = mouse,
            targetEvents = scenario.events.toList(),
        )
    }
}
