package de.yuuto.aimtune.app

import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.input.NanoClock
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.TargetEvent
import de.yuuto.aimtune.games.GameProfiles
import de.yuuto.aimtune.input.RawMouseSource
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainerTest {
    private class FakeSource : RawMouseSource {
        var now = 0L
        private val queue = ConcurrentLinkedQueue<MouseEvent>()
        override val name = "fake"
        override val clock = NanoClock { now }
        override var eventsReceived = 0L
        override fun start() = Unit
        override fun setWindowFocused(focused: Boolean) = Unit
        override fun close() = Unit
        fun push(dx: Int, dy: Int) {
            queue.add(MouseEvent(now, dx, dy))
            eventsReceived++
        }
        override fun drain(consumer: (MouseEvent) -> Unit) {
            while (true) consumer(queue.poll() ?: return)
        }
    }

    private val options = TrainerConfig(
        dpi = 800.0, cm360 = 40.0, profile = checkNotNull(GameProfiles.load().find("valorant")), roundSeconds = 2, seed = 5,
    )
    private val second = 1_000_000_000L

    private fun trainer(source: FakeSource, sink: (RoundRecording) -> Unit) = Trainer(options, source, sink)

    @Test
    fun `a finished round is delivered with its mouse and target events`() {
        val source = FakeSource()
        val finished = mutableListOf<RoundRecording>()
        val trainer = trainer(source) { finished += it }

        source.now = 10 * second
        trainer.startRound()
        source.now += second / 2
        source.push(120, -4)
        trainer.update()
        source.now += second / 2
        source.push(-30, 8)
        trainer.update()
        assertEquals(Phase.RUNNING, trainer.phase)
        assertTrue(finished.isEmpty())

        source.now = 10 * second + 2 * second
        trainer.update()

        assertEquals(Phase.FINISHED, trainer.phase)
        val r = finished.single()
        assertEquals("flick", r.scenarioId)
        assertEquals(5L, r.seed)
        assertEquals(800.0, r.dpi)
        assertEquals(40.0, r.cm360)
        assertEquals(10 * second, r.startNanos)
        assertEquals(12 * second, r.endNanos)
        assertEquals(listOf(120 to -4, -30 to 8), r.mouseEvents.map { it.dx to it.dy })
        assertTrue(r.targetEvents.first() is TargetEvent.Spawned)
    }

    @Test
    fun `mouse reports after the round end are not part of the recording`() {
        val source = FakeSource()
        val finished = mutableListOf<RoundRecording>()
        val trainer = trainer(source) { finished += it }
        source.now = 0
        trainer.startRound()
        source.now = 2 * second + 5_000_000
        source.push(50, 50)
        trainer.update()
        assertTrue(finished.single().mouseEvents.isEmpty())
    }

    @Test
    fun `aborting discards the round and delivers nothing`() {
        val source = FakeSource()
        val finished = mutableListOf<RoundRecording>()
        val trainer = trainer(source) { finished += it }
        trainer.startRound()
        source.push(10, 10)
        trainer.update()
        trainer.abort()
        assertEquals(Phase.READY, trainer.phase)
        source.now += 5 * second
        trainer.update()
        assertTrue(finished.isEmpty())
        assertNull(trainer.summary)
    }

    @Test
    fun `the next round uses the next seed and starts clean`() {
        val source = FakeSource()
        val finished = mutableListOf<RoundRecording>()
        val trainer = trainer(source) { finished += it }
        trainer.startRound()
        source.now += 3 * second
        trainer.update()
        trainer.startRound()
        source.now += 3 * second
        trainer.update()
        assertEquals(listOf(5L, 6L), finished.map { it.seed })
        assertNotNull(finished[1].targetEvents.firstOrNull())
        assertTrue(finished[1].mouseEvents.isEmpty())
    }

    @Test
    fun `input outside a running round moves nothing and is not recorded`() {
        val source = FakeSource()
        val trainer = trainer(source) { }
        source.push(500, 500)
        trainer.update()
        assertEquals(0.0, trainer.view.yawDegrees)
        assertEquals(500, trainer.lastEvent?.dx)
    }
}
