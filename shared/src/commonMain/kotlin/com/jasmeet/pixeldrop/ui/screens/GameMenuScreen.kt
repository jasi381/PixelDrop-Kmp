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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.game.GAMES
import com.jasmeet.pixeldrop.game.GameId
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.components.pixelTextCellWidth
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.min

/** CANVAS CLASSICS menu LCD: header, game list with an inverse selection
 *  bar, and a small live preview of whichever game is highlighted. */
@Composable
fun GameMenuScreen(selected: GameId, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "menu-blink")
    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "blink",
    )
    Canvas(modifier.fillMaxSize()) {
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
        // LCD once the sixth classic landed. The window slides so the
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
        drawGamePreview(selected, panelLeft, panelTop, panelRight, panelBottom, ink, blinkPhase)

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

/** Bordered mini-LCD box with a faint grid and the selected game's preview. */
private fun DrawScope.drawGamePreview(
    selected: GameId,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    ink: Color,
    blinkPhase: Float,
) {
    val cols = 9
    val rows = 16
    val availW = right - left
    val availH = bottom - top
    val cell = min(availW / cols, availH / rows)
    val fieldW = cell * cols
    val fieldH = cell * rows
    val x0 = left + (availW - fieldW) / 2f
    val y0 = top + (availH - fieldH) / 2f

    drawRect(ink, Offset(x0, y0), Size(fieldW, fieldH), style = Stroke(width = cell * 0.12f))
    val grid = ink.copy(alpha = 0.14f)
    for (c in 1 until cols) drawRect(grid, Offset(x0 + c * cell, y0), Size(1f, fieldH))
    for (r in 1 until rows) drawRect(grid, Offset(x0, y0 + r * cell), Size(fieldW, 1f))

    when (selected) {
        GameId.PixelDrop -> drawPixelDropPreview(x0, y0, cell, ink)
        GameId.PixelTrail -> drawPixelTrailPreview(x0, y0, cell, ink, blinkPhase)
        GameId.BrickPulse -> drawBrickPulsePreview(x0, y0, cell, ink)
        GameId.OrbitBlaster -> drawOrbitBlasterPreview(x0, y0, cell, ink)
        GameId.MeteorDodge -> drawMeteorDodgePreview(x0, y0, cell, ink)
        GameId.PixelRacer -> drawPixelRacerPreview(x0, y0, cell, ink, blinkPhase)
        GameId.GalaxyMiner -> drawGalaxyMinerPreview(x0, y0, cell, ink, blinkPhase)
    }
}

private fun DrawScope.drawGalaxyMinerPreview(x0: Float, y0: Float, cell: Float, ink: Color, blink: Float) {
    // Crystal belt, refinery ring and a small triangular mining craft.
    listOf(3 to 2, 5 to 7, 8 to 3).forEach { (row, column) ->
        fillPreviewCell(x0, y0, cell, row, column, ink, if (blink < 0.65f) 1f else 0.55f)
    }
    listOf(6 to 4, 6 to 5, 7 to 4, 7 to 5).forEach { (row, column) -> fillPreviewCell(x0, y0, cell, row, column, ink) }
    for (column in 3..5) fillPreviewCell(x0, y0, cell, 14, column, ink, 0.55f)
    fillPreviewCell(x0, y0, cell, 12, 4, ink)
    fillPreviewCell(x0, y0, cell, 13, 3, ink)
    fillPreviewCell(x0, y0, cell, 13, 5, ink)
}

private fun DrawScope.fillPreviewCell(x0: Float, y0: Float, cell: Float, r: Int, c: Int, ink: Color, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val gap = cell * 0.12f
    drawRect(
        color = if (alpha < 1f) ink.copy(alpha = alpha) else ink,
        topLeft = Offset(x0 + c * cell + gap, y0 + r * cell + gap),
        size = Size(cell - gap * 2, cell - gap * 2),
    )
}

private fun DrawScope.drawPixelDropPreview(x0: Float, y0: Float, cell: Float, ink: Color) {
    val stack = listOf(
        15 to 0, 15 to 1, 15 to 2, 15 to 3, 15 to 4, 15 to 5, 15 to 6, 15 to 7, 15 to 8,
        14 to 0, 14 to 1, 14 to 2, 14 to 4, 14 to 5, 14 to 7, 14 to 8,
        13 to 1, 13 to 4, 13 to 7,
    )
    val falling = listOf(6 to 5, 7 to 4, 7 to 5)
    (stack + falling).forEach { (r, c) -> fillPreviewCell(x0, y0, cell, r, c, ink) }
}

private fun DrawScope.drawPixelTrailPreview(x0: Float, y0: Float, cell: Float, ink: Color, blinkPhase: Float) {
    val trail = listOf(
        5 to 1, 5 to 2, 5 to 3, 5 to 4, 5 to 5,
        6 to 5, 7 to 5, 8 to 5, 9 to 5,
        9 to 4,
    )
    trail.forEach { (r, c) -> fillPreviewCell(x0, y0, cell, r, c, ink) }
    // Blinking food/data dot.
    val dotAlpha = if (blinkPhase < 0.6f) 1f else 0f
    fillPreviewCell(x0, y0, cell, 3, 7, ink, alpha = dotAlpha)
}

private fun DrawScope.drawBrickPulsePreview(x0: Float, y0: Float, cell: Float, ink: Color) {
    for (c in 0 until 9) {
        fillPreviewCell(x0, y0, cell, 2, c, ink)
        fillPreviewCell(x0, y0, cell, 3, c, ink)
    }
    fillPreviewCell(x0, y0, cell, 8, 4, ink) // ball
    listOf(3, 4, 5).forEach { c -> fillPreviewCell(x0, y0, cell, 13, c, ink) } // paddle
}

private fun DrawScope.drawOrbitBlasterPreview(x0: Float, y0: Float, cell: Float, ink: Color) {
    val enemyCols = listOf(1, 4, 7)
    enemyCols.forEach { c ->
        fillPreviewCell(x0, y0, cell, 1, c, ink)
        fillPreviewCell(x0, y0, cell, 2, c, ink)
    }
    // Tracer dashes climbing from the ship toward the enemies.
    listOf(5, 7, 9, 11).forEach { r -> fillPreviewCell(x0, y0, cell, r, 4, ink, alpha = 0.6f) }
    // Player ship.
    fillPreviewCell(x0, y0, cell, 14, 4, ink)
    listOf(3, 4, 5).forEach { c -> fillPreviewCell(x0, y0, cell, 15, c, ink) }
}

private fun DrawScope.drawMeteorDodgePreview(x0: Float, y0: Float, cell: Float, ink: Color) {
    val meteorCenters = listOf(2 to 7, 4 to 2, 6 to 6, 8 to 3, 10 to 6)
    meteorCenters.forEach { (r, c) ->
        fillPreviewCell(x0, y0, cell, r, c, ink)
        fillPreviewCell(x0, y0, cell, r - 1, c, ink)
        fillPreviewCell(x0, y0, cell, r + 1, c, ink)
        fillPreviewCell(x0, y0, cell, r, c - 1, ink)
        fillPreviewCell(x0, y0, cell, r, c + 1, ink)
    }
    fillPreviewCell(x0, y0, cell, 14, 4, ink) // player ship
    listOf(3, 4, 5).forEach { c -> fillPreviewCell(x0, y0, cell, 15, c, ink) }
}

/** Mini replica of the in-game road: thin shoulders at the game's road edges,
 *  three dashed lane dividers (four lanes), sprite-style cars, ring coin. */
private fun DrawScope.drawPixelRacerPreview(x0: Float, y0: Float, cell: Float, ink: Color, blinkPhase: Float) {
    val fieldW = cell * 9
    val fieldH = cell * 16
    // Map the game's 100-unit-wide field onto the preview box.
    val sx = fieldW / 100f
    fun rx(x: Float) = x0 + x * sx

    // Shoulders at the road edges (game coords 10..90).
    drawRect(ink, Offset(rx(10f) - sx * 2f, y0), Size(sx * 2f, fieldH))
    drawRect(ink, Offset(rx(90f), y0), Size(sx * 2f, fieldH))

    // Three dashed dividers -> four lanes, same as the game road.
    for (lane in 1 until 4) {
        val lx = rx(10f + lane * 20f)
        var dy = cell * 0.3f
        while (dy < fieldH - cell * 0.6f) {
            drawRect(ink.copy(alpha = 0.45f), Offset(lx - sx, y0 + dy), Size(sx * 2f, cell * 0.6f))
            dy += cell * 1.4f
        }
    }

    // Oncoming traffic in three lanes (lane centers 20/40/60/80).
    drawPreviewRacerCar(rx(20f), y0 + fieldH * 0.20f, sx, ink)
    drawPreviewRacerCar(rx(60f), y0 + fieldH * 0.40f, sx, ink)
    drawPreviewRacerCar(rx(40f), y0 + fieldH * 0.62f, sx, ink)

    // Blinking ring coin ahead in the player's lane.
    if (blinkPhase < 0.6f) {
        val c = Offset(rx(80f), y0 + fieldH * 0.5f)
        drawCircle(ink, radius = sx * 3.4f, center = c, style = Stroke(width = sx * 1.4f))
        drawCircle(ink, radius = sx * 1.1f, center = c)
    }

    // Player car near the bottom, matching RACER_PLAYER_Y's spot on the road.
    drawPreviewRacerCar(rx(80f), y0 + fieldH * 0.86f, sx, ink)
}

/** Shrunk copy of the in-game car sprite: hull, cabin notch, four wheel studs. */
private fun DrawScope.drawPreviewRacerCar(cx: Float, cy: Float, sx: Float, ink: Color) {
    val w = 12f * sx
    val h = 16f * sx
    drawRect(ink, Offset(cx - w * 0.32f, cy - h * 0.46f), Size(w * 0.64f, h * 0.92f))
    drawRect(
        PixelDropColors.LcdGreen.copy(alpha = 0.55f),
        Offset(cx - w * 0.18f, cy - h * 0.16f),
        Size(w * 0.36f, h * 0.3f),
    )
    val wheelW = w * 0.14f
    val wheelH = h * 0.2f
    drawRect(ink, Offset(cx - w * 0.5f, cy - h * 0.4f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx + w * 0.5f - wheelW, cy - h * 0.4f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx - w * 0.5f, cy + h * 0.2f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx + w * 0.5f - wheelW, cy + h * 0.2f), Size(wheelW, wheelH))
}

/** Placeholder LCD for menu slots that don't have a game wired up yet. */
@Composable
fun ComingSoonScreen(gameId: GameId, modifier: Modifier = Modifier) {
    val title = GAMES.first { it.id == gameId }.title
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val ink = PixelDropColors.LcdDark

        drawCornerMarks()
        // Sized for the longest title ("ORBIT BLASTER") to stay clear of the
        // LCD edges — the same glyph-width-vs-px mismatch as the menu list.
        drawPixelTextCentered(title, w / 2f, h * 0.32f, w / 95f, ink)
        drawPixelTextCentered("COMING SOON", w / 2f, h * 0.46f, w / 105f, ink)
        drawPixelTextCentered("B: MENU", w / 2f, h * 0.62f, w / 120f, ink.copy(alpha = 0.8f))
    }
}
