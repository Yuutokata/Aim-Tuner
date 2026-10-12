package de.yuuto.aimtune.core.input

/**
 * One relative mouse report: raw counts [dx]/[dy] (positive x = right, positive y = down) and the
 * arrival time in nanoseconds on a monotonic clock whose origin is arbitrary but fixed per source.
 */
data class MouseEvent(val timeNanos: Long, val dx: Int, val dy: Int)
