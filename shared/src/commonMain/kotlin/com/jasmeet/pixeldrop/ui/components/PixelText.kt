package com.jasmeet.pixeldrop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp

/**
 * Original 5x7 dot-matrix font drawn as Canvas rects, so every LCD string
 * shares the same chunky retro look at any scale.
 */
const val GLYPH_W = 5
const val GLYPH_H = 7
const val GLYPH_ADVANCE = 6 // 5 columns + 1 gap

private fun g(vararg rows: String) = IntArray(GLYPH_H) { rows[it].toInt(2) }

private val GLYPHS: Map<Char, IntArray> = mapOf(
    'A' to g("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'B' to g("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'C' to g("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
    'D' to g("11100", "10010", "10001", "10001", "10001", "10010", "11100"),
    'E' to g("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'F' to g("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'G' to g("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
    'H' to g("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to g("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
    'J' to g("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
    'K' to g("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'L' to g("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to g("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to g("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
    'O' to g("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'P' to g("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'Q' to g("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    'R' to g("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to g("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to g("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to g("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'V' to g("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'W' to g("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
    'X' to g("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    'Y' to g("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    'Z' to g("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
    '0' to g("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to g("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to g("01110", "10001", "00001", "00110", "01000", "10000", "11111"),
    '3' to g("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
    '4' to g("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to g("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to g("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to g("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to g("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to g("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    ':' to g("00000", "00100", "00000", "00000", "00000", "00100", "00000"),
    '.' to g("00000", "00000", "00000", "00000", "00000", "00110", "00110"),
    ',' to g("00000", "00000", "00000", "00000", "00110", "00100", "01000"),
    '-' to g("00000", "00000", "00000", "01110", "00000", "00000", "00000"),
    '/' to g("00001", "00001", "00010", "00100", "01000", "10000", "10000"),
    '!' to g("00100", "00100", "00100", "00100", "00100", "00000", "00100"),
    '>' to g("01000", "01100", "01110", "01111", "01110", "01100", "01000"),
    '<' to g("00010", "00110", "01110", "11110", "01110", "00110", "00010"),
    '#' to g("11111", "11111", "11111", "11111", "11111", "11111", "11111"),
    ' ' to g("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
)

/** Width of [text] in font cells (pixels of the dot matrix, not px). */
fun pixelTextCellWidth(text: String): Int =
    if (text.isEmpty()) 0 else text.length * GLYPH_ADVANCE - 1

/** Deterministic per-dot hash in 0..1, used for pixel-by-pixel reveals. */
internal fun dotHash(i: Int, r: Int, c: Int): Float {
    var h = i * 73_856_093 xor r * 19_349_663 xor c * 83_492_791
    h = h xor (h shr 13)
    h *= 0x5BD1E995.toInt()
    return ((h shr 8) and 0xFFFF) / 65535f
}

/**
 * Draws [text] with the dot-matrix font. [reveal] in 0..1 shows only a
 * pseudo-random subset of dots, giving the boot "assembling" effect.
 */
fun DrawScope.drawPixelText(
    text: String,
    topLeft: Offset,
    pixel: Float,
    color: Color,
    reveal: Float = 1f,
) {
    if (reveal <= 0f) return
    val upper = text.uppercase()
    for (i in upper.indices) {
        val glyph = GLYPHS[upper[i]] ?: continue
        val gx = topLeft.x + i * GLYPH_ADVANCE * pixel
        for (r in 0 until GLYPH_H) {
            val bits = glyph[r]
            for (c in 0 until GLYPH_W) {
                if ((bits shr (GLYPH_W - 1 - c)) and 1 == 0) continue
                if (reveal < 1f && dotHash(i, r, c) > reveal) continue
                drawRect(
                    color = color,
                    topLeft = Offset(gx + c * pixel, topLeft.y + r * pixel),
                    size = Size(pixel, pixel),
                )
            }
        }
    }
}

fun DrawScope.drawPixelTextCentered(
    text: String,
    centerX: Float,
    top: Float,
    pixel: Float,
    color: Color,
    reveal: Float = 1f,
) = drawPixelText(
    text = text,
    topLeft = Offset(centerX - pixelTextCellWidth(text) * pixel / 2f, top),
    pixel = pixel,
    color = color,
    reveal = reveal,
)

fun DrawScope.drawPixelTextRightAligned(
    text: String,
    right: Float,
    top: Float,
    pixel: Float,
    color: Color,
) = drawPixelText(
    text = text,
    topLeft = Offset(right - pixelTextCellWidth(text) * pixel, top),
    pixel = pixel,
    color = color,
)

/** Standalone composable pixel-font text, used for console-body branding. */
@Composable
fun PixelText(
    text: String,
    pixel: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier.size(
            width = pixel * pixelTextCellWidth(text),
            height = pixel * GLYPH_H,
        )
    ) {
        drawPixelText(text, Offset.Zero, pixel.toPx(), color)
    }
}
