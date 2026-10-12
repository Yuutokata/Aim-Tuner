package de.yuuto.aimtune.core.recording

import de.yuuto.aimtune.core.input.MouseEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewTrackTest {
    private val events = listOf(MouseEvent(10, 100, 0), MouseEvent(20, 0, 50), MouseEvent(30, 100, 0))
    private val recording = RoundRecording("flick", 1, dpi = 800.0, cm360 = 40.0, startNanos = 0, endNanos = 100, mouseEvents = events, targetEvents = emptyList())
    private val dpc = recording.degreesPerCount

    @Test
    fun `view before the first event is the start orientation`() {
        val v = ViewTrack(recording).viewAt(5)
        assertEquals(0.0, v.yawDegrees)
        assertEquals(0.0, v.pitchDegrees)
    }

    @Test
    fun `view accumulates counts up to the requested time`() {
        val track = ViewTrack(recording)
        assertEquals(100 * dpc, track.viewAt(10).yawDegrees, 1e-9)
        val mid = track.viewAt(25)
        assertEquals(100 * dpc, mid.yawDegrees, 1e-9)
        assertEquals(-50 * dpc, mid.pitchDegrees, 1e-9)
        assertEquals(200 * dpc, track.viewAt(1000).yawDegrees, 1e-9)
    }
}
