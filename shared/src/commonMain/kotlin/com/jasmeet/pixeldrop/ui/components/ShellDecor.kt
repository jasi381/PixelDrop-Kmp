package com.jasmeet.pixeldrop.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.jasmeet.pixeldrop.ui.theme.DPAD_DOWN
import com.jasmeet.pixeldrop.ui.theme.DPAD_LEFT
import com.jasmeet.pixeldrop.ui.theme.DPAD_RIGHT
import com.jasmeet.pixeldrop.ui.theme.DPAD_UP
import com.jasmeet.pixeldrop.ui.theme.GameControls
import com.jasmeet.pixeldrop.ui.theme.LIVERY_COLS
import com.jasmeet.pixeldrop.ui.theme.Livery
import com.jasmeet.pixeldrop.ui.theme.LiveryTone

/*
 * Draw-only decoration for the shell: livery decals, game badge and the
 * one-time control captions. Nothing here allocates per frame: colours are
 * value classes, text is drawn from constant strings and index loops walk
 * primitive arrays.
 */

/** Paint used by [liveryBehind]; all derived from the active theme so contrast follows the shell. */
class LiveryInk(val ink: Color, val accent: Color, val accentDark: Color)

private const val FAINT_ALPHA = 0.10f
private const val SOFT_ALPHA = 0.18f
private const val ACCENT_ALPHA = 0.55f
private const val ACCENT_DARK_ALPHA = 0.70f

/**
 * Draws [livery] decals and its badge behind the area's children, at [alpha]
 * (0 skips all work). The area is [ShellGeometry.AREA_W] of a console width wide.
 */
fun Modifier.liveryBehind(livery: Livery?, alpha: Float, paint: LiveryInk): Modifier =
    if (livery == null || alpha <= 0.01f) this else this.drawBehind {
        val cw = size.width / ShellGeometry.AREA_W
        val cell = ShellGeometry.BAND_W * cw / LIVERY_COLS
        val left = ShellGeometry.BAND_L * cw
        val top = ShellGeometry.BAND_T * cw
        for (i in 0 until livery.count) {
            val color = when (livery.tone(i)) {
                LiveryTone.Faint -> paint.ink.copy(alpha = FAINT_ALPHA * alpha)
                LiveryTone.Soft -> paint.ink.copy(alpha = SOFT_ALPHA * alpha)
                LiveryTone.Accent -> paint.accent.copy(alpha = ACCENT_ALPHA * alpha)
                LiveryTone.AccentDark -> paint.accentDark.copy(alpha = ACCENT_DARK_ALPHA * alpha)
            }
            drawRect(
                color = color,
                topLeft = Offset(left + livery.x(i) * cell, top + livery.y(i) * cell),
                size = Size(livery.w(i) * cell, livery.h(i) * cell),
            )
        }
        // Badge: full-strength label ink, so it keeps the shell's label contrast.
        drawPixelTextCentered(
            text = livery.badge,
            centerX = size.width / 2f,
            top = ShellGeometry.BADGE_TOP * cw,
            pixel = ShellGeometry.BADGE_PIXEL * cw,
            color = paint.ink.copy(alpha = alpha),
        )
    }

private fun DrawScope.captionPixel(cw: Dp): Float = cw.toPx() * ShellGeometry.CAPTION_PIXEL

/** Caption under a control, right-aligned to it (used for the A and B keys). */
fun Modifier.captionBelowEnd(text: String?, cw: Dp, color: Color, alpha: Float): Modifier =
    if (text == null || alpha <= 0.01f) this else this.drawWithContent {
        drawContent()
        val p = captionPixel(cw)
        val w = pixelTextCellWidth(text) * p
        drawPixelText(text, Offset(size.width - w, size.height + p), p, color.copy(alpha = alpha))
    }

/** Caption centred above a control (used for START). */
fun Modifier.captionAboveCenter(text: String?, cw: Dp, color: Color, alpha: Float): Modifier =
    if (text == null || alpha <= 0.01f) this else this.drawWithContent {
        drawContent()
        val p = captionPixel(cw)
        val w = pixelTextCellWidth(text) * p
        drawPixelText(text, Offset((size.width - w) / 2f, -7f * p - 2f * p), p, color.copy(alpha = alpha))
    }

/**
 * D-pad legend to the right of the pad: a mini pad icon with the arms a caption
 * covers lit, then the caption. Rows with identical captions are merged.
 */
fun Modifier.dpadLegend(controls: GameControls?, cw: Dp, color: Color, alpha: Float): Modifier =
    if (controls == null || controls.dpadLegend.isEmpty() || alpha <= 0.01f) this else this.drawWithContent {
        drawContent()
        val cwPx = cw.toPx()
        val p = cwPx * ShellGeometry.legendPixel(controls.legendMaxCells)
        val rows = controls.dpadLegend
        val rowH = 7f * p
        val gap = ShellGeometry.LEGEND_ROW_GAP_CELLS * p
        val total = rows.size * rowH + (rows.size - 1) * gap
        val left = size.width + cwPx * ShellGeometry.LEGEND_MARGIN
        var y = (size.height - total) / 2f
        for (i in rows.indices) {
            val row = rows[i]
            drawArmIcon(row.mask, left, y, p, color, alpha)
            drawPixelText(row.caption, Offset(left + 9f * p, y), p, color.copy(alpha = alpha))
            y += rowH + gap
        }
    }

/** 7x7-cell mini D-pad; lit arms are the ones the caption applies to. */
private fun DrawScope.drawArmIcon(mask: Int, left: Float, top: Float, p: Float, color: Color, alpha: Float) {
    val lit = color.copy(alpha = alpha)
    val dim = color.copy(alpha = alpha * 0.22f)
    drawRect(if (mask and DPAD_UP != 0) lit else dim, Offset(left + 2f * p, top), Size(3f * p, 2f * p))
    drawRect(if (mask and DPAD_DOWN != 0) lit else dim, Offset(left + 2f * p, top + 5f * p), Size(3f * p, 2f * p))
    drawRect(if (mask and DPAD_LEFT != 0) lit else dim, Offset(left, top + 2f * p), Size(2f * p, 3f * p))
    drawRect(if (mask and DPAD_RIGHT != 0) lit else dim, Offset(left + 5f * p, top + 2f * p), Size(2f * p, 3f * p))
    drawRect(color.copy(alpha = alpha * 0.55f), Offset(left + 2f * p, top + 2f * p), Size(3f * p, 3f * p))
}
