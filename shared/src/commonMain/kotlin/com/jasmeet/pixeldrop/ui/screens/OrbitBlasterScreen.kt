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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.orbitblaster.BOSS_HEIGHT
import com.jasmeet.pixeldrop.orbitblaster.BOSS_WIDTH
import com.jasmeet.pixeldrop.orbitblaster.DANGER_Y
import com.jasmeet.pixeldrop.orbitblaster.ENEMY_HEIGHT
import com.jasmeet.pixeldrop.orbitblaster.ENEMY_WIDTH
import com.jasmeet.pixeldrop.orbitblaster.ORBIT_BULLET_HEIGHT
import com.jasmeet.pixeldrop.orbitblaster.ORBIT_BULLET_WIDTH
import com.jasmeet.pixeldrop.orbitblaster.ORBIT_FIELD_HEIGHT
import com.jasmeet.pixeldrop.orbitblaster.ORBIT_FIELD_WIDTH
import com.jasmeet.pixeldrop.orbitblaster.ORBIT_POWERUP_SIZE
import com.jasmeet.pixeldrop.orbitblaster.OrbitBlasterState
import com.jasmeet.pixeldrop.orbitblaster.OrbitPowerUpType
import com.jasmeet.pixeldrop.orbitblaster.PLAYER_HEIGHT
import com.jasmeet.pixeldrop.orbitblaster.PLAYER_WIDTH
import com.jasmeet.pixeldrop.orbitblaster.PLAYER_Y
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlinx.coroutines.delay
import kotlin.math.min
import com.jasmeet.pixeldrop.ui.components.retroPad

/** ORBIT BLASTER LCD: shooter arena left, SCORE/LIVES/WAVE panel right. */
@Composable
fun OrbitBlasterScreen(state: OrbitBlasterState, modifier: Modifier = Modifier) {
    // Brief "WAVE 0X" banner whenever the wave actually changes — purely a
    // client-side flourish, same pattern as BRICK PULSE's level banner.
    var lastWave by remember { mutableIntStateOf(state.wave) }
    var showWaveBanner by remember { mutableStateOf(false) }
    LaunchedEffect(state.wave) {
        if (state.wave != lastWave) {
            lastWave = state.wave
            showWaveBanner = true
            delay(900)
            showWaveBanner = false
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.05f
        val ink = PixelDropColors.LcdDark

        // Uniform scale keeps shapes square, same field-fit approach as the
        // other games' grids.
        val scale = min((h - pad * 2) / ORBIT_FIELD_HEIGHT, (w * 0.62f - pad) / ORBIT_FIELD_WIDTH)
        val fieldW = scale * ORBIT_FIELD_WIDTH
        val fieldH = scale * ORBIT_FIELD_HEIGHT
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

        // Faint danger line — enemies crossing it cost a hit.
        drawRect(
            color = ink.copy(alpha = 0.25f),
            topLeft = Offset(fx, mapY(DANGER_Y)),
            size = Size(fieldW, scale * 0.5f),
        )

        state.enemies.forEach { enemy ->
            drawDrone(mapX(enemy.x), mapY(enemy.y), scale, ink)
        }

        state.boss?.let { boss ->
            drawBoss(mapX(boss.x), mapY(boss.y), scale, ink)
            // Health pips above the boss, draining right to left.
            val pipW = scale * 1.6f
            val pipGap = scale * 0.6f
            val totalW = boss.maxHealth * pipW + (boss.maxHealth - 1) * pipGap
            val pipsLeft = mapX(boss.x) - totalW / 2f
            val pipsTop = mapY(boss.y - BOSS_HEIGHT / 2f) - scale * 3f
            for (i in 0 until boss.maxHealth) {
                val x = pipsLeft + i * (pipW + pipGap)
                if (i < boss.health) {
                    drawRect(ink, Offset(x, pipsTop), Size(pipW, scale * 1.4f))
                } else {
                    drawRect(ink, Offset(x, pipsTop), Size(pipW, scale * 1.4f), style = Stroke(width = scale * 0.2f))
                }
            }
        }

        (state.playerBullets + state.enemyBullets).forEach { bullet ->
            drawRect(
                color = ink,
                topLeft = Offset(mapX(bullet.x - ORBIT_BULLET_WIDTH / 2f), mapY(bullet.y - ORBIT_BULLET_HEIGHT / 2f)),
                size = Size(ORBIT_BULLET_WIDTH * scale, ORBIT_BULLET_HEIGHT * scale),
            )
        }

        // Expanding, fading pixel-cross bursts.
        state.explosions.forEach { explosion ->
            val t = (explosion.ageMs / 250f).coerceIn(0f, 1f)
            val alpha = (1f - t) * 0.85f
            val arm = scale * (1.5f + t * 3f)
            val thick = scale * 1.2f
            val cx = mapX(explosion.x)
            val cy = mapY(explosion.y)
            drawRect(ink.copy(alpha = alpha), Offset(cx - arm, cy - thick / 2f), Size(arm * 2, thick))
            drawRect(ink.copy(alpha = alpha), Offset(cx - thick / 2f, cy - arm), Size(thick, arm * 2))
        }

        // Falling powerups: outlined capsule with a letter inside.
        state.powerUps.forEach { powerUp ->
            val half = ORBIT_POWERUP_SIZE / 2f
            val left = mapX(powerUp.x - half)
            val top = mapY(powerUp.y - half)
            val puSize = ORBIT_POWERUP_SIZE * scale
            drawRect(ink, Offset(left, top), Size(puSize, puSize), style = Stroke(width = scale * 0.3f))
            val letter = when (powerUp.type) {
                OrbitPowerUpType.Shield -> "S"
                OrbitPowerUpType.Dash -> "D"
            }
            drawPixelTextCentered(letter, mapX(powerUp.x), top + puSize * 0.24f, scale * 0.34f, ink)
        }

        // Ship blinks through the post-hit grace period (state-driven, so
        // the blink needs no clock of its own).
        val shipVisible = state.invulnerableMs <= 0L || (state.invulnerableMs / 150L) % 2L == 0L
        if (shipVisible) {
            drawShip(mapX(state.playerX), mapY(PLAYER_Y), scale, ink)
        }
        if (state.shieldMs > 0L) {
            drawRect(
                color = ink.copy(alpha = 0.8f),
                topLeft = Offset(mapX(state.playerX - PLAYER_WIDTH / 2f - 1.4f), mapY(PLAYER_Y - PLAYER_HEIGHT / 2f - 1.4f)),
                size = Size((PLAYER_WIDTH + 2.8f) * scale, (PLAYER_HEIGHT + 2.8f) * scale),
                style = Stroke(width = scale * 0.35f),
            )
        }

        if (showWaveBanner) {
            val bannerH = scale * 14f
            val bannerY = fy + fieldH / 2f - bannerH / 2f
            drawRect(ink, Offset(fx, bannerY), Size(fieldW, bannerH))
            drawPixelTextCentered(
                "WAVE ${retroPad(state.wave, 2)}", fx + fieldW / 2f, bannerY + bannerH * 0.28f,
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
        drawPixelText("WAVE", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.wave, 2), Offset(panelX, y), px, ink)

        if (state.shieldMs > 0L) {
            y += lineH * 1.9f
            drawPixelText("SH ${retroPad((state.shieldMs / 1000L).coerceAtLeast(0L), 2)}", Offset(panelX, y), px, ink)
        }
        if (state.dashMs > 0L) {
            y += lineH * 1.9f
            drawPixelText("DS ${retroPad((state.dashMs / 1000L).coerceAtLeast(0L), 2)}", Offset(panelX, y), px, ink)
        }
    }
}

/** Original pixel ship: stepped pyramid — nose, canopy, full-width base. */
private fun DrawScope.drawDrone(cx: Float, cy: Float, scale: Float, ink: androidx.compose.ui.graphics.Color) {
    val w = ENEMY_WIDTH * scale
    val h = ENEMY_HEIGHT * scale
    // Body slab.
    drawRect(ink, Offset(cx - w / 2f, cy - h * 0.2f), Size(w, h * 0.5f))
    // Two antenna studs.
    drawRect(ink, Offset(cx - w * 0.32f, cy - h * 0.5f), Size(w * 0.18f, h * 0.3f))
    drawRect(ink, Offset(cx + w * 0.14f, cy - h * 0.5f), Size(w * 0.18f, h * 0.3f))
    // Two dangling legs.
    drawRect(ink, Offset(cx - w * 0.42f, cy + h * 0.3f), Size(w * 0.16f, h * 0.24f))
    drawRect(ink, Offset(cx + w * 0.26f, cy + h * 0.3f), Size(w * 0.16f, h * 0.24f))
}

private fun DrawScope.drawBoss(cx: Float, cy: Float, scale: Float, ink: androidx.compose.ui.graphics.Color) {
    val w = BOSS_WIDTH * scale
    val h = BOSS_HEIGHT * scale
    // Hull.
    drawRect(ink, Offset(cx - w / 2f, cy - h * 0.3f), Size(w, h * 0.6f))
    // Winglets.
    drawRect(ink, Offset(cx - w * 0.62f, cy - h * 0.1f), Size(w * 0.12f, h * 0.35f))
    drawRect(ink, Offset(cx + w * 0.5f, cy - h * 0.1f), Size(w * 0.12f, h * 0.35f))
    // Top turret.
    drawRect(ink, Offset(cx - w * 0.14f, cy - h * 0.5f), Size(w * 0.28f, h * 0.2f))
    // Two "eye" slits punched out in LCD green.
    val eye = Size(w * 0.12f, h * 0.16f)
    drawRect(PixelDropColors.LcdGreen.copy(alpha = 0.6f), Offset(cx - w * 0.28f, cy - h * 0.12f), eye)
    drawRect(PixelDropColors.LcdGreen.copy(alpha = 0.6f), Offset(cx + w * 0.16f, cy - h * 0.12f), eye)
    // Bottom cannon nub.
    drawRect(ink, Offset(cx - w * 0.08f, cy + h * 0.3f), Size(w * 0.16f, h * 0.2f))
}

private fun DrawScope.drawShip(cx: Float, cy: Float, scale: Float, ink: androidx.compose.ui.graphics.Color) {
    val w = PLAYER_WIDTH * scale
    val h = PLAYER_HEIGHT * scale
    // Nose tip.
    drawRect(ink, Offset(cx - w * 0.1f, cy - h * 0.5f), Size(w * 0.2f, h * 0.34f))
    // Canopy.
    drawRect(ink, Offset(cx - w * 0.3f, cy - h * 0.16f), Size(w * 0.6f, h * 0.33f))
    // Full-width base with engine notch.
    drawRect(ink, Offset(cx - w / 2f, cy + h * 0.17f), Size(w, h * 0.33f))
}

/** Game-over LCD: headline, final stats, blinking retry hint. */
@Composable
fun OrbitBlasterGameOverScreen(state: OrbitBlasterState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orbit-gameover-blink")
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
        drawPixelTextCentered("ORBIT BLASTER", w / 2f, h * 0.14f, w / 105f, ink)
        drawPixelTextCentered("GAME OVER", w / 2f, h * 0.24f, w / 62f, ink)

        val px = w / 85f
        val labelX = w * 0.12f
        val valueRight = w * 0.88f
        val rows = listOf(
            "SCORE" to retroPad(state.score, 4),
            "WAVE" to retroPad(state.wave, 2),
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
