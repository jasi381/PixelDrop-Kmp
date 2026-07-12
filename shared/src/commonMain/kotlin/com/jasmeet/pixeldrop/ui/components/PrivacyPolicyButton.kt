package com.jasmeet.pixeldrop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors

/** Retro shield entry point that lives in the synthwave backdrop. */
@Composable
fun PrivacyPolicyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "privacyButtonPress",
    )

    Box(
        modifier = modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xEF171522))
            .border(2.dp, PixelDropColors.SynthSun, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Open privacy policy" },
        contentAlignment = Alignment.Center,
    ) {
        PixelShield(Modifier.size(28.dp))
    }
}

@Composable
private fun PixelShield(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = size.minDimension / 9f
        val rows = arrayOf(
            "001111100",
            "011111110",
            "111111111",
            "111101111",
            "111101111",
            "011101110",
            "011111110",
            "001111100",
            "000111000",
        )
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, pixel ->
                if (pixel == '1') {
                    drawRect(
                        color = PixelDropColors.SynthSun,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
        drawRect(
            color = Color(0xFF171522),
            topLeft = Offset(4f * cell, 3f * cell),
            size = Size(cell, 3f * cell),
        )
        drawRect(
            color = Color(0xFF171522),
            topLeft = Offset(3f * cell, 4f * cell),
            size = Size(3f * cell, cell),
        )
    }
}
