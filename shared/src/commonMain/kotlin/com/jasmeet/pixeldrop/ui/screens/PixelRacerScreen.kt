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
import androidx.compose.ui.graphics.drawscope.clipRect
import com.jasmeet.pixeldrop.pixelracer.PixelRacerState
import com.jasmeet.pixeldrop.pixelracer.RACER_FIELD_HEIGHT
import com.jasmeet.pixeldrop.pixelracer.RACER_FIELD_WIDTH
import com.jasmeet.pixeldrop.pixelracer.RACER_LANE_COUNT
import com.jasmeet.pixeldrop.pixelracer.RACER_PICKUP_SIZE
import com.jasmeet.pixeldrop.pixelracer.RACER_PLAYER_HEIGHT
import com.jasmeet.pixeldrop.pixelracer.RACER_PLAYER_WIDTH
import com.jasmeet.pixeldrop.pixelracer.RACER_PLAYER_Y
import com.jasmeet.pixeldrop.pixelracer.RACER_ROAD_LEFT
import com.jasmeet.pixeldrop.pixelracer.RACER_ROAD_RIGHT
import com.jasmeet.pixeldrop.pixelracer.RacerGameOverCause
import com.jasmeet.pixeldrop.pixelracer.RacerPickupKind
import com.jasmeet.pixeldrop.pixelracer.TrafficKind
import com.jasmeet.pixeldrop.pixelracer.multiplier
import com.jasmeet.pixeldrop.pixelracer.racerLaneCenterX
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.min
import com.jasmeet.pixeldrop.ui.components.retroPad

/** PIXEL RACER LCD: scrolling 4-lane road left, SCORE/FUEL/LIVES panel right. */
@Composable
fun PixelRacerScreen(state: PixelRacerState, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.05f
        val ink = PixelDropColors.LcdDark

        val scale = min((h - pad * 2) / RACER_FIELD_HEIGHT, (w * 0.62f - pad) / RACER_FIELD_WIDTH)
        val fieldW = scale * RACER_FIELD_WIDTH
        val fieldH = scale * RACER_FIELD_HEIGHT
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

        clipRect(fx, fy, fx + fieldW, fy + fieldH) {
            // Road shoulders.
            drawRect(ink, Offset(mapX(RACER_ROAD_LEFT) - scale, fy), Size(scale, fieldH))
            drawRect(ink, Offset(mapX(RACER_ROAD_RIGHT), fy), Size(scale, fieldH))

            // Lane dividers: dashes that ride the scroll, so the road itself
            // reads as moving. Purely state-driven (distance modulo period).
            val dashPeriod = 14f
            val dashLen = 7f
            val phase = state.distancePx % dashPeriod
            for (lane in 1 until RACER_LANE_COUNT) {
                val lx = mapX(RACER_ROAD_LEFT + lane * (RACER_ROAD_RIGHT - RACER_ROAD_LEFT) / RACER_LANE_COUNT)
                var dy = phase - dashPeriod
                while (dy < RACER_FIELD_HEIGHT) {
                    drawRect(
                        ink.copy(alpha = 0.45f),
                        Offset(lx - scale * 0.35f, mapY(dy)),
                        Size(scale * 0.7f, dashLen * scale),
                    )
                    dy += dashPeriod
                }
            }

            // Pickups. Coins blink off the survival clock, boxes carry a tag
            // letter — state-driven, no logic owned by the draw pass.
            val blinkOn = (state.survivedMs / 250L) % 2L == 0L
            state.pickups.forEach { p ->
                val cx = mapX(racerLaneCenterX(p.lane))
                val cy = mapY(p.y)
                val s = RACER_PICKUP_SIZE * scale
                when (p.kind) {
                    RacerPickupKind.Coin -> if (blinkOn) {
                        drawCircle(ink, radius = s * 0.4f, center = Offset(cx, cy), style = Stroke(width = scale * 0.5f))
                        drawCircle(ink, radius = s * 0.12f, center = Offset(cx, cy))
                    }
                    RacerPickupKind.Fuel -> {
                        drawRect(ink, Offset(cx - s * 0.35f, cy - s * 0.45f), Size(s * 0.7f, s * 0.9f), style = Stroke(width = scale * 0.5f))
                        drawRect(ink, Offset(cx + s * 0.2f, cy - s * 0.65f), Size(s * 0.22f, s * 0.3f))
                        drawPixelTextCentered("F", cx, cy - s * 0.28f, s / 9f, ink)
                    }
                    RacerPickupKind.Shield, RacerPickupKind.SlowMo, RacerPickupKind.Turbo -> {
                        drawRect(ink, Offset(cx - s / 2f, cy - s / 2f), Size(s, s), style = Stroke(width = scale * 0.5f))
                        val tag = when (p.kind) {
                            RacerPickupKind.Shield -> "S"
                            RacerPickupKind.SlowMo -> "W"
                            else -> "T"
                        }
                        drawPixelTextCentered(tag, cx, cy - s * 0.28f, s / 9f, ink)
                    }
                }
            }

            // Traffic.
            state.traffic.forEach { car ->
                drawTrafficCar(mapX(racerLaneCenterX(car.lane)), mapY(car.y), car.kind, scale, ink)
            }

            // Player, blinking through the post-crash mercy window.
            val playerVisible = state.invulnMs <= 0L || (state.survivedMs / 120L) % 2L == 0L
            if (playerVisible) {
                drawPlayerCar(mapX(state.playerX), mapY(RACER_PLAYER_Y), scale, ink, turbo = state.turboMs > 0)
            }
            if (state.shieldActive) {
                drawRect(
                    color = ink.copy(alpha = 0.8f),
                    topLeft = Offset(
                        mapX(state.playerX - RACER_PLAYER_WIDTH / 2f - 1.6f),
                        mapY(RACER_PLAYER_Y - RACER_PLAYER_HEIGHT / 2f - 1.6f),
                    ),
                    size = Size((RACER_PLAYER_WIDTH + 3.2f) * scale, (RACER_PLAYER_HEIGHT + 3.2f) * scale),
                    style = Stroke(width = scale * 0.35f),
                )
            }
        }

        // Stats panel.
        val panelX = fx + fieldW + pad * 0.9f
        val panelW = w - panelX - pad * 0.7f
        val px = panelW / 58f
        val lineH = px * 10f

        var y = fy + scale * 0.3f
        drawPixelText("SCORE", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText(retroPad(state.score, 6), Offset(panelX, y), px, ink)
        y += lineH * 1.9f
        drawPixelText("DIST", Offset(panelX, y), px, ink)
        y += lineH
        drawPixelText("${retroPad((state.distancePx / 10f).toInt(), 4)}M", Offset(panelX, y), px, ink)
        y += lineH * 1.9f

        // Fuel gauge: outline bar, fill, blinking when the tank runs low.
        drawPixelText("FUEL", Offset(panelX, y), px, ink)
        y += lineH
        val barW = panelW * 0.92f
        val barH = px * 6f
        val lowFuel = state.fuel <= 25f
        val fuelBlinkOn = !lowFuel || (state.survivedMs / 300L) % 2L == 0L
        drawRect(ink, Offset(panelX, y), Size(barW, barH), style = Stroke(width = px))
        if (fuelBlinkOn) {
            drawRect(ink, Offset(panelX + px, y + px), Size((barW - px * 2) * (state.fuel / 100f), barH - px * 2))
        }
        y += barH + lineH * 0.9f

        drawPixelText("LIVES", Offset(panelX, y), px, ink)
        y += lineH
        repeat(state.lives.coerceAtLeast(0)) { i ->
            drawMiniCar(panelX + i * px * 12f, y, px, ink)
        }
        y += lineH * 1.9f

        drawPixelText("SPD ${retroPad(state.speedLevel, 2)}", Offset(panelX, y), px, ink)
        y += lineH * 1.4f
        if (state.combo > 1) {
            drawPixelText("X${retroPad(state.multiplier(), 1)}", Offset(panelX, y), px * 1.4f, ink)
            y += lineH * 1.6f
        }
        val powerLabel = when {
            state.turboMs > 0 -> "TRB ${retroPad(state.turboMs / 1000L + 1, 1)}"
            state.slowMoMs > 0 -> "SLW ${retroPad(state.slowMoMs / 1000L + 1, 1)}"
            state.shieldActive -> "SH ON"
            else -> null
        }
        if (powerLabel != null) {
            drawPixelText(powerLabel, Offset(panelX, y), px, ink)
        }
    }
}

/** Original chunky racer: hull, cabin notch, four wheel studs; turbo adds a
 *  two-prong exhaust flame under the tail. */
private fun DrawScope.drawPlayerCar(cx: Float, cy: Float, scale: Float, ink: Color, turbo: Boolean) {
    val w = RACER_PLAYER_WIDTH * scale
    val h = RACER_PLAYER_HEIGHT * scale
    // Hull.
    drawRect(ink, Offset(cx - w * 0.32f, cy - h * 0.46f), Size(w * 0.64f, h * 0.92f))
    // Cabin notch (lighter inset, same trick as the LCD bricks).
    drawRect(
        PixelDropColors.LcdGreen.copy(alpha = 0.55f),
        Offset(cx - w * 0.18f, cy - h * 0.16f),
        Size(w * 0.36f, h * 0.3f),
    )
    // Wheels.
    val wheelW = w * 0.14f
    val wheelH = h * 0.2f
    drawRect(ink, Offset(cx - w * 0.5f, cy - h * 0.4f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx + w * 0.5f - wheelW, cy - h * 0.4f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx - w * 0.5f, cy + h * 0.2f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx + w * 0.5f - wheelW, cy + h * 0.2f), Size(wheelW, wheelH))
    if (turbo) {
        drawRect(ink, Offset(cx - w * 0.22f, cy + h * 0.5f), Size(w * 0.14f, h * 0.22f))
        drawRect(ink, Offset(cx + w * 0.08f, cy + h * 0.5f), Size(w * 0.14f, h * 0.28f))
    }
}

/** Traffic variants: sedan mirrors the player silhouette, trucks add a cab
 *  divider line, sports taper with a nose stud. */
private fun DrawScope.drawTrafficCar(cx: Float, cy: Float, kind: TrafficKind, scale: Float, ink: Color) {
    val w = kind.width * scale
    val h = kind.height * scale
    when (kind) {
        TrafficKind.Sedan -> {
            drawRect(ink, Offset(cx - w * 0.32f, cy - h * 0.46f), Size(w * 0.64f, h * 0.92f))
            drawRect(
                PixelDropColors.LcdGreen.copy(alpha = 0.55f),
                Offset(cx - w * 0.18f, cy - h * 0.14f),
                Size(w * 0.36f, h * 0.28f),
            )
        }
        TrafficKind.Truck -> {
            drawRect(ink, Offset(cx - w * 0.36f, cy - h * 0.48f), Size(w * 0.72f, h * 0.96f))
            // Cab/trailer divider.
            drawRect(
                PixelDropColors.LcdGreen.copy(alpha = 0.55f),
                Offset(cx - w * 0.36f, cy + h * 0.18f),
                Size(w * 0.72f, h * 0.06f),
            )
        }
        TrafficKind.Sport -> {
            drawRect(ink, Offset(cx - w * 0.28f, cy - h * 0.34f), Size(w * 0.56f, h * 0.8f))
            // Nose stud pointing down-screen (oncoming).
            drawRect(ink, Offset(cx - w * 0.1f, cy + h * 0.34f), Size(w * 0.2f, h * 0.16f))
        }
    }
    // Shared wheel studs.
    val wheelW = w * 0.14f
    val wheelH = h * 0.16f
    drawRect(ink, Offset(cx - w * 0.5f, cy - h * 0.36f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx + w * 0.5f - wheelW, cy - h * 0.36f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx - w * 0.5f, cy + h * 0.2f), Size(wheelW, wheelH))
    drawRect(ink, Offset(cx + w * 0.5f - wheelW, cy + h * 0.2f), Size(wheelW, wheelH))
}

/** Tiny life-counter car icon for the stats panel. */
private fun DrawScope.drawMiniCar(x: Float, y: Float, px: Float, ink: Color) {
    drawRect(ink, Offset(x, y), Size(px * 8f, px * 10f))
    drawRect(PixelDropColors.LcdGreen.copy(alpha = 0.55f), Offset(x + px * 2f, y + px * 3f), Size(px * 4f, px * 3f))
}

/** Game-over LCD: cause-specific headline, final stats, blinking retry hint. */
@Composable
fun PixelRacerGameOverScreen(state: PixelRacerState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "racer-gameover-blink")
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
        drawPixelTextCentered("PIXEL RACER", w / 2f, h * 0.14f, w / 105f, ink)
        val headline = when (state.gameOverCause) {
            RacerGameOverCause.OutOfFuel -> "OUT OF FUEL"
            else -> "WRECKED"
        }
        drawPixelTextCentered(headline, w / 2f, h * 0.24f, w / 72f, ink)

        val px = w / 85f
        val labelX = w * 0.12f
        val valueRight = w * 0.88f
        val rows = listOf(
            "SCORE" to retroPad(state.score, 6),
            "DIST" to "${retroPad((state.distancePx / 10f).toInt(), 4)}M",
            "SPEED" to retroPad(state.speedLevel, 2),
            "COINS" to retroPad(state.coinsCollected, 3),
        )
        rows.forEachIndexed { i, (label, value) ->
            val y = h * 0.38f + i * h * 0.095f
            drawPixelText(label, Offset(labelX, y), px, ink)
            drawPixelTextRightAligned(value, valueRight, y, px, ink)
        }

        if (phase < 0.62f) {
            drawPixelTextCentered("A/START RETRY", w / 2f, h * 0.78f, w / 110f, ink)
        }
        drawPixelTextCentered("B MENU", w / 2f, h * 0.86f, w / 120f, ink.copy(alpha = 0.8f))
    }
}
