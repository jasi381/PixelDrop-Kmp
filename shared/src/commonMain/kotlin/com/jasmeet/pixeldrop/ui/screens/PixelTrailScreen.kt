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
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.pixeltrail.PixelTrailState
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.min
import com.jasmeet.pixeldrop.ui.components.retroPad

/** PIXEL TRAIL LCD: grid playfield left, SCORE/LENGTH/SPEED panel right. */
@Composable
fun PixelTrailScreen(state: PixelTrailState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "trail-food-blink")
    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "blink",
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.05f
        val ink = PixelDropColors.LcdDark

        // Playfield geometry — a square grid, unlike PIXEL DROP's tall well.
        val cell = min((h - pad * 2) / state.gridHeight, (w * 0.62f - pad) / state.gridWidth)
        val fieldW = cell * state.gridWidth
        val fieldH = cell * state.gridHeight
        val fx = pad
        val fy = (h - fieldH) / 2f

        drawRect(
            color = ink,
            topLeft = Offset(fx - cell * 0.15f, fy - cell * 0.15f),
            size = Size(fieldW + cell * 0.3f, fieldH + cell * 0.3f),
            style = Stroke(width = cell * 0.14f),
        )

        val grid = ink.copy(alpha = 0.10f)
        for (c in 1 until state.gridWidth) {
            drawRect(grid, Offset(fx + c * cell, fy), Size(1.2f, fieldH))
        }
        for (r in 1 until state.gridHeight) {
            drawRect(grid, Offset(fx, fy + r * cell), Size(fieldW, 1.2f))
        }

        // Trail: head first. Draw body first, then head on top with its
        // extra center pixel so it isn't overdrawn by the neck segment.
        state.trail.drop(1).forEach { (gx, gy) ->
            drawLcdBlock(fx + gx * cell, fy + gy * cell, cell)
        }
        val head = state.trail.first()
        drawLcdBlock(fx + head.x * cell, fy + head.y * cell, cell)
        drawRect(
            color = ink,
            topLeft = Offset(fx + (head.x + 0.4f) * cell, fy + (head.y + 0.4f) * cell),
            size = Size(cell * 0.2f, cell * 0.2f),
        )

        // Blinking food/data dot.
        if (blinkPhase < 0.75f) {
            val fcx = fx + (state.food.x + 0.5f) * cell
            val fcy = fy + (state.food.y + 0.5f) * cell
            drawRect(
                color = ink,
                topLeft = Offset(fcx - cell * 0.28f, fcy - cell * 0.28f),
                size = Size(cell * 0.56f, cell * 0.56f),
            )
        }

        // Stats panel.
        val panelX = fx + fieldW + pad * 0.9f
        val panelW = w - panelX - pad * 0.7f
        val px = panelW / 58f
        val lineH = px * 10f

        var y = fy + cell * 0.3f
        drawPixelText("SCORE", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.score, 4), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("LENGTH", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.trail.size, 3), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("SPEED", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.speed, 2), Offset(panelX, y), px, ink)
    }
}

/** Game-over LCD: headline, final stats, blinking retry hint. */
@Composable
fun PixelTrailGameOverScreen(state: PixelTrailState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "trail-gameover-blink")
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
        drawPixelTextCentered("PIXEL TRAIL", w / 2f, h * 0.14f, w / 95f, ink)
        drawPixelTextCentered("GAME OVER", w / 2f, h * 0.24f, w / 62f, ink)

        val px = w / 85f
        val labelX = w * 0.12f
        val valueRight = w * 0.88f
        val rows = listOf(
            "SCORE" to retroPad(state.score, 4),
            "LENGTH" to retroPad(state.trail.size, 3),
        )
        rows.forEachIndexed { i, (label, value) ->
            val y = h * 0.42f + i * h * 0.115f
            drawPixelText(label, Offset(labelX, y), px, ink)
            drawPixelTextRightAligned(value, valueRight, y, px, ink)
        }

        if (phase < 0.62f) {
            drawPixelTextCentered("A/START RETRY", w / 2f, h * 0.68f, w / 110f, ink)
        }
        drawPixelTextCentered("B MENU", w / 2f, h * 0.78f, w / 120f, ink.copy(alpha = 0.8f))
    }
}
