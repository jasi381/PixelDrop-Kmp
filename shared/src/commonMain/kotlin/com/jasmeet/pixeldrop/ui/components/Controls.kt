package com.jasmeet.pixeldrop.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.jasmeet.pixeldrop.ui.haptics.ConsoleButton
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import com.jasmeet.pixeldrop.ui.theme.PixelDropType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Spring used by every physical button to animate its pressed state —
 *  fast enough to feel snappy, with a hint of bounce on release. */
private val pressSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)

/**
 * Press handling with optional auto-repeat (for D-pad left/right/down).
 * [fireOnRelease] defers the action to finger-up (used by SELECT/START so a
 * long two-pill power-off hold doesn't fire their tap actions on the way in;
 * the release after a completed hold lands in the guarded PoweringOff state).
 */
@Composable
fun Modifier.consolePressable(
    repeatable: Boolean = false,
    fireOnRelease: Boolean = false,
    onPressedChange: (Boolean) -> Unit = {},
    /** Fires once on finger-down, before [action]; the host maps it to a haptic. */
    onPressFeedback: () -> Unit = {},
    action: () -> Unit,
): Modifier {
    val currentAction by rememberUpdatedState(action)
    val currentPressedChange by rememberUpdatedState(onPressedChange)
    val scope = rememberCoroutineScope()
    val currentPressFeedback by rememberUpdatedState(onPressFeedback)
    return this.pointerInput(repeatable, fireOnRelease) {
        detectTapGestures(
            onTap = { if (fireOnRelease) currentAction() },
            onPress = {
                // One haptic per physical press, chosen by the host; auto-repeat
                // steps below stay silent so holding a direction never hums.
                currentPressFeedback()
                currentPressedChange(true)
                if (!fireOnRelease) currentAction()
                val repeatJob = if (repeatable) {
                    scope.launch {
                        delay(280)
                        while (isActive) {
                            currentAction()
                            delay(110)
                        }
                    }
                } else null
                tryAwaitRelease()
                repeatJob?.cancel()
                currentPressedChange(false)
            }
        )
    }
}

/** How strongly an unused arm melts into the body: a plain dim, deepened by either rocker style. */
fun dpadFlatness(rocker: Float, verticalRocker: Float): Float = 0.55f + 0.2f * maxOf(rocker, verticalRocker)

private enum class DpadDir { Up, Down, Left, Right }

@Composable
fun DPad(
    size: Dp,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    faceLight: Color = PixelDropColors.DpadLight,
    face: Color = PixelDropColors.DpadBlack,
    faceDark: Color = PixelDropColors.DpadDark,
    arrowColor: Color = Color(0xFF4A4A4A),
    onButtonPress: (ConsoleButton) -> Unit = {},
    /** Finger lifted from UP / DOWN (or the gesture cancelled); lets a game treat the arm as a held latch. */
    onUpRelease: () -> Unit = {},
    onDownRelease: () -> Unit = {},
    /** 0..1 strength of each arm. Below 1 the arm is toned toward [dimColor] and its arrow fades;
     *  the arm keeps its hit box and still works. All 1 = today's look. */
    upEmphasis: Float = 1f,
    downEmphasis: Float = 1f,
    leftEmphasis: Float = 1f,
    rightEmphasis: Float = 1f,
    /** 0..1: horizontal-only "rocker" styling (flatter unused arms plus a centre pivot groove). */
    rocker: Float = 0f,
    /** 0..1: vertical-only "rocker" styling (flatter left/right arms plus a horizontal pivot groove). */
    verticalRocker: Float = 0f,
    /** The body plastic the unused arms melt toward. */
    dimColor: Color = Color.Unspecified,
) {
    var pressed by remember { mutableStateOf<DpadDir?>(null) }
    var lastPressedDir by remember { mutableStateOf<DpadDir?>(null) }
    LaunchedEffect(pressed) { if (pressed != null) lastPressedDir = pressed }
    // Fades in/out smoothly instead of snapping, so the dip reads as a soft
    // press rather than a flat color swap.
    val pressDepth by animateFloatAsState(
        targetValue = if (pressed != null) 1f else 0f,
        animationSpec = pressSpring,
        label = "dpadPress",
    )
    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val arm = s / 3f
            val r = CornerRadius(arm * 0.22f)
            val base = Brush.verticalGradient(
                listOf(faceLight, face, faceDark)
            )
            // cross
            drawRoundRect(base, topLeft = Offset(arm, 0f), size = Size(arm, s), cornerRadius = r)
            drawRoundRect(base, topLeft = Offset(0f, arm), size = Size(s, arm), cornerRadius = r)
            // pressed arm shading, animated toward a sunken look
            if (pressDepth > 0.001f) {
                lastPressedDir?.let { dir ->
                    val shade = Color.Black.copy(alpha = 0.45f * pressDepth)
                    val (tl, sz) = when (dir) {
                        DpadDir.Up -> Offset(arm, 0f) to Size(arm, arm)
                        DpadDir.Down -> Offset(arm, arm * 2) to Size(arm, arm)
                        DpadDir.Left -> Offset(0f, arm) to Size(arm, arm)
                        DpadDir.Right -> Offset(arm * 2, arm) to Size(arm, arm)
                    }
                    drawRoundRect(shade, topLeft = tl, size = sz, cornerRadius = r)
                    // inset top/left shadow so the arm reads as pressed inward
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.25f * pressDepth),
                        topLeft = tl,
                        size = Size(sz.width, sz.height * 0.3f),
                        cornerRadius = r,
                    )
                }
            }
            // Unused arms sink toward the body colour; a rocker also flattens them.
            if (dimColor != Color.Unspecified) {
                val flat = dpadFlatness(rocker, verticalRocker)
                if (upEmphasis < 0.999f) drawRoundRect(dimColor.copy(alpha = flat * (1f - upEmphasis)), Offset(arm, 0f), Size(arm, arm), r)
                if (downEmphasis < 0.999f) drawRoundRect(dimColor.copy(alpha = flat * (1f - downEmphasis)), Offset(arm, arm * 2), Size(arm, arm), r)
                if (leftEmphasis < 0.999f) drawRoundRect(dimColor.copy(alpha = flat * (1f - leftEmphasis)), Offset(0f, arm), Size(arm, arm), r)
                if (rightEmphasis < 0.999f) drawRoundRect(dimColor.copy(alpha = flat * (1f - rightEmphasis)), Offset(arm * 2, arm), Size(arm, arm), r)
            }
            // center dish
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(faceDark, faceLight),
                    center = center,
                    radius = arm * 0.55f,
                ),
                radius = arm * 0.38f,
                center = center,
            )
            // arrows
            val arrow = arrowColor
            fun trianglePath(cx: Float, cy: Float, half: Float, pointUp: Boolean): Path =
                Path().apply {
                    if (pointUp) {
                        moveTo(cx, cy - half); lineTo(cx - half, cy + half); lineTo(cx + half, cy + half)
                    } else {
                        moveTo(cx, cy + half); lineTo(cx - half, cy - half); lineTo(cx + half, cy - half)
                    }
                    close()
                }
            val ah = arm * 0.22f
            fun fade(e: Float) = arrow.copy(alpha = arrow.alpha * (0.3f + 0.7f * e))
            drawPath(trianglePath(s / 2f, arm * 0.42f, ah, true), fade(upEmphasis))
            drawPath(trianglePath(s / 2f, s - arm * 0.42f, ah, false), fade(downEmphasis))
            rotate(degrees = -90f, pivot = Offset(arm * 0.42f, s / 2f)) {
                drawPath(trianglePath(arm * 0.42f, s / 2f, ah, true), fade(leftEmphasis))
            }
            rotate(degrees = 90f, pivot = Offset(s - arm * 0.42f, s / 2f)) {
                drawPath(trianglePath(s - arm * 0.42f, s / 2f, ah, true), fade(rightEmphasis))
            }
            // vertical rocker: the pivot groove runs left-right across the centre
            if (verticalRocker > 0.01f) {
                drawRect(
                    Color.Black.copy(alpha = 0.35f * verticalRocker),
                    topLeft = Offset(arm * 0.9f, s / 2f - s * 0.008f),
                    size = Size(arm * 1.2f, s * 0.016f),
                )
            }
            // rocker pivot groove across the centre
            if (rocker > 0.01f) {
                drawRect(
                    Color.Black.copy(alpha = 0.35f * rocker),
                    topLeft = Offset(s / 2f - s * 0.008f, arm * 0.9f),
                    size = Size(s * 0.016f, arm * 1.2f),
                )
            }
        }
        val arm = size / 3
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(arm, arm)
                .consolePressable(onPressedChange = { pressed = if (it) DpadDir.Up else null; if (!it) onUpRelease() }, onPressFeedback = { onButtonPress(ConsoleButton.Up) }, action = onUp)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(arm, arm)
                .consolePressable(repeatable = true, onPressedChange = { pressed = if (it) DpadDir.Down else null; if (!it) onDownRelease() }, onPressFeedback = { onButtonPress(ConsoleButton.Down) }, action = onDown)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(arm, arm)
                .consolePressable(repeatable = true, onPressedChange = { pressed = if (it) DpadDir.Left else null }, onPressFeedback = { onButtonPress(ConsoleButton.Left) }, action = onLeft)
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(arm, arm)
                .consolePressable(repeatable = true, onPressedChange = { pressed = if (it) DpadDir.Right else null }, onPressFeedback = { onButtonPress(ConsoleButton.Right) }, action = onRight)
        )
    }
}

/** Glossy action button with its label placed below-right, per mocks.
 *  Face colors default to the classic purple; the shell overrides them
 *  with the active [com.jasmeet.pixeldrop.ui.theme.ConsoleTheme]'s. */
@Composable
fun RoundButton(
    label: String,
    size: Dp,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    faceLight: Color = PixelDropColors.ButtonPurpleLight,
    face: Color = PixelDropColors.ButtonPurple,
    faceDark: Color = PixelDropColors.ButtonPurpleDark,
    labelColor: Color = PixelDropColors.LabelDark,
    /** Fires when the finger lifts (or the gesture is cancelled); lets a game
     *  treat the button as hold-to-charge, e.g. RIDGE RUNNER's variable jump. */
    onRelease: () -> Unit = {},
    onPressFeedback: () -> Unit = {},
    /** 0..1 key strength. Below 1 the key loses its gloss and tones toward [dimColor];
     *  it stays fully pressable. 1 = today's look. */
    emphasis: Float = 1f,
    /** Visual scale only (e.g. 1.12 for the dominant key, 0.88 for an unused one).
     *  The touch target stays [size] whatever is drawn. */
    drawScale: Float = 1f,
    /** The body plastic an unused key melts toward. */
    dimColor: Color = Color.Unspecified,
) {
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpring,
        label = "roundButtonPress",
    )
    val flat = (1f - emphasis).coerceIn(0f, 1f)
    val dimmed = flat > 0.001f && dimColor != Color.Unspecified
    val drawFace = if (dimmed) lerp(face, dimColor, flat * 0.5f) else face
    val drawLight = if (dimmed) lerp(faceLight, drawFace, flat) else faceLight
    val drawDark = if (dimmed) lerp(faceDark, drawFace, flat * 0.6f) else faceDark
    val shownLabel = if (dimmed) labelColor.copy(alpha = labelColor.alpha * (1f - 0.35f * flat)) else labelColor
    Column(modifier, horizontalAlignment = Alignment.End) {
        // The outer box is the fixed touch target; only the inner layer is scaled and depressed.
        Box(
            Modifier
                .size(size)
                .consolePressable(
                    onPressedChange = {
                        pressed = it
                        if (!it) onRelease()
                    },
                    onPressFeedback = onPressFeedback,
                    action = onPress,
                )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Real elevation shadow shrinks as the button sinks, plus a
                    // physical push-down + squash so it reads as a 3D key, not a flat swap.
                    .shadow(elevation = ((7f - press * 6f) * (1f - 0.6f * flat)).dp, shape = CircleShape, clip = false)
                    .graphicsLayer {
                        translationY = this.size.height * 0.06f * press
                        scaleX = (1f - 0.05f * press) * drawScale
                        scaleY = (1f - 0.05f * press) * drawScale
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = this.size.minDimension / 2f
                    // recessed ring
                    drawCircle(Color.Black.copy(alpha = 0.25f), radius = r, center = center)
                    val topShift = r * 0.35f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                lerp(drawLight, drawFace, press),
                                drawFace,
                                drawDark,
                            ),
                            center = center - Offset(topShift * 0.6f, topShift),
                            radius = r * 1.35f,
                        ),
                        radius = r * 0.92f,
                        center = center,
                    )
                    // specular highlight fades as the surface flattens under press
                    drawCircle(
                        color = Color.White.copy(alpha = (0.22f - 0.12f * press) * (1f - flat)),
                        radius = r * 0.28f,
                        center = center - Offset(r * 0.25f, r * 0.4f),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        // Dot-matrix label to match the 90s pixel branding.
        PixelText(
            text = label,
            pixel = size * 0.026f,
            color = shownLabel,
        )
    }
}

/** SELECT / START pill. [onHeldChange] reports raw pressed state upward
 *  so the app can detect the SELECT+START power-off hold combo. */
@Composable
fun PillButton(
    label: String,
    width: Dp,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    onHeldChange: (Boolean) -> Unit = {},
    labelColor: Color = PixelDropColors.LabelDark,
    onPressFeedback: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = pressSpring,
        label = "pillButtonPress",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width, width * 0.34f)
                .testTag("pill-$label")
                .shadow(elevation = (5f - press * 4.5f).dp, shape = RoundedCornerShape(50), clip = false)
                .graphicsLayer {
                    translationY = this.size.height * 0.14f * press
                    scaleY = 1f - 0.08f * press
                }
                .consolePressable(
                    fireOnRelease = true,
                    onPressedChange = {
                        pressed = it
                        onHeldChange(it)
                    },
                    onPressFeedback = onPressFeedback,
                    action = onPress,
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = this.size.height
                val r = CornerRadius(h / 2f)
                // resting shadow gap closes as the pill sinks flush into its socket
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.30f),
                    topLeft = Offset(0f, h * 0.08f * (1f - press)),
                    size = this.size,
                    cornerRadius = r,
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            lerp(PixelDropColors.PillGrayLight, PixelDropColors.PillGray, press),
                            PixelDropColors.PillGray,
                            PixelDropColors.PillGrayDark,
                        )
                    ),
                    size = Size(this.size.width, h * (0.94f - 0.06f * press)),
                    cornerRadius = r,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = PixelDropType.ConsoleLabel,
            fontSize = 8.sp,
            color = labelColor,
            textAlign = TextAlign.Center,
        )
    }
}

/** Diagonal speaker grill slots, bottom-right of the console. */
@Composable
fun SpeakerGrill(
    width: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(width, width * 0.62f)) {
        val slotCount = 6
        val w = size.width
        val h = size.height
        val slotW = w * 0.055f
        rotate(degrees = -30f, pivot = Offset(w / 2f, h / 2f)) {
            for (i in 0 until slotCount) {
                val x = w * 0.12f + i * (w * 0.15f)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    topLeft = Offset(x, h * 0.12f),
                    size = Size(slotW, h * 0.76f),
                    cornerRadius = CornerRadius(slotW / 2f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(x + slotW * 0.4f, h * 0.12f + h * 0.76f),
                    size = Size(slotW, h * 0.03f),
                    cornerRadius = CornerRadius(slotW / 2f),
                )
            }
        }
    }
}

/** Small cross-head screw detail. */
@Composable
fun Screw(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(PixelDropColors.ScrewGray, Color(0xFF564F41)),
                center = center - Offset(r * 0.25f, r * 0.25f),
                radius = r * 1.4f,
            ),
            radius = r,
            center = center,
        )
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.18f))
        val slot = Color.Black.copy(alpha = 0.5f)
        val len = r * 0.65f
        val thick = r * 0.16f
        rotate(degrees = 30f, pivot = center) {
            drawRect(slot, topLeft = center - Offset(len, thick / 2f), size = Size(len * 2f, thick))
            drawRect(slot, topLeft = center - Offset(thick / 2f, len), size = Size(thick, len * 2f))
        }
    }
}

/** Battery LED with soft glow; [brightness] 0..1 for boot fade-in. */
@Composable
fun BatteryLed(size: Dp, brightness: Float, modifier: Modifier = Modifier) {
    val b = brightness.coerceIn(0f, 1f)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        if (b > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(PixelDropColors.LedRedGlow.copy(alpha = 0.55f * b), Color.Transparent),
                    center = center,
                    radius = r * 1.9f,
                ),
                radius = r * 1.9f,
                center = center,
            )
        }
        drawCircle(
            color = androidx.compose.ui.graphics.lerp(PixelDropColors.LedOff, PixelDropColors.LedRed, b),
            radius = r * 0.55f,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.35f * b),
            radius = r * 0.18f,
            center = center - Offset(r * 0.12f, r * 0.15f),
        )
    }
}
