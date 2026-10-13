package de.yuuto.aimtune.app.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.List
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable
import de.yuuto.aimtune.app.Fonts

/** Names of the widget styles registered in [UiSkin.skin]. */
object Styles {
    // Label
    const val BODY = "body"
    const val BODY_MUTED = "bodyMuted"
    const val BODY_LG = "bodyLg"
    const val CAPTION = "caption"
    const val CAPTION_MUTED = "captionMuted"
    const val LABEL = "label"
    const val BRAND = "brand"
    const val TITLE = "title"
    const val HEADING = "heading"
    const val MONO = "mono"
    const val MONO_MUTED = "monoMuted"
    const val NUMBER_MD = "numberMd"
    const val NUMBER_LG = "numberLg"
    const val NUMBER_XL = "numberXl"
    const val OK = "ok"
    const val CAUTION = "caution"

    // TextButton
    const val PRIMARY = "primary"
    const val SECONDARY = "secondary"
    const val TEXT = "text"
    const val TILE = "tile"

    // Button (container with its own labels) and cards
    const val CARD_BUTTON = "cardButton"
    const val CARD = "card"
    const val CARD_CAUTION = "cardCaution"

    // TextField
    const val INPUT_LG = "inputLg"
    const val INPUT = "input"
}

/**
 * The Scene2D skin of the design: colors, radii and type come from the design tokens, shapes are
 * generated at framebuffer resolution (see [DrawableFactory]). Sizes everywhere are design units.
 * Rebuild when the framebuffer height changes noticeably; dispose the old one afterwards.
 */
class UiSkin(val tokens: Tokens, framebufferHeight: Int) : Disposable {
    val fonts = Fonts(framebufferHeight, tokens)
    private val shapes = DrawableFactory(fonts.scale)
    val skin = Skin()

    /** Drawables that other code needs by name (logo, cards). */
    val logoRing: Drawable = shapes.roundedRect(LOGO_SIZE / 2, null, tokens.accent, LOGO_RING)
    val logoDot: Drawable = shapes.circle(LOGO_DOT, tokens.accent)
    val surfaceSolid: Drawable = shapes.solid(tokens.surface)
    val whiteSolid: Drawable = shapes.solid(Color.WHITE)

    /** A filled circle of [diameter] design units, owned by this skin (chart dots, legend marks). */
    fun dot(color: Color, diameter: Float): Drawable = shapes.circle(diameter, color)

    /** Filled and empty segments of the step indicator. */
    val stepOn: Drawable = shapes.pill(STEP_HEIGHT, tokens.accent)
    val stepOff: Drawable = shapes.pill(STEP_HEIGHT, tokens.neutral(800))

    private val muted = tokens.neutral(500)
    private val numberColor = tokens.accent(300)

    init {
        addLabels()
        addButtons()
        addInputs()
        addCards()
        addSlidersAndBars()
        addSelectBox()
    }

    private fun addLabels() {
        label(Styles.BODY, fonts.body, tokens.text)
        label(Styles.BODY_MUTED, fonts.body, tokens.neutral(400))
        label(Styles.BODY_LG, fonts.bodyLg, tokens.text)
        label(Styles.CAPTION, fonts.caption, tokens.text)
        label(Styles.CAPTION_MUTED, fonts.caption, muted)
        label(Styles.LABEL, fonts.label, tokens.neutral(400))
        label(Styles.BRAND, fonts.brand, tokens.text)
        label(Styles.TITLE, fonts.title, tokens.text)
        label(Styles.HEADING, fonts.heading, tokens.text)
        label(Styles.MONO, fonts.mono, tokens.text)
        label(Styles.MONO_MUTED, fonts.mono, muted)
        label(Styles.NUMBER_MD, fonts.numberMd, tokens.text)
        label(Styles.NUMBER_LG, fonts.numberLg, numberColor)
        label(Styles.NUMBER_XL, fonts.numberXl, numberColor)
        label(Styles.OK, fonts.mono, tokens.ok)
        label(Styles.CAUTION, fonts.mono, tokens.caution)
    }

    private fun addButtons() {
        val r = tokens.radiusMd
        skin.add(
            Styles.PRIMARY,
            outlinedButton(tokens.accent(400), tokens.accent(300), tokens.accent, tokens.accent(900), tokens.accent(800), tokens.neutral(700)),
            TextButton.TextButtonStyle::class.java,
        )
        skin.add(
            Styles.SECONDARY,
            outlinedButton(tokens.text, tokens.text, tokens.neutral(600), tokens.surface, tokens.neutral(900), tokens.neutral(800)),
            TextButton.TextButtonStyle::class.java,
        )
        skin.add(Styles.TEXT, TextButton.TextButtonStyle().apply {
            font = fonts.bodyLg
            fontColor = tokens.accent(500)
            overFontColor = tokens.accent(300)
            downFontColor = tokens.accent(300)
            disabledFontColor = tokens.neutral(700)
        }, TextButton.TextButtonStyle::class.java)
        skin.add(Styles.TILE, TextButton.TextButtonStyle().apply {
            font = fonts.bodyLg
            fontColor = tokens.text
            disabledFontColor = tokens.neutral(600)
            up = shapes.roundedRect(r, tokens.surface, tokens.divider, TILE_BORDER)
            over = shapes.roundedRect(r, tokens.surface, tokens.neutral(600), TILE_BORDER)
            down = shapes.roundedRect(r, tokens.accent(900), tokens.accent, TILE_BORDER)
            checked = shapes.roundedRect(r, tokens.accent(900), tokens.accent, OUTLINE)
            checkedOver = checked
            checkedDown = checked
        }, TextButton.TextButtonStyle::class.java)
        skin.add(Styles.CARD_BUTTON, Button.ButtonStyle().apply {
            up = shapes.roundedRect(r, tokens.surface, tokens.divider, TILE_BORDER)
            over = shapes.roundedRect(r, tokens.surface, tokens.neutral(600), TILE_BORDER)
            down = shapes.roundedRect(r, tokens.accent(900), tokens.accent, TILE_BORDER)
            checked = shapes.roundedRect(r, tokens.accent(900), tokens.accent, OUTLINE)
            checkedOver = checked
            checkedDown = checked
        }, Button.ButtonStyle::class.java)
    }

    /** A transparent button with a [border] outline that fills on hover and press and greys out when disabled. */
    private fun outlinedButton(
        textColor: Color,
        hoverTextColor: Color,
        border: Color,
        hoverFill: Color,
        pressFill: Color,
        disabledBorder: Color,
    ): TextButton.TextButtonStyle = TextButton.TextButtonStyle().apply {
        font = fonts.bodyLg
        fontColor = textColor
        overFontColor = hoverTextColor
        downFontColor = hoverTextColor
        disabledFontColor = tokens.neutral(600)
        up = shapes.roundedRect(tokens.radiusMd, null, border, OUTLINE)
        over = shapes.roundedRect(tokens.radiusMd, hoverFill, border, OUTLINE)
        down = shapes.roundedRect(tokens.radiusMd, pressFill, border, OUTLINE)
        disabled = shapes.roundedRect(tokens.radiusMd, null, disabledBorder, OUTLINE)
    }

    private fun addInputs() {
        fun field(font: BitmapFont, height: Float) = TextField.TextFieldStyle().apply {
            this.font = font
            fontColor = tokens.text
            messageFont = font
            messageFontColor = tokens.neutral(600)
            background = shapes.roundedRect(tokens.radiusMd, tokens.surface, tokens.neutral(700), TILE_BORDER)
            focusedBackground = shapes.roundedRect(tokens.radiusMd, tokens.surface, tokens.accent, OUTLINE)
            disabledBackground = shapes.roundedRect(tokens.radiusMd, tokens.surface, tokens.neutral(800), TILE_BORDER)
            cursor = shapes.solid(tokens.accent(400)).also { it.minWidth = CURSOR_WIDTH }
            selection = shapes.solid(Color(tokens.accent.r, tokens.accent.g, tokens.accent.b, SELECTION_ALPHA))
            background.leftWidth = FIELD_PADDING
            background.rightWidth = FIELD_PADDING
            focusedBackground.leftWidth = FIELD_PADDING
            focusedBackground.rightWidth = FIELD_PADDING
            background.minHeight = height
            focusedBackground.minHeight = height
        }
        skin.add(Styles.INPUT_LG, field(fonts.numberMd, INPUT_LG_HEIGHT), TextField.TextFieldStyle::class.java)
        skin.add(Styles.INPUT, field(fonts.mono, INPUT_HEIGHT), TextField.TextFieldStyle::class.java)
    }

    private fun addCards() {
        skin.add(Styles.CARD, shapes.roundedRect(tokens.radiusMd, tokens.surface), Drawable::class.java)
        skin.add(Styles.CARD_CAUTION, shapes.roundedRect(tokens.radiusMd, tokens.surface, tokens.caution, OUTLINE), Drawable::class.java)
    }

    private fun addSlidersAndBars() {
        skin.add("default-horizontal", Slider.SliderStyle().apply {
            background = shapes.pill(TRACK_HEIGHT, tokens.neutral(800))
            knobBefore = shapes.pill(TRACK_HEIGHT, tokens.accent)
            knob = shapes.circle(KNOB_SIZE, tokens.accent(300))
        }, Slider.SliderStyle::class.java)
        skin.add("default", CheckBox.CheckBoxStyle().apply {
            font = fonts.body
            fontColor = tokens.text
            checkboxOff = shapes.roundedRect(tokens.radiusSm, tokens.surface, tokens.neutral(600), OUTLINE).also { size(it, CHECK_SIZE) }
            checkboxOn = shapes.roundedRect(tokens.radiusSm, tokens.accent, tokens.accent, OUTLINE).also { size(it, CHECK_SIZE) }
        }, CheckBox.CheckBoxStyle::class.java)
    }

    private fun addSelectBox() {
        val list = List.ListStyle().apply {
            font = fonts.body
            fontColorSelected = tokens.text
            fontColorUnselected = tokens.text
            selection = shapes.roundedRect(tokens.radiusSm, tokens.accent(900)).also {
                it.leftWidth = LIST_PADDING
                it.rightWidth = LIST_PADDING
                it.topHeight = LIST_ITEM_PADDING
                it.bottomHeight = LIST_ITEM_PADDING
            }
        }
        val scroll = ScrollPane.ScrollPaneStyle().apply {
            background = shapes.roundedRect(tokens.radiusMd, tokens.surface, tokens.neutral(700), TILE_BORDER)
        }
        skin.add("default", SelectBox.SelectBoxStyle().apply {
            font = fonts.body
            fontColor = tokens.text
            background = shapes.roundedRect(tokens.radiusMd, tokens.surface, tokens.neutral(700), TILE_BORDER).also {
                it.leftWidth = FIELD_PADDING
                it.rightWidth = FIELD_PADDING
                it.minHeight = INPUT_HEIGHT
            }
            backgroundOver = shapes.roundedRect(tokens.radiusMd, tokens.surface, tokens.neutral(600), TILE_BORDER).also {
                it.leftWidth = FIELD_PADDING
                it.rightWidth = FIELD_PADDING
                it.minHeight = INPUT_HEIGHT
            }
            backgroundOpen = shapes.roundedRect(tokens.radiusMd, tokens.surface, tokens.accent, OUTLINE).also {
                it.leftWidth = FIELD_PADDING
                it.rightWidth = FIELD_PADDING
                it.minHeight = INPUT_HEIGHT
            }
            scrollStyle = scroll
            listStyle = list
        }, SelectBox.SelectBoxStyle::class.java)
    }

    private fun size(drawable: Drawable, size: Float) {
        drawable.minWidth = size
        drawable.minHeight = size
    }

    private fun label(name: String, font: BitmapFont, color: Color) {
        skin.add(name, Label.LabelStyle(font, color), Label.LabelStyle::class.java)
    }

    override fun dispose() {
        fonts.dispose()
        shapes.dispose()
    }

    private companion object {
        const val LOGO_SIZE = 28f
        const val LOGO_RING = 2.5f
        const val LOGO_DOT = 8f
        const val OUTLINE = 2f
        const val TILE_BORDER = 1.5f
        const val FIELD_PADDING = 24f
        const val CURSOR_WIDTH = 2f
        const val SELECTION_ALPHA = 0.35f
        const val INPUT_HEIGHT = 56f
        const val INPUT_LG_HEIGHT = 88f
        const val TRACK_HEIGHT = 6f
        const val STEP_HEIGHT = 6f
        const val KNOB_SIZE = 22f
        const val CHECK_SIZE = 26f
        const val LIST_PADDING = 16f
        const val LIST_ITEM_PADDING = 10f
    }
}
