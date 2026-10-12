package de.yuuto.aimtune.input

import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.input.NanoClock

/**
 * Source of raw relative mouse movement. Implementations deliver [MouseEvent]s from their own
 * thread into an internal queue; the consumer drains it from the render thread.
 *
 * Hard rule: a source must deliver input only while the application window is focused. The caller
 * reports focus through [setWindowFocused]; while unfocused, no events may be queued.
 */
interface RawMouseSource : AutoCloseable {
    val name: String

    /** Clock that stamps the events; the consumer should use it for any time it compares with them. */
    val clock: NanoClock

    /** Total events accepted since [start] (diagnostics). */
    val eventsReceived: Long

    fun start()

    fun setWindowFocused(focused: Boolean)

    /** Hands all queued events, oldest first, to [consumer] and removes them from the queue. */
    fun drain(consumer: (MouseEvent) -> Unit)

    override fun close()
}
