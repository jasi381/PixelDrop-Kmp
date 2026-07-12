package com.jasmeet.pixeldrop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.jasmeet.pixeldrop.ui.theme.CONSOLE_THEMES
import com.jasmeet.pixeldrop.ui.theme.ConsoleTheme
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import com.jasmeet.pixeldrop.ui.theme.PixelDropType

/** All physical inputs of the handheld, wired by the current screen.
 *  [onPowerHeldChange] reports the raw POWER press state so the app can run
 *  the hold-to-power-off timer; [onPower] is the tap action (power on). */
class ConsoleInput(
    val onUp: () -> Unit = {},
    val onDown: () -> Unit = {},
    val onLeft: () -> Unit = {},
    val onRight: () -> Unit = {},
    val onA: () -> Unit = {},
    val onB: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onPower: () -> Unit = {},
    val onStartHeldChange: (Boolean) -> Unit = {},
    val onPowerHeldChange: (Boolean) -> Unit = {},
)

private const val CONSOLE_ASPECT = 0.545f
private const val THEME_FADE_MS = 450

/**
 * The one physical console used by every screen. Ivory body, top branding,
 * recessed LCD, D-pad + A/B + SELECT/START + speaker.
 */
@Composable
fun RetroConsoleShell(
    input: ConsoleInput,
    modifier: Modifier = Modifier,
    lcdBrightness: Float = 1f,
    ledBrightness: Float = 1f,
    theme: ConsoleTheme = CONSOLE_THEMES.first(),
    lcdContent: @Composable BoxScope.() -> Unit,
) {
    // The paint job cross-fades rather than snapping, so picking a theme in
    // the paint shop reads like the shell being resprayed.
    val bodyLight by animateColorAsState(theme.bodyLight, tween(THEME_FADE_MS), label = "bodyLight")
    val body by animateColorAsState(theme.body, tween(THEME_FADE_MS), label = "body")
    val bodyDark by animateColorAsState(theme.bodyDark, tween(THEME_FADE_MS), label = "bodyDark")
    val edge by animateColorAsState(theme.edge, tween(THEME_FADE_MS), label = "edge")
    val buttonLight by animateColorAsState(theme.buttonLight, tween(THEME_FADE_MS), label = "buttonLight")
    val button by animateColorAsState(theme.button, tween(THEME_FADE_MS), label = "button")
    val buttonDark by animateColorAsState(theme.buttonDark, tween(THEME_FADE_MS), label = "buttonDark")

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cw = min(maxWidth, maxHeight * CONSOLE_ASPECT)
        val ch = cw / CONSOLE_ASPECT
        val corner = cw * 0.09f
        val shape = RoundedCornerShape(corner)

        Box(
            Modifier
                .size(cw, ch)
                .shadow(elevation = 22.dp, shape = shape, spotColor = Color.Black, ambientColor = Color.Black)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(bodyLight, body, bodyDark)))
        ) {
            // Molded inset line + edge shading.
            Canvas(Modifier.fillMaxSize()) {
                val inset = size.width * 0.028f
                drawRoundRect(
                    color = edge.copy(alpha = 0.55f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(corner.toPx() * 0.75f),
                    style = Stroke(width = size.width * 0.004f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(inset * 1.25f, inset * 1.25f),
                    size = Size(size.width - inset * 2.5f, size.height - inset * 2.5f),
                    cornerRadius = CornerRadius(corner.toPx() * 0.72f),
                    style = Stroke(width = size.width * 0.002f),
                )
            }

            Column(Modifier.fillMaxSize().padding(horizontal = cw * 0.062f)) {
                Spacer(Modifier.height(ch * 0.022f))
                ConsoleHeader(cw, ledBrightness)
                Spacer(Modifier.height(ch * 0.012f))
                LcdScreen(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.02f),
                    brightness = lcdBrightness,
                    corner = cw * 0.045f,
                    content = lcdContent,
                )
                Spacer(Modifier.height(ch * 0.010f))
                DotMatrixLabel()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    DPad(
                        size = cw * 0.30f,
                        onUp = input.onUp,
                        onDown = input.onDown,
                        onLeft = input.onLeft,
                        onRight = input.onRight,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = cw * 0.015f, y = ch * 0.035f),
                    )
                    // A raised, B lower-left, diagonal like the mocks.
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = -cw * 0.005f, y = ch * 0.018f)
                    ) {
                        RoundButton(
                            label = "A",
                            size = cw * 0.155f,
                            onPress = input.onA,
                            modifier = Modifier.align(Alignment.TopEnd),
                            faceLight = buttonLight,
                            face = button,
                            faceDark = buttonDark,
                        )
                        RoundButton(
                            label = "B",
                            size = cw * 0.155f,
                            onPress = input.onB,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = -cw * 0.17f, y = ch * 0.052f),
                            faceLight = buttonLight,
                            face = button,
                            faceDark = buttonDark,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = cw * 0.16f, y = -ch * 0.030f),
                        horizontalArrangement = Arrangement.spacedBy(cw * 0.05f),
                    ) {
                        PillButton(
                            "POWER",
                            width = cw * 0.115f,
                            onPress = input.onPower,
                            onHeldChange = input.onPowerHeldChange,
                        )
                        PillButton(
                            "START",
                            width = cw * 0.115f,
                            onPress = input.onStart,
                            onHeldChange = input.onStartHeldChange,
                        )
                    }
                    SpeakerGrill(
                        width = cw * 0.21f,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = -cw * 0.02f, y = -ch * 0.028f),
                    )
                    Screw(
                        size = cw * 0.028f,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = cw * 0.005f, y = -ch * 0.012f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleHeader(cw: Dp, ledBrightness: Float) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            BatteryLed(size = cw * 0.06f, brightness = ledBrightness)
            Text(
                text = "BATTERY",
                style = PixelDropType.ConsoleLabel,
                fontSize = 6.sp,
                color = PixelDropColors.LabelDark,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PixelText(
                text = "PIXEL DROP",
                pixel = cw * 0.0092f,
                color = PixelDropColors.LabelPurple,
            )
            Spacer(Modifier.height(cw * 0.016f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentLine(cw * 0.055f)
                Text(
                    text = "CANVAS CLASSICS",
                    style = PixelDropType.AccentLabel,
                    fontSize = 7.sp,
                    color = PixelDropColors.LabelPurpleMuted,
                    modifier = Modifier.padding(horizontal = cw * 0.02f),
                )
                AccentLine(cw * 0.055f)
            }
        }
        Screw(
            size = cw * 0.028f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = cw * 0.02f),
        )
    }
}

@Composable
private fun AccentLine(width: Dp) {
    Box(
        Modifier
            .width(width)
            .height(1.5.dp)
            .background(PixelDropColors.LabelPurpleMuted)
    )
}

@Composable
private fun DotMatrixLabel() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentLine(16.dp)
        Text(
            text = "DOT MATRIX WITH STEREO SOUND",
            style = PixelDropType.AccentLabel,
            fontSize = 6.sp,
            color = PixelDropColors.LabelDark,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        AccentLine(16.dp)
    }
}
