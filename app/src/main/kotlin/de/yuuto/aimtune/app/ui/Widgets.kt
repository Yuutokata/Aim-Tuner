package de.yuuto.aimtune.app.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align

private const val LOGO_SIZE = 28f
private const val LOGO_DOT = 8f
private const val BRAND_GAP = 12f
private const val CONTENT_TOP_PADDING = 64f
private const val BUTTON_HEIGHT = 64f
private const val BUTTON_PADDING = 36f

fun UiSkin.label(text: CharSequence, style: String = Styles.BODY, wrap: Boolean = false): Label =
    Label(text, skin, style).also { it.wrap = wrap }

/** Small uppercase caption above a field or section. The design's 10 % letter-spacing is not reproduced (see DESIGN.md). */
fun UiSkin.caps(text: String): Label = label(text.uppercase(), Styles.LABEL)

fun UiSkin.button(text: String, style: String, onClick: () -> Unit): TextButton =
    TextButton(text, skin, style).also { button ->
        button.pad(0f, BUTTON_PADDING, 0f, BUTTON_PADDING)
        button.onChange(onClick)
    }

fun Actor.onChange(action: () -> Unit) {
    addListener(object : ChangeListener() {
        override fun changed(event: ChangeEvent, actor: Actor) = action()
    })
}

/** Fixed height for buttons in the footer row. */
fun Table.addButton(button: TextButton, minWidth: Float = 0f) =
    add(button).height(BUTTON_HEIGHT).minWidth(minWidth)

/**
 * The common screen layout of the design: a header with logo and brand name (and an optional
 * [headerRight]), the [content] filling the middle, and an optional [footer] row, all inside the
 * 72-unit margins.
 */
fun UiSkin.frame(headerRight: Actor?, content: Actor, footer: Actor?): Table {
    val root = Table()
    root.setFillParent(true)
    root.pad(0f, tokens.margin, 0f, tokens.margin)

    val logo = Stack()
    logo.add(Image(logoRing))
    logo.add(Container(Image(logoDot)).size(LOGO_DOT).center())
    val header = Table()
    header.add(logo).size(LOGO_SIZE)
    header.add(label("AimTune", Styles.BRAND)).padLeft(BRAND_GAP)
    header.add().expandX()
    if (headerRight != null) header.add(headerRight)
    root.add(header).growX().height(tokens.headerHeight).row()

    if (content is Table) content.align(Align.topLeft)
    root.add(content).grow().top().left().padTop(CONTENT_TOP_PADDING).row()
    if (footer != null) root.add(footer).growX().height(tokens.footerHeight)
    return root
}
