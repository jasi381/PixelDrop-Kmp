package com.jasmeet.pixeldrop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.jasmeet.pixeldrop.ui.settings.GAME_THEME_HINT_TEXT
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import kotlin.math.min

/**
 * One-time nudge drawn over the bottom edge of the menu LCD, in the LCD's own
 * dot-matrix language (inverse bar, olive text), so it reads as system UI and
 * not as a Material toast. Draw it inside the LCD content scope, aligned to
 * the bottom; the host decides when it is visible and dismisses it.
 *
 * With reduced motion it simply appears and disappears.
 */
@Composable
fun GameThemeHintStrip(
    visible: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
        exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(HINT_HEIGHT_FRACTION)
                .semantics {
                    contentDescription = "Tip: try game theme in settings"
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            val ink = PixelDropColors.LcdDark
            drawRect(ink, Offset.Zero, size)
            // Thin inner rule, like the menu's divider, so the bar feels built-in.
            drawRect(PixelDropColors.LcdGreen.copy(alpha = 0.5f), Offset(0f, size.height * 0.08f), Size(size.width, size.height * 0.03f))
            val cells = pixelTextCellWidth(GAME_THEME_HINT_TEXT)
            val px = min(size.width * 0.92f / cells, size.height * 0.5f / GLYPH_H)
            drawPixelTextCentered(
                GAME_THEME_HINT_TEXT,
                centerX = size.width / 2f,
                top = (size.height - GLYPH_H * px) / 2f + size.height * 0.04f,
                pixel = px,
                color = PixelDropColors.LcdGreen,
            )
        }
    }
}

private const val HINT_HEIGHT_FRACTION = 0.10f
