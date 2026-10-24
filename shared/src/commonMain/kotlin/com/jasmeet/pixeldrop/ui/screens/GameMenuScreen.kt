package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.game.GAMES
import com.jasmeet.pixeldrop.game.GameId
import com.jasmeet.pixeldrop.game.isPlayable
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.components.pixelTextCellWidth
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import com.jasmeet.pixeldrop.ui.theme.rememberReducedMotion
import kotlin.math.floor
import kotlin.math.min

/** CANVAS CLASSICS menu LCD: header, game list with an inverse selection
 *  bar, and a small live preview of whichever game is highlighted. */
@Composable
fun GameMenuScreen(selected: GameId, modifier: Modifier = Modifier) {
    // One transition drives everything: the preview demos read it as their loop
    // phase and the scroll chevrons derive their blink from it (loop = 4 blinks).
    val transition = rememberInfiniteTransition(label = "menu-demo")
    val loopPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DEMO_LOOP_MS, easing = LinearEasing)),
        label = "demo-loop",
    )
    val painter = remember { DemoPainter() }
    // Animator scale 0 means the user asked for no motion: hold one frame.
    val reducedMotion = rememberReducedMotion()
    Canvas(modifier.fillMaxSize()) {
        val demoT = if (reducedMotion) DEMO_STILL_T else loopPhase.coerceAtMost(0.9999f)
        val blinkPhase = if (reducedMotion) 0f else (loopPhase * (DEMO_LOOP_MS / 900f)) % 1f
        val w = size.width
        val h = size.height
        val ink = PixelDropColors.LcdDark
        val marginX = w * 0.045f

        // Header: title + underline, system subtitle below. Sized off the
        // LCD's own w/h (not a fixed px) so 7-row glyphs never outgrow the
        // row spacing they're placed in.
        val titlePx = w / 118f
        drawPixelText("CANVAS CLASSICS", Offset(marginX, h * 0.035f), titlePx, ink)
        val titleW = pixelTextCellWidth("CANVAS CLASSICS") * titlePx
        drawRect(ink, Offset(marginX, h * 0.035f + 7 * titlePx + titlePx * 0.6f), Size(titleW, titlePx * 0.35f))

        val systemPx = w / 165f
        drawPixelText("PIXEL DROP SYSTEM", Offset(marginX, h * 0.14f), systemPx, ink)

        // Game list: a fixed 4-row viewport that scrolls with the cursor
        // instead of stretching to fit every title — the library outgrew the
        // LCD once the sixth classic landed. The same viewport now carries
        // all eight; the window slides so the
        // selection is always visible; pixel chevrons hint at off-screen
        // rows above/below. Gap between barLeft and listX must clear the ">"
        // glyph (5 cols + lead-in) or the cursor collides with the label text.
        val listPx = w / 136f
        val listX = w * 0.105f
        val barLeft = w * 0.045f
        val rowH = h * 0.095f
        val listTop = h * 0.27f
        val visibleRows = min(MENU_VISIBLE_ROWS, GAMES.size)
        val selectedIndex = GAMES.indexOfFirst { it.id == selected }.coerceAtLeast(0)
        val firstVisible = (selectedIndex - visibleRows + 1)
            .coerceAtLeast(0)
            .coerceAtMost(GAMES.size - visibleRows)
        for (row in 0 until visibleRows) {
            val game = GAMES[firstVisible + row]
            val y = listTop + row * rowH
            if (game.id == selected) {
                val barPad = listPx * 3f
                val barW = (listX - barLeft) + pixelTextCellWidth(game.title) * listPx + barPad
                drawRect(ink, Offset(barLeft, y - listPx * 2.5f), Size(barW, listPx * 10.5f))
                drawPixelText(">", Offset(barLeft + listPx * 1.2f, y), listPx, PixelDropColors.LcdGreen)
                drawPixelText(game.title, Offset(listX, y), listPx, PixelDropColors.LcdGreen)
            } else {
                drawPixelText(game.title, Offset(listX, y), listPx, ink)
            }
        }
        // Scroll chevrons, blinking softly so they read as UI, not content.
        val chevronAlpha = if (blinkPhase < 0.6f) 0.9f else 0.45f
        val chevronX = barLeft + listPx * 2f
        if (firstVisible > 0) {
            drawScrollChevron(chevronX, listTop - rowH * 0.55f, listPx, ink.copy(alpha = chevronAlpha), up = true)
        }
        if (firstVisible + visibleRows < GAMES.size) {
            drawScrollChevron(chevronX, listTop + visibleRows * rowH - rowH * 0.35f, listPx, ink.copy(alpha = chevronAlpha), up = false)
        }

        // Right-side preview panel — kept clear of the longest list label
        // ("ORBIT BLASTER") plus its selection bar.
        val panelLeft = w * 0.72f
        val panelRight = w * 0.965f
        val panelTop = h * 0.20f
        val panelBottom = h * 0.70f
        drawGamePreview(selected, panelLeft, panelTop, panelRight, panelBottom, ink, demoT, painter)

        // Divider + control hints + footer.
        val ruleY = h * 0.74f
        drawRect(ink.copy(alpha = 0.7f), Offset(marginX, ruleY), Size(w - marginX * 2, h * 0.004f))

        val hintPx = w / 175f
        val hintY = h * 0.775f
        drawPixelText("A/START PLAY", Offset(marginX, hintY), hintPx, ink)
        drawPixelTextRightAligned("D-PAD SELECT", w - marginX, hintY, hintPx, ink)

        val footerPx = w / 195f
        drawPixelTextCentered("V0.1 ARCADE MODE", w / 2f, h * 0.86f, footerPx, ink.copy(alpha = 0.85f))
    }
}

/** Rows visible at once in the menu list; the window scrolls past this. */
private const val MENU_VISIBLE_ROWS = 4

/** Three-step pixel triangle pointing up or down — the scroll hint. */
private fun DrawScope.drawScrollChevron(x: Float, y: Float, px: Float, ink: Color, up: Boolean) {
    val step = px * 1.2f
    for (i in 0 until 3) {
        // Up arrow: narrow tip first (1,3,5); down arrow: the reverse.
        val rowW = step * (if (up) 1 + i * 2 else 5 - i * 2)
        val rowY = y + i * step
        drawRect(ink, Offset(x + (step * 5 - rowW) / 2f, rowY), Size(rowW, step))
    }
}

/** Bordered mini-LCD box with a faint grid and the selected game's live demo. */
private fun DrawScope.drawGamePreview(
    selected: GameId,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    ink: Color,
    demoT: Float,
    painter: DemoPainter,
) {
    val cols = DEMO_COLS
    val rows = DEMO_ROWS
    val availW = right - left
    val availH = bottom - top
    val cell = min(availW / cols, availH / rows)
    val fieldW = cell * cols
    val fieldH = cell * rows
    val x0 = floor(left + (availW - fieldW) / 2f)
    val y0 = floor(top + (availH - fieldH) / 2f)

    drawRect(ink, Offset(x0, y0), Size(fieldW, fieldH), style = Stroke(width = cell * 0.12f))
    val grid = ink.copy(alpha = 0.14f)
    for (c in 1 until cols) drawRect(grid, Offset(x0 + c * cell, y0), Size(1f, fieldH))
    for (r in 1 until rows) drawRect(grid, Offset(x0, y0 + r * cell), Size(fieldW, 1f))

    if (selected.isPlayable) painter.draw(this, selected, demoT, x0, y0, cell)
    else drawComingSoonSprite(x0, y0, fieldW, fieldH, ink, demoT)
}

/** Placeholder LCD for teaser slots: the same padlock sprite the menu preview
 *  plays, big on the left, with the exact menu title beside it. */
@Composable
fun ComingSoonScreen(gameId: GameId, modifier: Modifier = Modifier) {
    val title = GAMES.first { it.id == gameId }.title
    val loop = rememberInfiniteTransition(label = "soon-loop")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(DEMO_LOOP_MS, easing = LinearEasing)), label = "soon-t")
    val painter = remember { DemoPainter() }
    val reducedMotion = rememberReducedMotion()
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val ink = PixelDropColors.LcdDark

        drawCornerMarks()
        val cell = floor(h * 0.78f / DEMO_ROWS)
        val x0 = floor(w * 0.1f)
        val y0 = floor((h - cell * DEMO_ROWS) / 2f)
        val t = if (reducedMotion) DEMO_STILL_T else loop.value.coerceAtMost(0.9999f)
        drawComingSoonSprite(x0, y0, cell * DEMO_COLS, cell * DEMO_ROWS, ink, t)

        val textCx = x0 + cell * DEMO_COLS + (w - x0 - cell * DEMO_COLS) / 2f
        drawPixelTextCentered(title, textCx, h * 0.34f, w / 150f, ink)
        drawPixelTextCentered("COMING SOON", textCx, h * 0.48f, w / 190f, ink.copy(alpha = 0.85f))
        drawPixelTextCentered("B: MENU", textCx, h * 0.62f, w / 170f, ink.copy(alpha = 0.8f))
    }
}
