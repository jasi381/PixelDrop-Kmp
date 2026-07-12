package com.jasmeet.pixeldrop.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.jasmeet.pixeldrop.game.GameState
import com.jasmeet.pixeldrop.ui.components.drawPixelText
import com.jasmeet.pixeldrop.ui.components.drawPixelTextCentered
import com.jasmeet.pixeldrop.ui.components.drawPixelTextRightAligned
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import com.jasmeet.pixeldrop.ui.components.retroGrouped
import com.jasmeet.pixeldrop.ui.components.retroPad

/** Game-over LCD: headline, final stats, blinking retry hint, block skyline. */
@Composable
fun GameOverScreen(state: GameState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gameover-blink")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "blink",
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val ink = PixelDropColors.LcdDark

        drawCornerMarks()
        drawPixelTextCentered("GAME OVER", w / 2f, h * 0.14f, w / 62f, ink)

        val px = w / 85f
        val labelX = w * 0.12f
        val valueRight = w * 0.88f
        val rows = listOf(
            "SCORE" to retroGrouped(state.score),
            "LEVEL" to retroPad(state.level, 2),
            "LINES" to retroPad(state.lines, 1),
        )
        rows.forEachIndexed { i, (label, value) ->
            val y = h * 0.34f + i * h * 0.115f
            drawPixelText(label, Offset(labelX, y), px, ink)
            drawPixelTextRightAligned(value, valueRight, y, px, ink)
        }

        if (phase < 0.62f) {
            drawPixelTextCentered("PRESS START", w / 2f, h * 0.72f, w / 110f, ink)
        }

        drawBlockFrieze(top = false, seed = state.lines + 3, alpha = 1f)
    }
}
