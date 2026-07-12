package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jasmeet.pixeldrop.motion.BootTimeline
import com.jasmeet.pixeldrop.motion.BootTimeline.ramp
import com.jasmeet.pixeldrop.ui.components.dotHash
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.pixelTextCellWidth
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors

/** LCD content of the boot sequence; [elapsedMs] is driven by the app loop. */
@Composable
fun BootScreen(elapsedMs: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawBootLcd(elapsedMs)
    }
}

private fun DrawScope.drawBootLcd(t: Int) {
    val w = size.width
    val h = size.height
    val ink = PixelDropColors.LcdDark

    // Stage: scan sweep, a dark band moving top to bottom.
    val sweep = ramp(t, BootTimeline.SWEEP_START, BootTimeline.SWEEP_END)
    if (t in BootTimeline.SWEEP_START until BootTimeline.SWEEP_END) {
        val y = h * sweep
        drawRect(
            color = ink.copy(alpha = 0.38f * (1f - sweep * 0.5f)),
            topLeft = Offset(0f, y),
            size = Size(w, h * 0.014f),
        )
    }

    // Stage: brief pixel flicker while the panel warms up.
    if (t in BootTimeline.FLICKER_START until BootTimeline.FLICKER_END) {
        val bucket = t / 90
        val px = w * 0.012f
        for (k in 0 until 10) {
            val hx = dotHash(bucket, k, 0)
            val hy = dotHash(bucket, k, 1)
            if (dotHash(bucket, k, 2) > 0.45f) continue
            drawRect(
                color = ink.copy(alpha = 0.30f),
                topLeft = Offset(hx * (w - px), hy * (h - px)),
                size = Size(px, px),
            )
        }
    }

    // Stage: logo assembles pixel-by-pixel, then subtitle appears.
    if (t in BootTimeline.LOGO_START until BootTimeline.SYS_START) {
        val reveal = ramp(t, BootTimeline.LOGO_START, BootTimeline.LOGO_END)
        drawPixelTextCentered("PIXEL DROP", w / 2f, h * 0.30f, w / 70f, ink, reveal)
        val subtitleAlpha = ramp(t, BootTimeline.SUBTITLE_START, BootTimeline.SUBTITLE_END)
        if (subtitleAlpha > 0f) {
            drawPixelTextCentered(
                "CANVAS CLASSICS", w / 2f, h * 0.47f, w / 130f,
                ink.copy(alpha = subtitleAlpha),
            )
        }
    }

    // Stage: system check text, line by line, with blinking block cursor.
    if (t in BootTimeline.SYS_START until BootTimeline.BLOCKS_END) {
        val fade = 1f - ramp(t, BootTimeline.SYS_FADE_START, BootTimeline.SYS_FADE_END)
        if (fade > 0f) {
            val lines = listOf(
                "PIXEL DROP SYSTEM",
                "GRID ENGINE: OK",
                "CANVAS RENDERER: OK",
                "INPUT: OK",
                "AUDIO: OK",
            )
            val px = w / 120f
            val x = w * 0.08f
            var shown = 0
            lines.forEachIndexed { i, line ->
                val lineTime = BootTimeline.SYS_START + i * BootTimeline.SYS_LINE_STEP
                if (t >= lineTime) {
                    shown = i
                    val y = h * 0.10f + i * h * 0.105f + if (i > 0) h * 0.03f else 0f
                    drawPixelText(line, Offset(x, y), px, ink.copy(alpha = fade))
                }
            }
            // Blinking cursor after the last visible line.
            if ((t / 260) % 2 == 0) {
                val y = h * 0.10f + shown * h * 0.105f + if (shown > 0) h * 0.03f else 0f
                val lineWidth = pixelTextCellWidth(lines[shown]) * px
                drawPixelText("#", Offset(x + lineWidth + px * 3, y), px, ink.copy(alpha = fade))
            }
        }
    }

    // Loading bar: block cells inside a bracketed outline (logo + sys stages).
    if (t in BootTimeline.BAR_START until BootTimeline.BLOCKS_START) {
        val progress = ramp(t, BootTimeline.BAR_START, BootTimeline.BAR_END)
        val barW = w * 0.62f
        val barH = h * 0.045f
        val bx = (w - barW) / 2f
        val by = h * 0.76f
        drawRect(ink, Offset(bx, by), Size(barW, barH), style = Stroke(width = w * 0.006f))
        val cells = 12
        val cellGap = barW * 0.012f
        val cellW = (barW - cellGap * (cells + 1)) / cells
        val filled = (progress * cells).toInt()
        for (i in 0 until filled) {
            drawRect(
                color = ink,
                topLeft = Offset(bx + cellGap + i * (cellW + cellGap), by + barH * 0.18f),
                size = Size(cellW, barH * 0.64f),
            )
        }
    }

    // Stage: small block shapes fall and stack at the bottom.
    if (t in BootTimeline.BLOCKS_START until BootTimeline.FINAL_START + 150) {
        val cell = w / 56f
        for (i in 0 until 10) {
            val start = BootTimeline.BLOCKS_START + i * 45
            val p = ramp(t, start, start + 330)
            if (p <= 0f) continue
            val colCell = (i * 23 + 5) % 52
            val stackRows = 1 + (dotHash(7, i, 3) * 3f).toInt()
            val targetY = h - stackRows * cell * 2f
            val y = -cell * 4f + (targetY + cell * 4f) * p
            val x = colCell * cell
            // 2x2 cluster with one offset cell, tetromino-ish silhouette.
            drawRect(ink.copy(alpha = 0.9f), Offset(x, y), Size(cell * 2f, cell * 2f))
            drawRect(ink.copy(alpha = 0.9f), Offset(x + cell * 2f, y + cell), Size(cell, cell))
        }
    }

    // Stage: final frame fades in (frieze + logo + press start + system label).
    if (t >= BootTimeline.FINAL_START) {
        val alpha = ramp(t, BootTimeline.FINAL_START, BootTimeline.FINAL_END)
        val blinkOn = (t / 550) % 2 == 0
        drawHomeLcd(blinkOn = blinkOn, alpha = alpha, frieze = true, icon = false)
    }
}
