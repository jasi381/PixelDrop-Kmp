package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.pixelTextCellWidth
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors

val PAUSE_MENU_ITEMS = listOf("RESUME", "RESTART", "MAIN MENU")

/** Pause LCD: PAUSED headline, dashed rule, menu with inverted selection. */
@Composable
fun PauseScreen(selectedIndex: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val ink = PixelDropColors.LcdDark

        drawCornerMarks()

        drawPixelTextCentered("PAUSED", w / 2f, h * 0.16f, w / 55f, ink)

        // Dashed rule under the headline.
        val ry = h * 0.30f
        var x = w * 0.12f
        while (x < w * 0.88f) {
            drawRect(ink.copy(alpha = 0.6f), Offset(x, ry), Size(w * 0.02f, h * 0.006f))
            x += w * 0.035f
        }

        // "MAIN MENU" is the longest item now; px sized so it still clears
        // the right edge with margin.
        val px = w / 95f
        val itemX = w * 0.30f
        PAUSE_MENU_ITEMS.forEachIndexed { i, item ->
            val y = h * 0.40f + i * h * 0.135f
            if (i == selectedIndex) {
                // Inverted selection bar with cursor arrow outside it.
                val barPad = px * 4f
                val barW = pixelTextCellWidth(item) * px + barPad * 2
                drawRect(
                    color = ink,
                    topLeft = Offset(itemX - barPad, y - px * 3f),
                    size = Size(barW, px * 13f),
                )
                drawPixelText(">", Offset(itemX - barPad - px * 9f, y), px, ink)
                drawPixelText(item, Offset(itemX, y), px, PixelDropColors.LcdGreen)
            } else {
                drawPixelText(item, Offset(itemX, y), px, ink)
            }
        }
    }
}

/** Small corner ticks used by the pause/menu style frames in the mocks. */
fun DrawScope.drawCornerMarks() {
    val w = size.width
    val h = size.height
    val ink = PixelDropColors.LcdDark.copy(alpha = 0.7f)
    val u = w * 0.012f
    val inset = w * 0.055f
    val corners = listOf(
        Offset(inset, inset),
        Offset(w - inset, inset),
        Offset(inset, h - inset),
        Offset(w - inset, h - inset),
    )
    corners.forEach { c ->
        drawRect(ink, Offset(c.x - u * 1.5f, c.y - u * 0.5f), Size(u * 3f, u))
        drawRect(ink, Offset(c.x - u * 0.5f, c.y - u * 1.5f), Size(u, u * 3f))
    }
}
