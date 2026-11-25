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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.bombgrid.BOMB_GRID_COLS
import com.jasmeet.pixeldrop.bombgrid.BOMB_GRID_ROWS
import com.jasmeet.pixeldrop.bombgrid.BombGridState
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.components.retroPad
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.min

/** BOMB GRID LCD: 15x11 arena left, SCORE/LIVES/GRID/BOMBS panel right. */
@Composable
fun BombGridScreen(state: BombGridState, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val ink = PixelDropColors.LcdDark
        val pad = size.width * 0.035f
        val cell = min((size.width * 0.77f - pad * 2) / BOMB_GRID_COLS, (size.height - pad * 2) / BOMB_GRID_ROWS)
        val fieldW = cell * BOMB_GRID_COLS
        val fieldH = cell * BOMB_GRID_ROWS
        val left = pad
        val top = (size.height - fieldH) / 2f
        fun x(col: Int) = left + col * cell
        fun y(row: Int) = top + row * cell

        for (row in 0 until BOMB_GRID_ROWS) for (col in 0 until BOMB_GRID_COLS) {
            if (isHardCell(col, row)) {
                drawRect(ink, Offset(x(col) + cell * 0.06f, y(row) + cell * 0.06f), Size(cell * 0.88f, cell * 0.88f))
                drawRect(PixelDropColors.LcdGreen.copy(alpha = 0.35f), Offset(x(col) + cell * 0.28f, y(row) + cell * 0.28f), Size(cell * 0.44f, cell * 0.44f))
            } else {
                drawRect(ink.copy(alpha = 0.10f), Offset(x(col), y(row)), Size(cell, cell), style = Stroke(cell * 0.04f))
            }
        }
        for (crate in state.crates) drawCrate(x(crate.x), y(crate.y), cell)
        for (bomb in state.bombs) {
            val pulse = if ((bomb.fuseMs / 140L) % 2L == 0L) 0.30f else 0.22f
            drawCircle(ink, cell * pulse, Offset(x(bomb.cell.x) + cell * 0.5f, y(bomb.cell.y) + cell * 0.56f))
            drawLine(ink, Offset(x(bomb.cell.x) + cell * 0.5f, y(bomb.cell.y) + cell * 0.28f), Offset(x(bomb.cell.x) + cell * 0.66f, y(bomb.cell.y) + cell * 0.12f), cell * 0.08f)
        }
        for (blast in state.blasts) {
            val bx = x(blast.cell.x)
            val by = y(blast.cell.y)
            drawRect(ink, Offset(bx + cell * 0.08f, by + cell * 0.38f), Size(cell * 0.84f, cell * 0.24f))
            drawRect(ink, Offset(bx + cell * 0.38f, by + cell * 0.08f), Size(cell * 0.24f, cell * 0.84f))
            drawRect(PixelDropColors.LcdGreen, Offset(bx + cell * 0.42f, by + cell * 0.42f), Size(cell * 0.16f, cell * 0.16f))
        }
        for (hunter in state.hunters) drawHunter(x(hunter.cell.x), y(hunter.cell.y), cell)
        // Blink through the post-hit grace window.
        if (state.invulnerableMs == 0L || (state.invulnerableMs / 110L) % 2L == 0L) drawBomber(x(state.player.x), y(state.player.y), cell)

        val panelX = left + fieldW + pad * 0.75f
        val px = (size.width - panelX - pad) / 49f
        var sy = top + cell * 0.25f
        fun stat(label: String, value: String) {
            drawPixelText(label, Offset(panelX, sy), px, ink); sy += px * 9.5f
            drawPixelText(value, Offset(panelX, sy), px, ink); sy += px * 17f
        }
        stat("SCORE", retroPad(state.score, 5))
        stat("LIVES", retroPad(state.lives, 2))
        stat("GRID", retroPad(state.grid, 2))
        stat("BOMBS", "${state.bombs.size}/${state.bombCapacity}")
        if (state.currentChain > 0) drawPixelText("CHAIN X${state.currentChain}", Offset(panelX, top + fieldH - px * 8f), px, ink)
        if (state.shieldHits > 0) drawPixelText("SHIELD", Offset(panelX, top + fieldH - px * 18f), px, ink)
    }
}

private fun isHardCell(x: Int, y: Int): Boolean =
    x == 0 || y == 0 || x == BOMB_GRID_COLS - 1 || y == BOMB_GRID_ROWS - 1 || (x % 2 == 0 && y % 2 == 0)

private fun DrawScope.drawCrate(x: Float, y: Float, cell: Float) {
    val ink = PixelDropColors.LcdDark
    val gap = cell * 0.12f
    drawRect(ink, Offset(x + gap, y + gap), Size(cell - gap * 2, cell - gap * 2))
    drawLine(PixelDropColors.LcdGreen.copy(alpha = 0.55f), Offset(x + gap, y + cell * 0.5f), Offset(x + cell - gap, y + cell * 0.5f), gap)
    drawLine(PixelDropColors.LcdGreen.copy(alpha = 0.55f), Offset(x + cell * 0.5f, y + gap), Offset(x + cell * 0.5f, y + cell - gap), gap)
}

private fun DrawScope.drawHunter(x: Float, y: Float, cell: Float) {
    val ink = PixelDropColors.LcdDark
    drawRect(ink, Offset(x + cell * 0.20f, y + cell * 0.25f), Size(cell * 0.60f, cell * 0.58f))
    drawRect(ink, Offset(x + cell * 0.10f, y + cell * 0.45f), Size(cell * 0.80f, cell * 0.25f))
    drawRect(PixelDropColors.LcdGreen, Offset(x + cell * 0.30f, y + cell * 0.42f), Size(cell * 0.12f, cell * 0.12f))
    drawRect(PixelDropColors.LcdGreen, Offset(x + cell * 0.58f, y + cell * 0.42f), Size(cell * 0.12f, cell * 0.12f))
}

private fun DrawScope.drawBomber(x: Float, y: Float, cell: Float) {
    val ink = PixelDropColors.LcdDark
    drawRect(ink, Offset(x + cell * 0.28f, y + cell * 0.16f), Size(cell * 0.44f, cell * 0.68f))
    drawRect(ink, Offset(x + cell * 0.16f, y + cell * 0.38f), Size(cell * 0.68f, cell * 0.34f))
    drawRect(PixelDropColors.LcdGreen, Offset(x + cell * 0.54f, y + cell * 0.32f), Size(cell * 0.12f, cell * 0.12f))
}

/**
 * State has no death-cause field, so it is derived: the killing tick leaves
 * the blast that caught the player in `blasts`, a hunter kill on a quiet
 * board leaves none. A long chain plus a blast reads as the chain biting back.
 */
internal fun bombGridHeadline(blastActive: Boolean, bestChain: Int): String = when {
    blastActive && bestChain >= 3 -> "BACKFIRED"
    blastActive -> "BLASTED"
    else -> "HUNTED"
}

/** Game-over LCD: cause-aware headline, final stats, blinking retry hint. */
@Composable
fun BombGridGameOverScreen(state: BombGridState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bomb-gameover-blink")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "blink",
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val ink = PixelDropColors.LcdDark

        drawCornerMarks()
        drawPixelTextCentered("BOMB GRID", w / 2f, h * 0.14f, w / 105f, ink)
        drawPixelTextCentered(bombGridHeadline(state.blasts.isNotEmpty(), state.bestChain), w / 2f, h * 0.24f, w / 72f, ink)

        val px = w / 85f
        val labelX = w * 0.12f
        val valueRight = w * 0.88f
        val rows = listOf(
            "SCORE" to retroPad(state.score, 6),
            "CHAIN" to "X${retroPad(state.bestChain, 2)}",
            "GRID" to retroPad(state.grid, 2),
            "KILLS" to retroPad(state.enemiesDestroyed, 3),
        )
        rows.forEachIndexed { i, (label, value) ->
            val y = h * 0.38f + i * h * 0.095f
            drawPixelText(label, Offset(labelX, y), px, ink)
            drawPixelTextRightAligned(value, valueRight, y, px, ink)
        }

        if (phase < 0.62f) {
            drawPixelTextCentered("A LIGHT FUSE", w / 2f, h * 0.78f, w / 110f, ink)
        }
        drawPixelTextCentered("B MENU", w / 2f, h * 0.86f, w / 120f, ink.copy(alpha = 0.8f))
    }
}
