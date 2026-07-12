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
import androidx.compose.ui.graphics.drawscope.clipRect
import com.jasmeet.pixeldrop.galaxyminer.GalaxyMinerState
import com.jasmeet.pixeldrop.galaxyminer.MINER_CRYSTAL_RADIUS
import com.jasmeet.pixeldrop.galaxyminer.MINER_FIELD_HEIGHT
import com.jasmeet.pixeldrop.galaxyminer.MINER_FIELD_WIDTH
import com.jasmeet.pixeldrop.galaxyminer.MINER_MINE_RADIUS
import com.jasmeet.pixeldrop.galaxyminer.MINER_POWER_SIZE
import com.jasmeet.pixeldrop.galaxyminer.MINER_SHIP_RADIUS
import com.jasmeet.pixeldrop.galaxyminer.MINER_STATION_X
import com.jasmeet.pixeldrop.galaxyminer.MINER_STATION_Y
import com.jasmeet.pixeldrop.galaxyminer.MinerPowerType
import com.jasmeet.pixeldrop.galaxyminer.cargoMultiplier
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.components.retroPad
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.min

@Composable
fun GalaxyMinerScreen(state: GalaxyMinerState, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val ink = PixelDropColors.LcdDark
        val pad = size.width * 0.045f
        val scale = min((size.height - pad * 2f) / MINER_FIELD_HEIGHT, (size.width * 0.69f - pad) / MINER_FIELD_WIDTH)
        val fieldW = MINER_FIELD_WIDTH * scale
        val fieldH = MINER_FIELD_HEIGHT * scale
        val fx = pad
        val fy = (size.height - fieldH) / 2f
        fun x(value: Float) = fx + value * scale
        fun y(value: Float) = fy + value * scale

        drawRect(ink, Offset(fx - scale * 0.4f, fy - scale * 0.4f), Size(fieldW + scale * 0.8f, fieldH + scale * 0.8f), style = Stroke(scale * 0.35f))
        clipRect(fx, fy, fx + fieldW, fy + fieldH) {
            // Fixed star map: no random values or allocations in the draw path.
            for (index in 0 until 30) {
                val sx = x(((index * 37) % 97 + 1).toFloat())
                val sy = y(((index * 53) % 127 + 1).toFloat())
                val star = if (index % 5 == 0) scale * 0.7f else scale * 0.35f
                drawRect(ink.copy(alpha = if (index % 3 == 0) 0.38f else 0.2f), Offset(sx, sy), Size(star, star))
            }

            drawCircle(ink.copy(alpha = 0.35f), 9f * scale, Offset(x(MINER_STATION_X), y(MINER_STATION_Y)), style = Stroke(scale * 0.7f))
            drawCircle(ink, 5.5f * scale, Offset(x(MINER_STATION_X), y(MINER_STATION_Y)), style = Stroke(scale * 0.65f))
            drawRect(ink, Offset(x(MINER_STATION_X) - scale, y(MINER_STATION_Y) - scale), Size(scale * 2f, scale * 2f))

            for (index in state.crystals.indices) {
                val crystal = state.crystals[index]
                drawCrystal(x(crystal.x), y(crystal.y), MINER_CRYSTAL_RADIUS * scale, ink)
            }
            for (index in state.powerUps.indices) {
                val power = state.powerUps[index]
                val label = when (power.type) { MinerPowerType.TractorBeam -> "T"; MinerPowerType.Shield -> "S"; MinerPowerType.SpeedBoost -> "V" }
                val half = MINER_POWER_SIZE * scale / 2f
                drawRect(ink, Offset(x(power.x) - half, y(power.y) - half), Size(half * 2f, half * 2f), style = Stroke(scale * 0.55f))
                drawPixelTextCentered(label, x(power.x), y(power.y) - scale * 2.5f, scale * 0.65f, ink)
            }
            for (index in state.mines.indices) {
                val mine = state.mines[index]
                drawSpaceMine(x(mine.x), y(mine.y), MINER_MINE_RADIUS * scale, ink)
            }

            if (state.beamMs > 0) {
                drawLine(ink, Offset(x(state.shipX), y(state.shipY)), Offset(x(state.beamX), y(state.beamY)), scale * 0.55f)
            }
            if (state.tractorMs > 0) {
                val pulse = 13f + ((state.elapsedMs / 120L) % 3L) * 2f
                drawCircle(ink.copy(alpha = 0.32f), pulse * scale, Offset(x(state.shipX), y(state.shipY)), style = Stroke(scale * 0.45f))
            }
            val visible = state.invulnerableMs == 0L || (state.elapsedMs / 100L) % 2L == 0L
            if (visible) drawMinerShip(x(state.shipX), y(state.shipY), MINER_SHIP_RADIUS * scale, ink, state.speedBoostMs > 0)
            if (state.shieldActive) drawCircle(ink, MINER_SHIP_RADIUS * 1.65f * scale, Offset(x(state.shipX), y(state.shipY)), style = Stroke(scale * 0.5f))
        }

        val panelX = fx + fieldW + pad * 0.8f
        val panelW = size.width - panelX - pad * 0.35f
        val px = panelW / 56f
        var panelY = fy + scale
        drawPixelText("SCORE", Offset(panelX, panelY), px, ink); panelY += px * 10f
        drawPixelText(retroPad(state.score, 6), Offset(panelX, panelY), px, ink); panelY += px * 18f
        drawPixelText("LEVEL ${retroPad(state.level, 2)}", Offset(panelX, panelY), px, ink); panelY += px * 16f
        drawPixelText("CARGO", Offset(panelX, panelY), px, ink); panelY += px * 10f
        drawPixelText("${retroPad(state.cargo, 2)} X${state.cargoMultiplier()}", Offset(panelX, panelY), px, ink); panelY += px * 18f
        drawPixelText("HULL", Offset(panelX, panelY), px, ink); panelY += px * 11f
        repeat(state.hull.coerceAtLeast(0)) { index -> drawRect(ink, Offset(panelX + index * px * 10f, panelY), Size(px * 7f, px * 5f)) }
        panelY += px * 18f
        when {
            state.tractorMs > 0 -> drawPixelText("TRACT ${state.tractorMs / 1000L + 1}", Offset(panelX, panelY), px, ink)
            state.speedBoostMs > 0 -> drawPixelText("BOOST ${state.speedBoostMs / 1000L + 1}", Offset(panelX, panelY), px, ink)
            state.shieldActive -> drawPixelText("SHIELD", Offset(panelX, panelY), px, ink)
        }
        drawPixelTextCentered("A MINE  B BRAKE", fx + fieldW / 2f, fy + fieldH - scale * 5f, scale * 0.72f, ink.copy(alpha = 0.72f))
    }
}

private fun DrawScope.drawCrystal(cx: Float, cy: Float, radius: Float, ink: Color) {
    drawLine(ink, Offset(cx, cy - radius), Offset(cx + radius * 0.75f, cy), radius * 0.28f)
    drawLine(ink, Offset(cx + radius * 0.75f, cy), Offset(cx, cy + radius), radius * 0.28f)
    drawLine(ink, Offset(cx, cy + radius), Offset(cx - radius * 0.75f, cy), radius * 0.28f)
    drawLine(ink, Offset(cx - radius * 0.75f, cy), Offset(cx, cy - radius), radius * 0.28f)
    drawCircle(ink, radius * 0.2f, Offset(cx, cy))
}

private fun DrawScope.drawSpaceMine(cx: Float, cy: Float, radius: Float, ink: Color) {
    drawCircle(ink, radius * 0.62f, Offset(cx, cy), style = Stroke(radius * 0.25f))
    drawLine(ink, Offset(cx - radius, cy), Offset(cx + radius, cy), radius * 0.22f)
    drawLine(ink, Offset(cx, cy - radius), Offset(cx, cy + radius), radius * 0.22f)
    drawLine(ink, Offset(cx - radius * 0.7f, cy - radius * 0.7f), Offset(cx + radius * 0.7f, cy + radius * 0.7f), radius * 0.18f)
}

private fun DrawScope.drawMinerShip(cx: Float, cy: Float, radius: Float, ink: Color, boost: Boolean) {
    drawLine(ink, Offset(cx, cy - radius), Offset(cx - radius * 0.8f, cy + radius * 0.7f), radius * 0.38f)
    drawLine(ink, Offset(cx, cy - radius), Offset(cx + radius * 0.8f, cy + radius * 0.7f), radius * 0.38f)
    drawLine(ink, Offset(cx - radius * 0.8f, cy + radius * 0.7f), Offset(cx + radius * 0.8f, cy + radius * 0.7f), radius * 0.38f)
    drawRect(ink, Offset(cx - radius * 0.18f, cy - radius * 0.05f), Size(radius * 0.36f, radius * 0.5f))
    if (boost) drawLine(ink, Offset(cx, cy + radius * 0.7f), Offset(cx, cy + radius * 1.35f), radius * 0.28f)
}

@Composable
fun GalaxyMinerGameOverScreen(state: GalaxyMinerState, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val ink = PixelDropColors.LcdDark
        drawPixelTextCentered("GALAXY MINER", size.width / 2f, size.height * 0.14f, size.width / 91f, ink)
        drawPixelTextCentered("SHIP LOST", size.width / 2f, size.height * 0.27f, size.width / 65f, ink)
        val px = size.width / 88f
        val labelX = size.width * 0.12f
        val right = size.width * 0.88f
        var y = size.height * 0.44f
        drawPixelText("SCORE", Offset(labelX, y), px, ink); drawPixelTextRightAligned(retroPad(state.score, 6), right, y, px, ink); y += size.height * 0.11f
        drawPixelText("CRYSTALS", Offset(labelX, y), px, ink); drawPixelTextRightAligned(retroPad(state.bankedCrystals, 3), right, y, px, ink); y += size.height * 0.11f
        drawPixelText("LEVEL", Offset(labelX, y), px, ink); drawPixelTextRightAligned(retroPad(state.level, 2), right, y, px, ink)
        drawPixelTextCentered("A/START RETRY", size.width / 2f, size.height * 0.79f, size.width / 112f, ink)
        drawPixelTextCentered("B MENU", size.width / 2f, size.height * 0.88f, size.width / 125f, ink.copy(alpha = 0.8f))
    }
}
