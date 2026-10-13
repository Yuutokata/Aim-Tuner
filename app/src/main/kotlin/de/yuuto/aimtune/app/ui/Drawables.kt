package de.yuuto.aimtune.app.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Creates the anti-aliased rounded shapes of the design (outlined buttons, cards, inputs) as
 * drawables. Shapes are drawn into small textures at framebuffer resolution and stretched as nine
 * patches; sizes are given in design units and [scale] converts them to pixels. Owns its textures.
 */
class DrawableFactory(private val scale: Float) : Disposable {
    private val textures = mutableListOf<Texture>()

    /** A rounded rectangle with optional [fill] and [border]; stretches to any size. */
    fun roundedRect(radius: Float, fill: Color?, border: Color? = null, borderWidth: Float = 0f): Drawable {
        val r = max(1, (radius * scale).roundToInt())
        val b = if (border == null) 0f else max(1f, borderWidth * scale)
        val size = 2 * r + 2
        val texture = texture(shapePixmap(size, r.toFloat(), fill, border, b))
        val patch = NinePatch(TextureRegion(texture), r, r, r, r)
        patch.scale(1f / scale, 1f / scale)
        return NinePatchDrawable(patch)
    }

    /** A filled circle of [diameter] design units. */
    fun circle(diameter: Float, color: Color): Drawable {
        val px = max(2, (diameter * scale).roundToInt())
        val drawable = TextureRegionDrawable(TextureRegion(texture(shapePixmap(px, px / 2f, color, null, 0f))))
        drawable.setMinSize(diameter, diameter)
        return drawable
    }

    /** A rounded bar (slider track and fill) of [height] design units; stretches horizontally. */
    fun pill(height: Float, color: Color): Drawable = roundedRect(height / 2f, color)

    /** A solid 1-pixel color, stretched to any size (text cursor, selections, lines). */
    fun solid(color: Color): Drawable {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(color)
        pixmap.fill()
        return TextureRegionDrawable(TextureRegion(texture(pixmap)))
    }

    private fun texture(pixmap: Pixmap): Texture {
        val texture = Texture(pixmap)
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pixmap.dispose()
        textures += texture
        return texture
    }

    override fun dispose() {
        textures.forEach { it.dispose() }
    }

    internal companion object {
        /**
         * Rasterizes a rounded square of [size] pixels with corner [radius]: [fill] inside, [border]
         * ring of [borderWidth] pixels at the edge, one pixel of anti-aliasing. Straight (non
         * premultiplied) RGBA, as SpriteBatch expects.
         */
        fun shapePixmap(size: Int, radius: Float, fill: Color?, border: Color?, borderWidth: Float): Pixmap {
            val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
            pixmap.blending = Pixmap.Blending.None
            val half = size / 2f
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val d = distanceOutside(x + 0.5f - half, y + 0.5f - half, half, radius)
                    val outer = coverage(d)
                    val inner = if (border == null) outer else coverage(d + borderWidth)
                    pixmap.setColor(blend(fill, border, outer, inner))
                    pixmap.drawPixel(x, y)
                }
            }
            return pixmap
        }

        /** Signed distance from the point to the rounded square's edge (negative inside). */
        private fun distanceOutside(px: Float, py: Float, half: Float, radius: Float): Float {
            val qx = abs(px) - (half - radius)
            val qy = abs(py) - (half - radius)
            return hypot(max(qx, 0f), max(qy, 0f)) + minOf(max(qx, qy), 0f) - radius
        }

        private fun coverage(distance: Float): Float = (0.5f - distance).coerceIn(0f, 1f)

        private fun blend(fill: Color?, border: Color?, outer: Float, inner: Float): Color {
            val fillWeight = (if (fill == null) 0f else fill.a * minOf(inner, outer))
            val borderWeight = (if (border == null) 0f else border.a * (outer - minOf(inner, outer)))
            val alpha = fillWeight + borderWeight
            if (alpha <= 0f) return Color(0f, 0f, 0f, 0f)
            val r = ((fill?.r ?: 0f) * fillWeight + (border?.r ?: 0f) * borderWeight) / alpha
            val g = ((fill?.g ?: 0f) * fillWeight + (border?.g ?: 0f) * borderWeight) / alpha
            val b = ((fill?.b ?: 0f) * fillWeight + (border?.b ?: 0f) * borderWeight) / alpha
            return Color(r, g, b, alpha.coerceAtMost(1f))
        }
    }
}
