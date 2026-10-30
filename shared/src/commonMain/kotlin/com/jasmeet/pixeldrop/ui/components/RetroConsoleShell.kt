package com.jasmeet.pixeldrop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.jasmeet.pixeldrop.ui.haptics.ConsoleButton
import com.jasmeet.pixeldrop.ui.theme.CONSOLE_THEMES
import com.jasmeet.pixeldrop.ui.theme.GameControls
import com.jasmeet.pixeldrop.ui.theme.GameShellStyle
import com.jasmeet.pixeldrop.ui.theme.Livery
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
    /** A lifted; only games with hold-to-charge behavior need it. */
    val onAReleased: () -> Unit = {},
    /** D-pad UP / DOWN lifted; only games with a held-direction latch (RALLY DUEL's paddle) need them. */
    val onUpReleased: () -> Unit = {},
    val onDownReleased: () -> Unit = {},
    val onB: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onPower: () -> Unit = {},
    val onStartHeldChange: (Boolean) -> Unit = {},
    val onPowerHeldChange: (Boolean) -> Unit = {},
    /** Finger-down on any physical control (once per press, never on auto-repeat).
     *  The host maps it to at most one haptic and may use it to dismiss hints. */
    val onButtonPress: (ConsoleButton) -> Unit = {},
)

private const val CONSOLE_ASPECT = 0.545f

// Estimated face (header + LCD + label) and control-area heights as a multiple of the body width;
// the control area stays above ShellGeometry.AREA_H_MIN so decals and captions keep their room.
private const val FACE_H = 1.22f
private const val CONTROLS_H = 0.80f
private const val WIDE_ASPECT = 1.8f
private const val HINGE_GAP = 0.04f
const val THEME_FADE_MS = 450

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
    themeChangeToken: Int = 0,
    /** Cross-fade length for paint changes; the host shortens it for the
     *  per-game morph and zeroes it under reduced motion. */
    themeFadeMs: Int = THEME_FADE_MS,
    /** GAME THEME adaptation (key emphasis, decals, badge, teaching captions).
     *  The default reproduces the plain shell exactly. */
    style: GameShellStyle = GameShellStyle.Default,
    /** Half-open fold state; picks the split layouts so nothing sits on the hinge. */
    posture: FoldPosture = FoldPosture.Flat,
    lcdContent: @Composable BoxScope.() -> Unit,
) {
    // The paint job cross-fades rather than snapping, so picking a theme in
    // the paint shop reads like the shell being resprayed.
    val bodyLight by animateColorAsState(theme.bodyLight, tween(themeFadeMs), label = "bodyLight")
    val body by animateColorAsState(theme.body, tween(themeFadeMs), label = "body")
    val bodyDark by animateColorAsState(theme.bodyDark, tween(themeFadeMs), label = "bodyDark")
    val edge by animateColorAsState(theme.edge, tween(themeFadeMs), label = "edge")
    val buttonLight by animateColorAsState(theme.buttonLight, tween(themeFadeMs), label = "buttonLight")
    val button by animateColorAsState(theme.button, tween(themeFadeMs), label = "button")
    val buttonDark by animateColorAsState(theme.buttonDark, tween(themeFadeMs), label = "buttonDark")
    val dpadLight by animateColorAsState(theme.dpadLight, tween(themeFadeMs), label = "dpadLight")
    val dpadFace by animateColorAsState(theme.dpadFace, tween(themeFadeMs), label = "dpadFace")
    val dpadDark by animateColorAsState(theme.dpadDark, tween(themeFadeMs), label = "dpadDark")
    val dpadArrow by animateColorAsState(theme.onDpad, tween(themeFadeMs), label = "dpadArrow")
    val labelColor by animateColorAsState(theme.onBody, tween(themeFadeMs), label = "labelColor")
    val mutedLabelColor by animateColorAsState(theme.onBodyMuted, tween(themeFadeMs), label = "mutedLabelColor")
    // Control emphasis eases with the same fade as the paint (snaps at 0 ms for reduced motion).
    val fadeSpec = tween<Float>(themeFadeMs)
    val upE by animateFloatAsState(if (style.upUsed) 1f else 0f, fadeSpec, label = "upE")
    val downE by animateFloatAsState(if (style.downUsed) 1f else 0f, fadeSpec, label = "downE")
    val leftE by animateFloatAsState(if (style.leftUsed) 1f else 0f, fadeSpec, label = "leftE")
    val rightE by animateFloatAsState(if (style.rightUsed) 1f else 0f, fadeSpec, label = "rightE")
    val rockerE by animateFloatAsState(if (style.rocker) 1f else 0f, fadeSpec, label = "rockerE")
    val rockerVE by animateFloatAsState(if (style.verticalRocker) 1f else 0f, fadeSpec, label = "rockerVE")
    val aE by animateFloatAsState(if (style.aUsed) 1f else 0f, fadeSpec, label = "aE")
    val bE by animateFloatAsState(if (style.bUsed) 1f else 0f, fadeSpec, label = "bE")
    val aScale by animateFloatAsState(style.aScale, fadeSpec, label = "aScale")
    val bScale by animateFloatAsState(style.bScale, fadeSpec, label = "bScale")
    val liveryAlpha by animateFloatAsState(if (style.livery != null) 1f else 0f, fadeSpec, label = "liveryAlpha")
    val captionAlpha by animateFloatAsState(
        if (style.captionsVisible) 1f else 0f,
        tween(if (themeFadeMs == 0) 0 else 300),
        label = "captionAlpha",
    )
    // Keep the last game's decals/captions around so they can fade out instead of vanishing.
    val liveryHolder = remember { arrayOfNulls<Livery>(1) }
    val controlsHolder = remember { arrayOfNulls<GameControls>(1) }
    style.livery?.let { liveryHolder[0] = it }
    style.controls?.let { controlsHolder[0] = it }
    val captionControls = controlsHolder[0]
    val respray = remember { Animatable(1f) }
    LaunchedEffect(themeChangeToken) {
        if (themeChangeToken > 0) {
            respray.snapTo(0f)
            respray.animateTo(1f, tween(620))
        }
    }

    // Movable so a fold/unfold layout switch keeps the running game's state.
    val latestLcd by rememberUpdatedState(lcdContent)
    val lcd = remember { movableContentOf<BoxScope> { latestLcd(it) } }

    // Everything below is laid out from a reference console width [cw] (and its
    // portrait height [ch]) so each layout can scale the same parts independently.
    val faceBlock: @Composable (Dp, Dp) -> Unit = { cw, ch ->
        Spacer(Modifier.height(ch * 0.022f))
        ConsoleHeader(cw, ledBrightness, labelColor, mutedLabelColor)
        Spacer(Modifier.height(ch * 0.012f))
        LcdScreen(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.02f),
            brightness = lcdBrightness,
            corner = cw * 0.045f,
        ) { lcd(this) }
        Spacer(Modifier.height(ch * 0.010f))
        DotMatrixLabel(labelColor, mutedLabelColor)
    }
    val dpadKey: @Composable (Dp, Modifier) -> Unit = { cw, m ->
        DPad(
            size = cw * 0.30f,
            onUp = input.onUp,
            onDown = input.onDown,
            onLeft = input.onLeft,
            onRight = input.onRight,
            faceLight = dpadLight,
            face = dpadFace,
            faceDark = dpadDark,
            arrowColor = dpadArrow,
            onButtonPress = input.onButtonPress,
            onUpRelease = input.onUpReleased,
            onDownRelease = input.onDownReleased,
            upEmphasis = upE,
            downEmphasis = downE,
            leftEmphasis = leftE,
            rightEmphasis = rightE,
            rocker = rockerE,
            verticalRocker = rockerVE,
            dimColor = body,
            modifier = m.dpadLegend(captionControls, cw, labelColor, captionAlpha),
        )
    }
    // A raised, B lower-left, diagonal like the mocks.
    val abKeys: @Composable (Dp, Dp, Modifier) -> Unit = { cw, ch, m ->
        Box(m) {
            RoundButton(
                label = "A",
                size = cw * 0.155f,
                onPress = input.onA,
                onRelease = input.onAReleased,
                onPressFeedback = { input.onButtonPress(ConsoleButton.A) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .captionBelowEnd(captionControls?.a, cw, labelColor, captionAlpha),
                faceLight = buttonLight,
                face = button,
                faceDark = buttonDark,
                labelColor = labelColor,
                emphasis = aE,
                drawScale = aScale,
                dimColor = body,
            )
            RoundButton(
                label = "B",
                size = cw * 0.155f,
                onPress = input.onB,
                onPressFeedback = { input.onButtonPress(ConsoleButton.B) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -cw * 0.17f, y = ch * 0.052f)
                    .captionBelowEnd(captionControls?.b, cw, labelColor, captionAlpha),
                faceLight = buttonLight,
                face = button,
                faceDark = buttonDark,
                labelColor = labelColor,
                emphasis = bE,
                drawScale = bScale,
                dimColor = body,
            )
        }
    }
    val powerPill: @Composable (Dp) -> Unit = { cw ->
        PillButton(
            "POWER",
            width = cw * 0.115f,
            onPress = input.onPower,
            onHeldChange = input.onPowerHeldChange,
            onPressFeedback = { input.onButtonPress(ConsoleButton.Power) },
            labelColor = labelColor,
        )
    }
    val startPill: @Composable (Dp) -> Unit = { cw ->
        PillButton(
            "START",
            width = cw * 0.115f,
            onPress = input.onStart,
            onHeldChange = input.onStartHeldChange,
            onPressFeedback = { input.onButtonPress(ConsoleButton.Start) },
            labelColor = labelColor,
            modifier = Modifier.captionAboveCenter(captionControls?.start, cw, labelColor, captionAlpha),
        )
    }
    // The portrait control area, also reused under the hinge in the split layouts.
    val controlsArea: @Composable BoxScope.(Dp, Dp) -> Unit = { cw, ch ->
        dpadKey(cw, Modifier.align(Alignment.TopStart).offset(x = cw * 0.015f, y = ch * 0.035f))
        abKeys(cw, ch, Modifier.align(Alignment.TopEnd).offset(x = -cw * 0.005f, y = ch * 0.018f))
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = cw * 0.16f, y = -ch * 0.030f),
            horizontalArrangement = Arrangement.spacedBy(cw * 0.05f),
        ) {
            powerPill(cw)
            startPill(cw)
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
    val shellBody: @Composable (Modifier, Dp, @Composable BoxScope.() -> Unit) -> Unit = { m, cw, content ->
        ShellBody(m, cw, bodyLight, body, bodyDark, edge) {
            content()
            // A short pixel scan makes a paint selection feel like the shell
            // is being re-sprayed instead of merely cross-fading.
            if (respray.value < 1f) RespraySweep(Modifier.matchParentSize(), respray.value, labelColor)
        }
    }
    // Split layouts: one body holds the face (LCD up top), the other the controls.
    val faceBody: @Composable (Dp) -> Unit = { cw ->
        shellBody(Modifier.width(cw), cw) {
            Column(Modifier.padding(horizontal = cw * 0.062f)) {
                faceBlock(cw, cw / CONSOLE_ASPECT)
                Spacer(Modifier.height(cw * 0.03f))
            }
        }
    }
    val controlsBody: @Composable (Dp) -> Unit = { cw ->
        shellBody(Modifier.size(cw, cw * CONTROLS_H), cw) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = cw * 0.062f, end = cw * 0.062f, top = cw * 0.03f)
                    // Silk-screen decals + badge print on the body, behind every control.
                    .liveryBehind(liveryHolder[0], liveryAlpha, LiveryInk(labelColor, button, buttonDark))
            ) { controlsArea(cw, cw / CONSOLE_ASPECT) }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Read once: the DSL marker hides these from nested Box/Row/Column lambdas.
        val maxW = maxWidth
        val maxH = maxHeight
        val layout = consoleLayoutFor(maxW, maxH, posture)
        val firstLayout = remember { booleanArrayOf(true) }
        LaunchedEffect(layout) {
            if (firstLayout[0]) {
                firstLayout[0] = false
            } else if (themeFadeMs > 0) {
                respray.snapTo(0f)
                respray.animateTo(1f, tween(620))
            }
        }

        when (layout) {
            ConsoleLayout.Portrait -> {
                val cw = min(maxW, maxH * CONSOLE_ASPECT)
                val ch = cw / CONSOLE_ASPECT
                shellBody(Modifier.size(cw, ch), cw) {
                    Column(Modifier.fillMaxSize().padding(horizontal = cw * 0.062f)) {
                        faceBlock(cw, ch)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                // Silk-screen decals + badge print on the body, behind every control.
                                .liveryBehind(liveryHolder[0], liveryAlpha, LiveryInk(labelColor, button, buttonDark))
                        ) { controlsArea(cw, ch) }
                    }
                }
            }

            // GBA-style: D-pad left, LCD centre, A/B right.
            // shortcut: no silk-screen decals here (ShellGeometry assumes the portrait control area), add if wanted.
            ConsoleLayout.Wide -> {
                val h = min(maxH, maxW / WIDE_ASPECT)
                val w = h * WIDE_ASPECT
                val faceW = w * 0.46f
                val panelW = (w - faceW) / 2f
                val cw = panelW / 0.55f
                val ch = cw / CONSOLE_ASPECT
                shellBody(Modifier.size(w, h), h * 0.5f) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(panelW).fillMaxHeight().padding(start = panelW * 0.04f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Column(
                                Modifier.width(cw * 0.30f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(cw * 0.10f),
                            ) {
                                dpadKey(cw, Modifier)
                                powerPill(cw)
                            }
                        }
                        Column(Modifier.width(faceW).padding(horizontal = faceW * 0.062f)) {
                            faceBlock(faceW, faceW / CONSOLE_ASPECT)
                        }
                        Column(
                            Modifier.width(panelW).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            abKeys(cw, ch, Modifier.size(cw * 0.33f, cw * 0.155f + ch * 0.052f))
                            startPill(cw)
                            SpeakerGrill(width = cw * 0.21f)
                        }
                    }
                }
            }

            // Flip-style tabletop: LCD above the hinge, controls below, like a GBA SP.
            ConsoleLayout.Clamshell -> {
                val gap = maxH * HINGE_GAP
                val halfH = (maxH - gap) / 2f
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(halfH), contentAlignment = Alignment.BottomCenter) {
                        faceBody(min(maxW, halfH / FACE_H))
                    }
                    Spacer(Modifier.height(gap))
                    Box(Modifier.fillMaxWidth().height(halfH), contentAlignment = Alignment.TopCenter) {
                        controlsBody(min(maxW, halfH / CONTROLS_H))
                    }
                }
            }

            // Fold held like a book: LCD on the left panel, controls on the right, nothing under the crease.
            ConsoleLayout.BookSplit -> {
                val gap = maxW * HINGE_GAP
                val halfW = (maxW - gap) / 2f
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(halfW).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        faceBody(min(halfW, maxH / FACE_H))
                    }
                    Spacer(Modifier.width(gap))
                    Box(Modifier.width(halfW).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        controlsBody(min(halfW, maxH / CONTROLS_H))
                    }
                }
            }
        }
    }
}

/** Molded body: gradient, drop shadow, inset line and edge shading. [cw] sizes the corner radius. */
@Composable
private fun ShellBody(
    modifier: Modifier,
    cw: Dp,
    bodyLight: Color,
    body: Color,
    bodyDark: Color,
    edge: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    val corner = cw * 0.09f
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .shadow(elevation = 22.dp, shape = shape, spotColor = Color.Black, ambientColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(bodyLight, body, bodyDark)))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val base = kotlin.math.min(size.width, size.height)
            val inset = base * 0.028f
            drawRoundRect(
                color = edge.copy(alpha = 0.55f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(corner.toPx() * 0.75f),
                style = Stroke(width = base * 0.004f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.5f),
                topLeft = Offset(inset * 1.25f, inset * 1.25f),
                size = Size(size.width - inset * 2.5f, size.height - inset * 2.5f),
                cornerRadius = CornerRadius(corner.toPx() * 0.72f),
                style = Stroke(width = base * 0.002f),
            )
        }
        content()
    }
}

@Composable
private fun RespraySweep(modifier: Modifier, progress: Float, labelColor: Color) {
    Canvas(modifier) {
        val bandY = size.height * progress
        val cell = size.width * 0.022f
        val row = (bandY / cell).toInt()
        for (column in 0..46) {
            val phase = ((column * 7 + row * 3) % 9) / 9f
            val y = bandY - phase * cell * 5f
            if (y in 0f..size.height) {
                drawRect(
                    color = labelColor.copy(alpha = (1f - progress) * 0.32f),
                    topLeft = Offset(column * cell, y),
                    size = Size(cell * 0.72f, cell * 0.72f),
                )
            }
        }
        drawRect(
            color = labelColor.copy(alpha = 0.28f * (1f - progress)),
            topLeft = Offset(0f, bandY.coerceIn(0f, size.height)),
            size = Size(size.width, cell * 0.22f),
        )
    }
}

@Composable
private fun ConsoleHeader(cw: Dp, ledBrightness: Float, labelColor: Color, mutedLabelColor: Color) {
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
                color = labelColor,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PixelText(
                text = "PIXEL DROP",
                pixel = cw * 0.0092f,
                color = labelColor,
            )
            Spacer(Modifier.height(cw * 0.016f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentLine(cw * 0.055f, mutedLabelColor)
                Text(
                    text = "CANVAS CLASSICS",
                    style = PixelDropType.AccentLabel,
                    fontSize = 7.sp,
                    color = mutedLabelColor,
                    modifier = Modifier.padding(horizontal = cw * 0.02f),
                )
                AccentLine(cw * 0.055f, mutedLabelColor)
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
private fun AccentLine(width: Dp, color: Color) {
    Box(
        Modifier
            .width(width)
            .height(1.5.dp)
            .background(color)
    )
}

@Composable
private fun DotMatrixLabel(labelColor: Color, mutedLabelColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentLine(16.dp, mutedLabelColor)
        Text(
            text = "DOT MATRIX WITH STEREO SOUND",
            style = PixelDropType.AccentLabel,
            fontSize = 6.sp,
            color = labelColor,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        AccentLine(16.dp, mutedLabelColor)
    }
}
