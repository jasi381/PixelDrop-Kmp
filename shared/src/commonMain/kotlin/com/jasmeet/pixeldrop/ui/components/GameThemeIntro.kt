package com.jasmeet.pixeldrop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.jasmeet.pixeldrop.game.GameId
import com.jasmeet.pixeldrop.ui.settings.GameThemeIntroStep
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import com.jasmeet.pixeldrop.ui.theme.gameConsoleTheme
import kotlin.math.min

/** Title of each previewed game, in the pixel font's character set. */
private fun gameLabel(id: GameId): String = gameConsoleTheme(id).label

/**
 * Interactive first-run intro for GAME THEME, drawn over the menu LCD in the
 * console's own dot-matrix language. While it is open the *real* shell morphs
 * to [previewGame]'s paint (the host feeds the same game to the theme
 * resolver), so the feature is shown rather than described: the D-pad steps
 * through games, A turns it on, B leaves it for later.
 *
 * Offer:      NEW! / GAME THEME / mini console in the game's paint / < NAME > / A ON  B LATER
 * Confirmed:  GAME THEME ON / MORPHS WHEN YOU PLAY / CHANGE IT IN SETTINGS
 *
 * With reduced motion the blinking title and arrows hold still.
 */
@Composable
fun GameThemeIntro(
    visible: Boolean,
    step: GameThemeIntroStep,
    previewGame: GameId,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(if (reducedMotion) 0 else 200)),
        exit = fadeOut(tween(if (reducedMotion) 0 else 160)),
    ) {
        val transition = rememberInfiniteTransition(label = "theme-intro-blink")
        val blink by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
            label = "blink",
        )
        val on = reducedMotion || blink < 0.6f
        val description = when (step) {
            GameThemeIntroStep.Offer ->
                "New: game theme. The console matches each game. Press A to turn on, B for later."
            GameThemeIntroStep.Confirmed -> "Game theme is on. Change it in settings."
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            val w = size.width
            val h = size.height
            val ink = PixelDropColors.LcdDark
            val paper = PixelDropColors.LcdGreen
            drawRect(paper, Offset.Zero, size)
            drawRect(ink, Offset(w * 0.03f, h * 0.02f), Size(w * 0.94f, h * 0.96f), style = Stroke(width = w * 0.012f))

            when (step) {
                GameThemeIntroStep.Offer -> drawOffer(previewGame, on, ink)
                GameThemeIntroStep.Confirmed -> drawConfirmed(ink)
            }
        }
    }
}

private fun DrawScope.centered(text: String, y: Float, maxWidthFraction: Float, maxPixel: Float, color: androidx.compose.ui.graphics.Color) {
    val cells = pixelTextCellWidth(text)
    val px = min(size.width * maxWidthFraction / cells, maxPixel)
    drawPixelTextCentered(text, centerX = size.width / 2f, top = y, pixel = px, color = color)
}

private fun DrawScope.drawOffer(game: GameId, blinkOn: Boolean, ink: androidx.compose.ui.graphics.Color) {
    val w = size.width
    val h = size.height
    val unit = w / 118f
    if (blinkOn) centered("NEW!", h * 0.06f, 0.30f, unit * 1.9f, ink)
    centered("GAME THEME", h * 0.15f, 0.80f, unit * 1.7f, ink)

    // Mini console in the previewed game's paint: body, screen, D-pad, A/B keys.
    val theme = gameConsoleTheme(game)
    val bw = w * 0.46f
    val bh = h * 0.30f
    val bx = (w - bw) / 2f
    val by = h * 0.27f
    drawRect(theme.bodyDark, Offset(bx, by + bh * 0.05f), Size(bw, bh))
    drawRect(theme.body, Offset(bx, by), Size(bw, bh))
    drawRect(theme.bodyLight, Offset(bx, by), Size(bw, bh * 0.06f))
    val sx = bx + bw * 0.12f
    drawRect(ink, Offset(sx, by + bh * 0.10f), Size(bw * 0.76f, bh * 0.42f))
    drawRect(PixelDropColors.LcdGreen, Offset(sx + bw * 0.03f, by + bh * 0.14f), Size(bw * 0.70f, bh * 0.34f))
    val dpad = theme.dpadFace
    val cx = bx + bw * 0.26f
    val cy = by + bh * 0.76f
    val arm = bw * 0.05f
    drawRect(dpad, Offset(cx - arm * 1.5f, cy - arm * 0.5f), Size(arm * 3f, arm))
    drawRect(dpad, Offset(cx - arm * 0.5f, cy - arm * 1.5f), Size(arm, arm * 3f))
    val key = arm * 1.5f
    drawRect(theme.button, Offset(bx + bw * 0.66f, cy - key * 0.2f), Size(key, key))
    drawRect(theme.button, Offset(bx + bw * 0.80f, cy - key * 0.9f), Size(key, key))

    // < NAME > : chevrons blink to say "press me".
    val label = gameLabel(game)
    val nameY = h * 0.62f
    centered(label, nameY, 0.60f, unit * 1.5f, ink)
    if (blinkOn) {
        val px = unit * 1.5f
        drawPixelText("<", Offset(w * 0.06f, nameY), px, ink)
        drawPixelText(">", Offset(w * 0.94f - 5 * px, nameY), px, ink)
    }
    centered("UP DOWN: PREVIEW", h * 0.73f, 0.72f, unit * 1.1f, ink)
    centered("CONSOLE MATCHES EACH GAME", h * 0.80f, 0.86f, unit * 1.0f, ink)

    // Action bar: inverse strip, same idiom as the menu's selection bar.
    val barTop = h * 0.88f
    drawRect(ink, Offset(w * 0.03f, barTop), Size(w * 0.94f, h * 0.09f))
    val barPx = unit * 1.2f
    drawPixelTextCentered("A: TURN ON   B: LATER", centerX = w / 2f, top = barTop + (h * 0.09f - 7 * barPx) / 2f, pixel = barPx, color = PixelDropColors.LcdGreen)
}

private fun DrawScope.drawConfirmed(ink: androidx.compose.ui.graphics.Color) {
    val h = size.height
    val unit = size.width / 118f
    centered("GAME THEME", h * 0.30f, 0.80f, unit * 1.9f, ink)
    centered("ON", h * 0.40f, 0.30f, unit * 3.2f, ink)
    centered("MORPHS WHEN YOU PLAY", h * 0.62f, 0.86f, unit * 1.2f, ink)
    centered("CHANGE IT IN SETTINGS", h * 0.72f, 0.86f, unit * 1.1f, ink)
}
