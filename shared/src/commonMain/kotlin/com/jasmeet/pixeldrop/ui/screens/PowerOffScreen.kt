package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jasmeet.pixeldrop.motion.BootTimeline.ramp
import com.jasmeet.pixeldrop.motion.PowerOffTimeline
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors

/**
 * Shutdown overlay drawn on top of the frozen LCD content (which the app
 * hides once the collapse begins). All motion stays inside the LCD.
 */
@Composable
fun PowerOffScreen(elapsedMs: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawPowerOffLcd(elapsedMs)
    }
}

private fun DrawScope.drawPowerOffLcd(t: Int) {
    val w = size.width
    val h = size.height

    // Phase 1-2: darken the frozen content and thicken scanlines.
    if (t < PowerOffTimeline.COLLAPSE_END) {
        val dim = 0.65f * ramp(t, PowerOffTimeline.FREEZE_END, PowerOffTimeline.FADE_END)
        if (dim > 0f) {
            drawRect(PixelDropColors.LcdDark.copy(alpha = dim))
        }
        val scan = 0.10f * ramp(t, PowerOffTimeline.FREEZE_END, PowerOffTimeline.FADE_END)
        if (scan > 0f) {
            val step = h / 60f
            var y = 0f
            while (y < h) {
                drawRect(Color.Black.copy(alpha = scan), Offset(0f, y), Size(w, step * 0.45f))
                y += step
            }
        }
    }

    // Phase 3: screen collapses into a glowing horizontal line.
    if (t in PowerOffTimeline.FADE_END until PowerOffTimeline.SHRINK_END) {
        val collapse = ramp(t, PowerOffTimeline.FADE_END, PowerOffTimeline.COLLAPSE_END)
        val shrink = ramp(t, PowerOffTimeline.COLLAPSE_END, PowerOffTimeline.SHRINK_END)

        // Dark curtain closing from top and bottom toward the middle.
        val curtain = h / 2f * collapse
        drawRect(PixelDropColors.LcdDark.copy(alpha = 0.9f * collapse), Offset(0f, 0f), Size(w, curtain))
        drawRect(
            PixelDropColors.LcdDark.copy(alpha = 0.9f * collapse),
            Offset(0f, h - curtain),
            Size(w, curtain),
        )

        drawCollapseLine(
            widthFraction = 1f - 0.98f * shrink,
            coreHeight = h * (0.06f - 0.052f * collapse),
            alpha = 0.35f + 0.65f * collapse,
        )
    }

    // Phase 4: the dot blinks once, then disappears.
    if (t in PowerOffTimeline.SHRINK_END until PowerOffTimeline.DOT_END) {
        val local = t - PowerOffTimeline.SHRINK_END // 0..400
        val visible = local < 160 || local in 240 until 340
        drawRect(PixelDropColors.LcdDark.copy(alpha = 0.95f))
        if (visible) {
            drawCollapseLine(widthFraction = 0.02f, coreHeight = size.height * 0.010f, alpha = 1f)
        }
    }

    // Phase 5: powered-off dark panel.
    if (t >= PowerOffTimeline.DOT_END) {
        drawRect(PixelDropColors.LcdDark.copy(alpha = 0.97f))
    }
}

/** Soft-glowing olive line centered on the LCD, used for collapse + dot. */
private fun DrawScope.drawCollapseLine(widthFraction: Float, coreHeight: Float, alpha: Float) {
    val w = size.width
    val h = size.height
    val lineW = w * widthFraction.coerceIn(0.02f, 1f)
    val x = (w - lineW) / 2f
    val cy = h / 2f
    // Glow halo.
    drawRoundRect(
        color = PixelDropColors.LcdGreenBright.copy(alpha = 0.25f * alpha),
        topLeft = Offset(x - coreHeight, cy - coreHeight * 2.2f),
        size = Size(lineW + coreHeight * 2f, coreHeight * 4.4f),
        cornerRadius = CornerRadius(coreHeight * 2f),
    )
    // Bright core.
    drawRoundRect(
        color = PixelDropColors.LcdGreenBright.copy(alpha = alpha),
        topLeft = Offset(x, cy - coreHeight / 2f),
        size = Size(lineW, coreHeight),
        cornerRadius = CornerRadius(coreHeight / 2f),
    )
}
