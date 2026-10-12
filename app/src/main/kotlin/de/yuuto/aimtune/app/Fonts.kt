package de.yuuto.aimtune.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.utils.Disposable
import kotlin.math.roundToInt

/**
 * Inter and JetBrains Mono rasterized at the exact pixel size for the current framebuffer, so text
 * stays sharp (a scaled bitmap font blurs). Sizes follow the design tokens at 1920x1080 and scale
 * with the framebuffer height. Call [dispose] before building a new instance.
 */
class Fonts(framebufferHeight: Int) : Disposable {
    private val scale = framebufferHeight / REFERENCE_HEIGHT
    private val generators = mutableListOf<FreeTypeFontGenerator>()

    /** Interface text (token `body`, 20 px at 1080p). */
    val body = create(INTER_REGULAR, BODY_PX)

    /** Section titles (token `title`, 32 px). */
    val title = create(INTER_MEDIUM, TITLE_PX)

    /** Uppercase labels (token `label`, 14 px). */
    val label = create(INTER_MEDIUM, LABEL_PX)

    /** Numbers, units, timers and the debug overlay (token `body` in mono). */
    val mono = create(JETBRAINS_MONO, BODY_PX)

    /** Large numbers such as the round timer (token `numberMd`, 44 px). */
    val monoLarge = create(JETBRAINS_MONO, NUMBER_MD_PX)

    private fun create(path: String, referencePx: Int): BitmapFont {
        val generator = FreeTypeFontGenerator(Gdx.files.classpath(path)).also { generators += it }
        val parameter = FreeTypeFontParameter().apply {
            size = (referencePx * scale).roundToInt().coerceAtLeast(MIN_PX)
            minFilter = Texture.TextureFilter.Linear
            magFilter = Texture.TextureFilter.Linear
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRA_CHARS
        }
        return generator.generateFont(parameter)
    }

    override fun dispose() {
        listOf(body, title, label, mono, monoLarge).forEach { it.dispose() }
        generators.forEach { it.dispose() }
    }

    private companion object {
        const val REFERENCE_HEIGHT = 1080f
        const val MIN_PX = 10
        const val BODY_PX = 20
        const val TITLE_PX = 32
        const val LABEL_PX = 14
        const val NUMBER_MD_PX = 44

        const val INTER_REGULAR = "assets/fonts/Inter-Regular.ttf"
        const val INTER_MEDIUM = "assets/fonts/Inter-Medium.ttf"
        const val JETBRAINS_MONO = "assets/fonts/JetBrainsMono-Regular.ttf"

        // Degree sign, middle dot, dashes and German letters used by the UI texts.
        const val EXTRA_CHARS = "°·–—…ÄÖÜäöüß"
    }
}
