package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.pixelTextCellWidth

/**
 * Text-only sprite for teaser slots: "COMING" / "SOON" set in the LCD pixel
 * font like a terminal screen, with twinkling stars, a divider, loading dots
 * that count 1-2-3 and a blinking cursor. [t] is the 0..1 loop phase.
 */
internal fun DrawScope.drawComingSoonSprite(x0: Float, y0: Float, fieldW: Float, fieldH: Float, ink: Color, t: Float) {
    val px = fieldW * 0.9f / pixelTextCellWidth("COMING")
    val cx = x0 + fieldW / 2f
    fun line(text: String, yFrac: Float, color: Color = ink) =
        drawPixelTextCentered(text, cx, y0 + fieldH * yFrac, px, color)

    // Stars alternate so one is always lit.
    val starsOn = ((t * 8f).toInt() and 1) == 0
    line(if (starsOn) "* * *" else " * * ", 0.16f, ink.copy(alpha = 0.7f))
    line("COMING", 0.30f)
    line("SOON", 0.44f)
    line("-----", 0.58f, ink.copy(alpha = 0.7f))
    val dots = 1 + (t * 3f).toInt().coerceAtMost(2)
    line(".".repeat(dots).padEnd(3, ' '), 0.68f)
    if (((t * 6f).toInt() and 1) == 0) line("_", 0.80f)
}
