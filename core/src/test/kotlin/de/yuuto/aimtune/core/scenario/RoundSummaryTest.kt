package de.yuuto.aimtune.core.scenario

import de.yuuto.aimtune.core.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoundSummaryTest {
    private fun target(id: Int) = Target(id, Vec3(0.0, 0.0, -10.0), 0.25)

    @Test
    fun `empty round has no hits and zero accuracy`() {
        val s = summarize(emptyList())
        assertEquals(0, s.shots)
        assertEquals(0.0, s.accuracy)
        assertNull(s.meanTimeToHitNanos)
    }

    @Test
    fun `counts hits misses and mean time to hit`() {
        val events = listOf(
            TargetEvent.Spawned(0, target(0)),
            TargetEvent.Miss(100),
            TargetEvent.Hit(400, 0),
            TargetEvent.Spawned(400, target(1)),
            TargetEvent.Hit(1000, 1),
        )
        val s = summarize(events)
        assertEquals(2, s.hits)
        assertEquals(1, s.misses)
        assertEquals(3, s.shots)
        assertEquals(2.0 / 3.0, s.accuracy, 1e-12)
        assertEquals(500L, s.meanTimeToHitNanos)
    }
}
