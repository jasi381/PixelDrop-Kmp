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
import com.jasmeet.pixeldrop.meteordodge.METEOR_BONUS_SIZE
import com.jasmeet.pixeldrop.meteordodge.METEOR_FIELD_HEIGHT
import com.jasmeet.pixeldrop.meteordodge.METEOR_FIELD_WIDTH
import com.jasmeet.pixeldrop.meteordodge.METEOR_PLAYER_HEIGHT
import com.jasmeet.pixeldrop.meteordodge.METEOR_PLAYER_WIDTH
import com.jasmeet.pixeldrop.meteordodge.MeteorBonusType
import com.jasmeet.pixeldrop.meteordodge.MeteorDodgeState
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.min
import com.jasmeet.pixeldrop.ui.components.retroPad

/** METEOR DODGE LCD: survival arena left, SCORE/TIME/SPD panel right. */
@Composable
fun MeteorDodgeScreen(state: MeteorDodgeState, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.05f
        val ink = PixelDropColors.LcdDark

        val scale = min((h - pad * 2) / METEOR_FIELD_HEIGHT, (w * 0.62f - pad) / METEOR_FIELD_WIDTH)
        val fieldW = scale * METEOR_FIELD_WIDTH
        val fieldH = scale * METEOR_FIELD_HEIGHT
        val fx = pad
        val fy = (h - fieldH) / 2f
        fun mapX(x: Float) = fx + x * scale
        fun mapY(y: Float) = fy + y * scale

        drawRect(
            color = ink,
            topLeft = Offset(fx - scale * 0.5f, fy - scale * 0.5f),
            size = Size(fieldW + scale, fieldH + scale),
            style = Stroke(width = scale * 0.45f),
        )

        state.meteors.forEach { meteor ->
            drawMeteor(mapX(meteor.x), mapY(meteor.y), meteor.radius * scale, meteor.id, ink)
        }

        // Bonus dots blink off the survival clock — state-driven, no logic
        // owned by the draw pass.
        val blinkOn = (state.survivedMs / 250L) % 2L == 0L
        state.bonuses.forEach { bonus ->
            val half = METEOR_BONUS_SIZE / 2f
            val puSize = METEOR_BONUS_SIZE * scale
            when (bonus.type) {
                MeteorBonusType.Score -> if (blinkOn) {
                    drawRect(ink, Offset(mapX(bonus.x - half), mapY(bonus.y - half)), Size(puSize, puSize))
                }
                MeteorBonusType.Shield -> {
                    drawRect(
                        ink, Offset(mapX(bonus.x - half), mapY(bonus.y - half)), Size(puSize, puSize),
                        style = Stroke(width = scale * 0.35f),
                    )
                    if (blinkOn) {
                        drawRect(
                            ink,
                            Offset(mapX(bonus.x) - scale * 0.5f, mapY(bonus.y) - scale * 0.5f),
                            Size(scale, scale),
                        )
                    }
                }
            }
        }

        drawDodgeShip(mapX(state.playerX), mapY(state.playerY), scale, ink)
        if (state.shieldActive) {
            drawRect(
                color = ink.copy(alpha = 0.8f),
                topLeft = Offset(
                    mapX(state.playerX - METEOR_PLAYER_WIDTH / 2f - 1.4f),
                    mapY(state.playerY - METEOR_PLAYER_HEIGHT / 2f - 1.4f),
                ),
                size = Size((METEOR_PLAYER_WIDTH + 2.8f) * scale, (METEOR_PLAYER_HEIGHT + 2.8f) * scale),
                style = Stroke(width = scale * 0.35f),
            )
        }

        // Stats panel.
        val panelX = fx + fieldW + pad * 0.9f
        val panelW = w - panelX - pad * 0.7f
        val px = panelW / 58f
        val lineH = px * 10f

        var y = fy + scale * 0.3f
        drawPixelText("SCORE", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.score, 4), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("TIME", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText("${retroPad(state.survivedMs / 1000L, 2)}S", Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("SPD", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.speedLevel, 2), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText(if (state.dashCooldownMs <= 0L) "DS OK" else "DS ..", Offset(panelX, y), px, ink)
        if (state.shieldActive) {
            y += lineH * 1.9f
            drawPixelText("SH ON", Offset(panelX, y), px, ink)
        }
    }
}

/** Original rock cluster: a center slab plus satellite pixels, the layout
 *  picked deterministically from the meteor's id so each rock keeps its own
 *  stable shape while falling. */
private fun DrawScope.drawMeteor(cx: Float, cy: Float, r: Float, id: Int, ink: Color) {
    val core = r * 1.1f
    drawRect(ink, Offset(cx - core / 2f, cy - core / 2f), Size(core, core))
    val chunk = r * 0.55f
    when (id % 3) {
        0 -> {
            drawRect(ink, Offset(cx - r, cy - chunk / 2f), Size(chunk, chunk))
            drawRect(ink, Offset(cx + r - chunk, cy - r * 0.2f), Size(chunk, chunk))
            drawRect(ink, Offset(cx - chunk / 2f, cy - r), Size(chunk, chunk))
        }
        1 -> {
            drawRect(ink, Offset(cx - r, cy - r * 0.6f), Size(chunk, chunk))
            drawRect(ink, Offset(cx + r - chunk, cy + r * 0.1f), Size(chunk, chunk))
            drawRect(ink, Offset(cx - chunk / 2f, cy + r - chunk), Size(chunk, chunk))
        }
        else -> {
            drawRect(ink, Offset(cx + r - chunk, cy - r * 0.7f), Size(chunk, chunk))
            drawRect(ink, Offset(cx - r, cy + r * 0.2f), Size(chunk, chunk))
        }
    }
}

/** Original dodge ship: squat block with a nose stud and two side fins. */
private fun DrawScope.drawDodgeShip(cx: Float, cy: Float, scale: Float, ink: Color) {
    val w = METEOR_PLAYER_WIDTH * scale
    val h = METEOR_PLAYER_HEIGHT * scale
    // Hull.
    drawRect(ink, Offset(cx - w * 0.3f, cy - h * 0.3f), Size(w * 0.6f, h * 0.72f))
    // Nose stud.
    drawRect(ink, Offset(cx - w * 0.1f, cy - h * 0.5f), Size(w * 0.2f, h * 0.24f))
    // Side fins.
    drawRect(ink, Offset(cx - w / 2f, cy + h * 0.05f), Size(w * 0.16f, h * 0.4f))
    drawRect(ink, Offset(cx + w / 2f - w * 0.16f, cy + h * 0.05f), Size(w * 0.16f, h * 0.4f))
}

/** Game-over LCD: headline, final stats, blinking retry hint. */
@Composable
fun MeteorDodgeGameOverScreen(state: MeteorDodgeState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "meteor-gameover-blink")
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
        drawPixelTextCentered("METEOR DODGE", w / 2f, h * 0.14f, w / 105f, ink)
        drawPixelTextCentered("GAME OVER", w / 2f, h * 0.24f, w / 62f, ink)

        val px = w / 85f
        val labelX = w * 0.12f
        val valueRight = w * 0.88f
        val rows = listOf(
            "SCORE" to retroPad(state.score, 4),
            "TIME" to "${retroPad(state.survivedMs / 1000L, 2)}S",
            "SPEED" to retroPad(state.speedLevel, 2),
        )
        rows.forEachIndexed { i, (label, value) ->
            val y = h * 0.40f + i * h * 0.105f
            drawPixelText(label, Offset(labelX, y), px, ink)
            drawPixelTextRightAligned(value, valueRight, y, px, ink)
        }

        if (phase < 0.62f) {
            drawPixelTextCentered("A/START RETRY", w / 2f, h * 0.72f, w / 110f, ink)
        }
        drawPixelTextCentered("B MENU", w / 2f, h * 0.81f, w / 120f, ink.copy(alpha = 0.8f))
    }
}
