package com.jasmeet.pixeldrop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors

/**
 * Recessed dark bezel with the olive LCD inside. [brightness] 0..1 lets the
 * boot sequence wake the panel from dim to full green.
 */
@Composable
fun LcdScreen(
    modifier: Modifier = Modifier,
    brightness: Float = 1f,
    corner: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val b = brightness.coerceIn(0f, 1f)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.verticalGradient(
                    listOf(PixelDropColors.BezelEdge, PixelDropColors.BezelDark)
                )
            )
            .padding(corner * 0.95f)
    ) {
        val lcdColor = lerp(PixelDropColors.LcdOff, PixelDropColors.LcdGreen, b)
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(corner * 0.3f))
                .background(lcdColor)
        ) {
            content()
            ScanlineOverlay(Modifier.fillMaxSize(), alpha = 0.05f + 0.05f * b)
            // Glass edge shadow + faint top sheen.
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.30f),
                    style = Stroke(width = size.minDimension * 0.015f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx() * 0.3f),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.05f * b), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.18f,
                    ),
                    topLeft = Offset.Zero,
                )
            }
        }
    }
}
