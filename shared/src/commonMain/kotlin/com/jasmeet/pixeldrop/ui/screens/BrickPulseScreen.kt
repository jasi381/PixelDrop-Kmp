package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.brickpulse.BALL_SIZE
import com.jasmeet.pixeldrop.brickpulse.BrickPulsePowerUpType
import com.jasmeet.pixeldrop.brickpulse.BrickPulseState
import com.jasmeet.pixeldrop.brickpulse.FIELD_HEIGHT
import com.jasmeet.pixeldrop.brickpulse.FIELD_WIDTH
import com.jasmeet.pixeldrop.brickpulse.PADDLE_HEIGHT
import com.jasmeet.pixeldrop.brickpulse.PADDLE_Y
import com.jasmeet.pixeldrop.brickpulse.POWERUP_SIZE
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlinx.coroutines.delay
import kotlin.math.min
import com.jasmeet.pixeldrop.ui.components.retroPad

/** BRICK PULSE LCD: paddle-ball arena left, SCORE/LIVES/LEVEL panel right. */
@Composable
fun BrickPulseScreen(state: BrickPulseState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "brick-launch-blink")
    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "blink",
    )

    // Brief "LEVEL 0X" banner whenever the level actually changes — purely a
    // client-side flourish, no engine phase needed for it.
    var lastLevel by remember { mutableIntStateOf(state.level) }
    var showLevelBanner by remember { mutableStateOf(false) }
    LaunchedEffect(state.level) {
        if (state.level != lastLevel) {
            lastLevel = state.level
            showLevelBanner = true
            delay(900)
            showLevelBanner = false
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.05f
        val ink = PixelDropColors.LcdDark

        // Uniform scale keeps the ball round and bounce angles correct,
        // same field-fit approach as PIXEL DROP's and PIXEL TRAIL's grids.
        val scale = min((h - pad * 2) / FIELD_HEIGHT, (w * 0.62f - pad) / FIELD_WIDTH)
        val fieldW = scale * FIELD_WIDTH
        val fieldH = scale * FIELD_HEIGHT
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

        state.bricks.forEach { brick ->
            if (!brick.isAlive) return@forEach
            val gap = scale * 0.4f
            val topLeft = Offset(mapX(brick.bounds.left) + gap, mapY(brick.bounds.top) + gap)
            val brickSize = Size(
                (brick.bounds.right - brick.bounds.left) * scale - gap * 2,
                (brick.bounds.bottom - brick.bounds.top) * scale - gap * 2,
            )
            when {
                // Damaged strong brick: hollow/outlined — reads as "cracked".
                brick.isDamaged -> drawRect(ink, topLeft, brickSize, style = Stroke(width = scale * 0.35f))
                // Undamaged strong brick: solid fill plus an inset second
                // border for a denser, "double-border" look.
                brick.maxHits > 1 -> {
                    drawRect(ink, topLeft, brickSize)
                    val inset = scale * 0.5f
                    drawRect(
                        color = PixelDropColors.LcdGreen.copy(alpha = 0.5f),
                        topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
                        size = Size(brickSize.width - inset * 2, brickSize.height - inset * 2),
                        style = Stroke(width = scale * 0.18f),
                    )
                }
                else -> drawRect(ink, topLeft, brickSize)
            }
        }

        // Fading break sparks.
        state.effects.forEach { effect ->
            val t = (effect.age / effect.maxAge).coerceIn(0f, 1f)
            val sparkSize = scale * BALL_SIZE * (1f + t)
            drawRect(
                color = ink.copy(alpha = (1f - t) * 0.7f),
                topLeft = Offset(mapX(effect.x) - sparkSize / 2f, mapY(effect.y) - sparkSize / 2f),
                size = Size(sparkSize, sparkSize),
            )
        }

        // Falling powerups: a small outlined capsule with a letter inside.
        state.powerUps.forEach { powerUp ->
            val half = POWERUP_SIZE / 2f
            val left = mapX(powerUp.x - half)
            val top = mapY(powerUp.y - half)
            val puSize = POWERUP_SIZE * scale
            drawRect(ink, Offset(left, top), Size(puSize, puSize), style = Stroke(width = scale * 0.3f))
            drawPixelTextCentered(letterFor(powerUp.type), mapX(powerUp.x), top + puSize * 0.24f, scale * 0.34f, ink)
        }

        val paddleLeft = mapX(state.paddleX - state.paddleWidth / 2f)
        drawRect(
            color = ink,
            topLeft = Offset(paddleLeft, mapY(PADDLE_Y)),
            size = Size(state.paddleWidth * scale, PADDLE_HEIGHT * scale),
        )

        state.balls.forEach { ball ->
            val ballLeft = mapX(ball.x - BALL_SIZE / 2f)
            val ballTop = mapY(ball.y - BALL_SIZE / 2f)
            drawRect(color = ink, topLeft = Offset(ballLeft, ballTop), size = Size(BALL_SIZE * scale, BALL_SIZE * scale))
        }

        if (state.balls.any { it.attached } && blinkPhase < 0.65f) {
            drawPixelTextCentered("PRESS A", fx + fieldW / 2f, mapY(PADDLE_Y) - scale * 9f, scale * 0.42f, ink)
        }

        if (showLevelBanner) {
            val bannerH = scale * 14f
            val bannerY = fy + fieldH / 2f - bannerH / 2f
            drawRect(ink, Offset(fx, bannerY), Size(fieldW, bannerH))
            drawPixelTextCentered(
                "LEVEL ${retroPad(state.level, 2)}", fx + fieldW / 2f, bannerY + bannerH * 0.28f,
                scale * 0.55f, PixelDropColors.LcdGreen,
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
        drawPixelText("LIVES", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.lives, 2), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("LEVEL", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.level, 2), Offset(panelX, y), px, ink)

        state.activeEffects.forEach { effect ->
            val label = when (effect.type) {
                BrickPulsePowerUpType.WidePaddle -> "WIDE"
                BrickPulsePowerUpType.SlowBall -> "SLOW"
                else -> null
            } ?: return@forEach
            y += lineH * 1.9f
            val secs = (effect.remainingMs / 1000L).coerceAtLeast(0L)
            drawPixelText("$label ${retroPad(secs, 2)}", Offset(panelX, y), px, ink)
        }
    }
}

private fun letterFor(type: BrickPulsePowerUpType): String = when (type) {
    BrickPulsePowerUpType.WidePaddle -> "W"
    BrickPulsePowerUpType.SlowBall -> "S"
    BrickPulsePowerUpType.ExtraLife -> "+"
    BrickPulsePowerUpType.MultiBall -> "M"
}

/** Game-over LCD: headline, final stats, blinking retry hint. */
@Composable
fun BrickPulseGameOverScreen(state: BrickPulseState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "brick-gameover-blink")
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
        drawPixelTextCentered("BRICK PULSE", w / 2f, h * 0.14f, w / 95f, ink)
        drawPixelTextCentered("GAME OVER", w / 2f, h * 0.24f, w / 62f, ink)

        val px = w / 85f
        val labelX = w * 0.12f
        val valueRight = w * 0.88f
        val rows = listOf(
            "SCORE" to retroPad(state.score, 4),
            "LEVEL" to retroPad(state.level, 2),
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
