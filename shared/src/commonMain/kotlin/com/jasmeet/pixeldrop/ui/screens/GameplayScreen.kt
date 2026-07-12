package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.game.Board
import com.jasmeet.pixeldrop.game.GameState
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.min
import com.jasmeet.pixeldrop.ui.components.retroPad

/** Gameplay LCD: 10x20 playfield left, SCORE/LEVEL/LINES/NEXT panel right. */
@Composable
fun GameplayScreen(state: GameState, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.05f
        val ink = PixelDropColors.LcdDark

        // Playfield geometry.
        val cell = min((h - pad * 2) / Board.HEIGHT, (w * 0.60f - pad) / Board.WIDTH)
        val fieldW = cell * Board.WIDTH
        val fieldH = cell * Board.HEIGHT
        val fx = pad
        val fy = (h - fieldH) / 2f

        // Border.
        drawRect(
            color = ink,
            topLeft = Offset(fx - cell * 0.15f, fy - cell * 0.15f),
            size = Size(fieldW + cell * 0.3f, fieldH + cell * 0.3f),
            style = Stroke(width = cell * 0.14f),
        )

        // Subtle grid.
        val grid = ink.copy(alpha = 0.10f)
        for (c in 1 until Board.WIDTH) {
            drawRect(grid, Offset(fx + c * cell, fy), Size(1.2f, fieldH))
        }
        for (r in 1 until Board.HEIGHT) {
            drawRect(grid, Offset(fx, fy + r * cell), Size(fieldW, 1.2f))
        }

        // Locked cells.
        for (r in 0 until Board.HEIGHT) {
            for (c in 0 until Board.WIDTH) {
                if (state.board[r][c] != 0) drawLcdBlock(fx + c * cell, fy + r * cell, cell)
            }
        }
        // Active piece.
        state.current.cells().forEach { (r, c) ->
            if (r >= 0) drawLcdBlock(fx + c * cell, fy + r * cell, cell)
        }

        // Stats panel.
        val panelX = fx + fieldW + pad * 0.9f
        val panelW = w - panelX - pad * 0.7f
        val px = panelW / 58f
        val lineH = px * 10f

        var y = fy + cell * 0.3f
        drawPixelText("SCORE", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.score, 6), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("LEVEL", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.level, 2), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("LINES", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.lines, 3), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("NEXT", Offset(panelX, y), px, ink)
        y += lineH * 1.2f

        // Next preview box.
        val previewCell = panelW / 8f
        val boxW = previewCell * 5f
        val boxH = previewCell * 3.4f
        drawRect(ink, Offset(panelX, y), Size(boxW, boxH), style = Stroke(width = px))
        val cells = state.next.rotations[0]
        val minR = cells.minOf { it.first }
        val maxR = cells.maxOf { it.first }
        val minC = cells.minOf { it.second }
        val maxC = cells.maxOf { it.second }
        val pw = (maxC - minC + 1) * previewCell
        val ph = (maxR - minR + 1) * previewCell
        val ox = panelX + (boxW - pw) / 2f
        val oy = y + (boxH - ph) / 2f
        cells.forEach { (r, c) ->
            drawLcdBlock(ox + (c - minC) * previewCell, oy + (r - minR) * previewCell, previewCell)
        }
    }
}

/** Classic LCD brick: filled square with a lighter inner notch. */
fun DrawScope.drawLcdBlock(x: Float, y: Float, cell: Float) {
    val ink = PixelDropColors.LcdDark
    val gap = cell * 0.06f
    drawRect(ink, Offset(x + gap, y + gap), Size(cell - gap * 2, cell - gap * 2))
    val inset = cell * 0.24f
    drawRect(
        color = PixelDropColors.LcdGreen.copy(alpha = 0.55f),
        topLeft = Offset(x + inset, y + inset),
        size = Size(cell - inset * 2, cell - inset * 2),
        style = Stroke(width = cell * 0.10f),
    )
}
