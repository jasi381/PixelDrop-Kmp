package com.jasmeet.pixeldrop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Static horizontal LCD scanlines. Cheap: one Canvas, no animation. */
@Composable
fun ScanlineOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 0.08f,
) {
    if (alpha <= 0f) return
    Canvas(modifier) {
        val step = 3.dp.toPx()
        val lineHeight = step * 0.42f
        val color = Color.Black.copy(alpha = alpha)
        var y = 0f
        while (y < size.height) {
            drawRect(color, Offset(0f, y), Size(size.width, lineHeight))
            y += step
        }
    }
}
