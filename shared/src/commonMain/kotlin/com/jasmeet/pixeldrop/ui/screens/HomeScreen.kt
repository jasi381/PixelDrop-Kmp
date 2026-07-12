package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.ui.components.dotHash
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors

/**
 * Shared home-style LCD frame; the boot finale reuses it with the block
 * frieze and no console icon.
 */
fun DrawScope.drawHomeLcd(blinkOn: Boolean, alpha: Float, frieze: Boolean, icon: Boolean) {
    if (alpha <= 0f) return
    val w = size.width
    val h = size.height
    val ink = PixelDropColors.LcdDark.copy(alpha = alpha)

    if (frieze) {
        drawBlockFrieze(top = true, seed = 11, alpha = alpha)
        drawBlockFrieze(top = false, seed = 29, alpha = alpha)
    }

    drawPixelTextCentered("PIXEL DROP", w / 2f, h * 0.24f, w / 68f, ink)

    if (icon) {
        drawConsoleIcon(center = Offset(w / 2f, h * 0.47f), unit = w / 90f, alpha = alpha)
    }

    if (blinkOn) {
        drawPixelTextCentered("PRESS START", w / 2f, h * 0.60f, w / 95f, ink)
    }

    drawPixelTextCentered(
        "PIXEL DROP SYSTEM", w / 2f, h * 0.78f, w / 125f,
        PixelDropColors.LcdDark.copy(alpha = 0.8f * alpha),
    )
}

/** Decorative stacked-block strip along the top or bottom LCD edge. */
fun DrawScope.drawBlockFrieze(top: Boolean, seed: Int, alpha: Float) {
    val w = size.width
    val h = size.height
    val cell = w / 56f
    val ink = PixelDropColors.LcdDark.copy(alpha = 0.85f * alpha)
    for (col in 0 until 56) {
        val depth = (dotHash(seed, col, 0) * 3f).toInt() // 0..2 cells deep
        for (r in 0..depth) {
            if (dotHash(seed, col, r + 1) > 0.82f) continue // occasional gap
            val y = if (top) r * cell else h - (r + 1) * cell
            drawRect(ink, Offset(col * cell, y), Size(cell * 0.92f, cell * 0.92f))
        }
    }
}

/** Tiny original handheld glyph used by the boot-finale LCD frame. */
fun DrawScope.drawConsoleIcon(center: Offset, unit: Float, alpha: Float) {
    val ink = PixelDropColors.LcdDark.copy(alpha = alpha)
    val bodyW = unit * 10f
    val bodyH = unit * 14f
    val tl = Offset(center.x - bodyW / 2f, center.y - bodyH / 2f)
    drawRoundRect(
        color = ink,
        topLeft = tl,
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(unit * 1.5f),
        style = Stroke(width = unit),
    )
    // screen
    drawRect(
        color = ink,
        topLeft = Offset(tl.x + unit * 2f, tl.y + unit * 2f),
        size = Size(bodyW - unit * 4f, unit * 5f),
    )
    // d-pad dot + two buttons
    drawRect(ink, Offset(tl.x + unit * 2f, tl.y + unit * 9.5f), Size(unit * 2f, unit * 2f))
    drawRect(ink, Offset(tl.x + bodyW - unit * 4.5f, tl.y + unit * 9f), Size(unit * 1.4f, unit * 1.4f))
    drawRect(ink, Offset(tl.x + bodyW - unit * 3f, tl.y + unit * 10.4f), Size(unit * 1.4f, unit * 1.4f))
}
