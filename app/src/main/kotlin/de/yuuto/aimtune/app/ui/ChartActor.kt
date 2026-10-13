package de.yuuto.aimtune.app.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import kotlin.math.atan2
import kotlin.math.hypot

private const val PLOT_LEFT = 72f
private const val PLOT_BOTTOM = 40f
private const val PLOT_TOP = 36f
private const val PLOT_RIGHT = 16f
private const val LINE_WIDTH = 3f
private const val GRID_WIDTH = 1f
private const val DASH = 14f
private const val GAP = 9f
private const val DOT_SIZE = 12f
private const val TICK_GAP = 10f
private const val MIN_SIZE = 200f
private const val GRID_ALPHA = 0.5f
private const val BAND_STRIP = 3f

/**
 * Draws a [ChartSpec] with sprite-batch rectangles: grid and axis labels, uncertainty bands, lines
 * (solid or dashed), dots and labelled vertical markers. Sizes are design units like the rest of the UI.
 */
class ChartActor(private val ui: UiSkin, private val spec: ChartSpec) : Widget() {
    private val white = ui.whiteSolid as TextureRegionDrawable
    private val font = ui.fonts.caption
    private val layout = GlyphLayout()
    private val dot = ui.dot(Color.WHITE, DOT_SIZE)

    override fun getPrefWidth() = MIN_SIZE

    override fun getPrefHeight() = MIN_SIZE

    private val plotWidth get() = width - PLOT_LEFT - PLOT_RIGHT
    private val plotHeight get() = height - PLOT_BOTTOM - PLOT_TOP

    private fun sx(x: Double): Float = this.x + PLOT_LEFT + ((x - spec.xMin) / (spec.xMax - spec.xMin)).toFloat() * plotWidth
    private fun sy(y: Double): Float = this.y + PLOT_BOTTOM + ((y - spec.yMin) / (spec.yMax - spec.yMin)).toFloat() * plotHeight

    override fun draw(batch: Batch, parentAlpha: Float) {
        val alpha = color.a * parentAlpha
        val saved = Color(batch.color)
        drawGrid(batch, alpha)
        spec.bands.forEach { drawBand(batch, it, alpha) }
        spec.referenceYs.forEach { y -> rect(batch, sx(spec.xMin), sy(y) - GRID_WIDTH, plotWidth, GRID_WIDTH * 2, ui.tokens.neutral(600), alpha) }
        spec.lines.forEach { drawLine(batch, it, alpha) }
        spec.markers.forEach { drawMarker(batch, it, alpha) }
        spec.dots.forEach { drawDots(batch, it, alpha) }
        batch.color = saved
    }

    private fun drawGrid(batch: Batch, alpha: Float) {
        val grid = ui.tokens.divider
        val text = ui.tokens.neutral(500)
        for (y in spec.yTicks) {
            rect(batch, x + PLOT_LEFT, sy(y), plotWidth, GRID_WIDTH, grid, alpha * GRID_ALPHA)
            val label = spec.formatY(y)
            layout.setText(font, label)
            drawText(batch, label, x + PLOT_LEFT - TICK_GAP - layout.width, sy(y) + layout.height / 2, text, alpha)
        }
        for (x in spec.xTicks) {
            val label = spec.formatX(x)
            layout.setText(font, label)
            drawText(batch, label, sx(x) - layout.width / 2, y + PLOT_BOTTOM - TICK_GAP, text, alpha)
        }
        rect(batch, x + PLOT_LEFT, y + PLOT_BOTTOM, plotWidth, GRID_WIDTH * 2, ui.tokens.neutral(700), alpha)
    }

    /** The band as thin vertical strips, with the edges interpolated linearly between the data points. */
    private fun drawBand(batch: Batch, band: ChartBand, alpha: Float) {
        for (i in 0 until band.xs.size - 1) {
            val x0 = sx(band.xs[i])
            val x1 = sx(band.xs[i + 1])
            val steps = ((x1 - x0) / BAND_STRIP).toInt().coerceAtLeast(1)
            for (k in 0 until steps) {
                val t = (k + 0.5) / steps
                val lo = sy((band.lo[i] + (band.lo[i + 1] - band.lo[i]) * t).coerceIn(spec.yMin, spec.yMax))
                val hi = sy((band.hi[i] + (band.hi[i + 1] - band.hi[i]) * t).coerceIn(spec.yMin, spec.yMax))
                if (hi > lo) rect(batch, x0 + (x1 - x0) * k / steps, lo, (x1 - x0) / steps, hi - lo, band.color, alpha)
            }
        }
    }

    private fun drawLine(batch: Batch, line: ChartLine, alpha: Float) {
        var phase = 0f
        for (i in 0 until line.xs.size - 1) {
            phase = segment(batch, sx(line.xs[i]), sy(line.ys[i]), sx(line.xs[i + 1]), sy(line.ys[i + 1]), line.color, alpha, line.dashed, phase)
        }
    }

    private fun drawMarker(batch: Batch, marker: ChartMarker, alpha: Float) {
        val mx = sx(marker.x)
        segment(batch, mx, y + PLOT_BOTTOM, mx, y + PLOT_BOTTOM + plotHeight, marker.color, alpha, dashed = true, phase = 0f)
        layout.setText(font, marker.label)
        val labelX = (mx - layout.width / 2).coerceIn(x + PLOT_LEFT, x + PLOT_LEFT + plotWidth - layout.width)
        drawText(batch, marker.label, labelX, y + PLOT_BOTTOM + plotHeight + TICK_GAP + layout.height, marker.color, alpha)
    }

    private fun drawDots(batch: Batch, dots: ChartDots, alpha: Float) {
        batch.setColor(dots.color.r, dots.color.g, dots.color.b, dots.color.a * alpha)
        for (i in dots.xs.indices) {
            if (dots.xs[i] < spec.xMin || dots.xs[i] > spec.xMax) continue
            dot.draw(batch, sx(dots.xs[i]) - DOT_SIZE / 2, sy(dots.ys[i].coerceIn(spec.yMin, spec.yMax)) - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE)
        }
    }

    /** Draws the part of a segment that falls on a dash; returns the dash phase at its end. */
    private fun segment(batch: Batch, x0: Float, y0: Float, x1: Float, y1: Float, color: Color, alpha: Float, dashed: Boolean, phase: Float): Float {
        val length = hypot(x1 - x0, y1 - y0)
        if (length <= 0f) return phase
        val angle = Math.toDegrees(atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())).toFloat()
        batch.setColor(color.r, color.g, color.b, color.a * alpha)
        if (!dashed) {
            white.draw(batch, x0, y0 - LINE_WIDTH / 2, 0f, LINE_WIDTH / 2, length, LINE_WIDTH, 1f, 1f, angle)
            return phase
        }
        val period = DASH + GAP
        var at = 0f
        var offset = phase
        while (at < length) {
            val inDash = offset % period < DASH
            val run = minOf(if (inDash) DASH - offset % period else period - offset % period, length - at)
            if (inDash) {
                val sx0 = x0 + (x1 - x0) * at / length
                val sy0 = y0 + (y1 - y0) * at / length
                white.draw(batch, sx0, sy0 - LINE_WIDTH / 2, 0f, LINE_WIDTH / 2, run, LINE_WIDTH, 1f, 1f, angle)
            }
            at += run
            offset += run
        }
        return offset % period
    }

    private fun rect(batch: Batch, x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) {
        batch.setColor(color.r, color.g, color.b, color.a * alpha)
        white.draw(batch, x, y, w, h)
    }

    private fun drawText(batch: Batch, text: String, x: Float, y: Float, color: Color, alpha: Float) {
        font.setColor(color.r, color.g, color.b, color.a * alpha)
        font.draw(batch, text, x, y)
    }
}
