package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jasmeet.pixeldrop.game.GameId
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.floor

/** Stepped LCD tones indexed by TONE_*; built once, never per frame. */
private val DEMO_TONES: Array<Color> = arrayOf(
    PixelDropColors.LcdDark,
    PixelDropColors.LcdDark.copy(alpha = 0.65f),
    PixelDropColors.LcdDark.copy(alpha = 0.38f),
    PixelDropColors.LcdDark.copy(alpha = 0.20f),
    PixelDropColors.LcdGreen,
)

/**
 * Bridges the pure demo frames to a [DrawScope]. One instance lives in the menu's
 * `remember`; per frame it only rebinds fields, so drawing allocates nothing.
 * Rect edges are floored to whole device pixels so neighbours never leave seams.
 */
internal class DemoPainter : DemoSink {
    private var scope: DrawScope? = null
    private var originX = 0f
    private var originY = 0f
    private var cellPx = 1f

    fun draw(scope: DrawScope, game: GameId, t: Float, originX: Float, originY: Float, cellPx: Float) {
        this.scope = scope
        this.originX = originX
        this.originY = originY
        this.cellPx = cellPx
        renderDemo(game, t, this)
        this.scope = null
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, tone: Int) {
        val s = scope ?: return
        val left = floor(originX + x * cellPx)
        val top = floor(originY + y * cellPx)
        val right = floor(originX + (x + w) * cellPx)
        val bottom = floor(originY + (y + h) * cellPx)
        s.drawRect(
            DEMO_TONES[tone],
            Offset(left, top),
            Size(maxOf(right - left, 1f), maxOf(bottom - top, 1f)),
        )
    }
}
