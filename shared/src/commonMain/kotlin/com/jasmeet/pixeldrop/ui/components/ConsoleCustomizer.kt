package com.jasmeet.pixeldrop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasmeet.pixeldrop.ui.theme.CONSOLE_THEMES
import com.jasmeet.pixeldrop.ui.theme.ConsoleTheme
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import com.jasmeet.pixeldrop.ui.theme.rememberReducedMotion

private val LabInk = Color(0xFFFFEBD1)
private val LabMuted = Color(0xFFD3A9CE)
private val LabPanel = Color(0xFF171522)
private val LabPanelRaised = Color(0xFF292033)

/** Pixel-gear entry point that sits in the backdrop, outside the handheld. */
@Composable
fun ConsoleCustomizerButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "customizerGearRotation",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "customizerGearPress",
    )

    Box(
        modifier = modifier
            .size(52.dp)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(4.dp))
            .background(LabPanel.copy(alpha = 0.94f))
            .border(2.dp, PixelDropColors.SynthGrid, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Open settings" },
        contentAlignment = Alignment.Center,
    ) {
        PixelGear(Modifier.size(28.dp))
    }
}

/** Retro settings deck opened by the gear on the main menu: paint shop and sound. */
@Composable
fun RetroSettingsMenu(
    visible: Boolean,
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    gameThemeEnabled: Boolean,
    /** Shows the pulsing NEW marker until GAME THEME has been toggled once. */
    showGameThemeNew: Boolean,
    onCustomize: () -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onGameThemeChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(160)) + scaleIn(
            initialScale = 0.88f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 390f),
        ),
        exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.95f, animationSpec = tween(130)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 92.dp)
                    .widthIn(max = 380.dp)
                    .heightIn(max = 650.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(LabPanel)
                    .border(2.dp, PixelDropColors.SynthGrid, RoundedCornerShape(5.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "SETTINGS",
                            color = LabInk,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = "SYSTEM DECK // 04",
                            color = PixelDropColors.SynthGrid,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                    Text(
                        text = "[ X ]",
                        color = LabInk,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .padding(10.dp)
                            .semantics { contentDescription = "Close settings" },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SettingCard(
                    title = "CUSTOMIZE CONSOLE",
                    subtitle = "PAINT / BUTTONS / SHELL",
                    onClick = onCustomize,
                ) {
                    Text(
                        text = "[ > ]",
                        color = PixelDropColors.SynthSun,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ToggleSettingCard(
                    name = "Sound",
                    title = "SOUND",
                    onSubtitle = "SFX + MUSIC ONLINE",
                    offSubtitle = "AUDIO MUTED",
                    enabled = soundEnabled,
                    onChanged = onSoundChanged,
                )
                Spacer(Modifier.height(12.dp))
                ToggleSettingCard(
                    name = "Haptics",
                    title = "HAPTICS",
                    onSubtitle = "KEY-TAP PULSES",
                    offSubtitle = "VIBRATION OFF",
                    enabled = hapticsEnabled,
                    onChanged = onHapticsChanged,
                )
                Spacer(Modifier.height(12.dp))
                ToggleSettingCard(
                    name = if (showGameThemeNew) "Game theme, new" else "Game theme",
                    title = "GAME THEME",
                    onSubtitle = "SHELL PAINT ADAPTS PER GAME",
                    offSubtitle = "SHELL STAYS YOUR PAINT",
                    enabled = gameThemeEnabled,
                    onChanged = onGameThemeChanged,
                    badge = if (showGameThemeNew) ({ NewMarker() }) else null,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "SETTINGS ARE SAVED AUTOMATICALLY",
                    color = LabMuted.copy(alpha = 0.78f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.975f else 1f,
        spring(stiffness = 700f),
        label = "settingPress",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(3.dp))
            .background(LabPanelRaised)
            .border(1.dp, LabMuted.copy(alpha = 0.42f), RoundedCornerShape(3.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = title,
                color = LabInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = LabMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
        trailing()
    }
}

@Composable
private fun ToggleSettingCard(
    name: String,
    title: String,
    onSubtitle: String,
    offSubtitle: String,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(LabPanelRaised)
            .border(
                1.dp,
                if (enabled) PixelDropColors.SynthGrid else LabMuted.copy(alpha = 0.42f),
                RoundedCornerShape(3.dp),
            )
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onChanged,
            )
            .semantics {
                contentDescription = name
                stateDescription = if (enabled) "On" else "Off"
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = LabInk,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    badge()
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (enabled) onSubtitle else offSubtitle,
                color = if (enabled) PixelDropColors.SynthGrid else LabMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
        RetroSoundSwitch(enabled)
    }
}

/** Small pixel-font "NEW" tag that breathes until the setting has been used once.
 *  Static under reduced motion. Decorative: the card's semantics already say "new". */

/** Small pixel-font "NEW" tag that breathes until the setting has been used once.
 *  Static under reduced motion. Decorative: the card's semantics already say "new". */
@Composable
private fun NewMarker() {
    val reduced = rememberReducedMotion()
    val alpha = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "newMarkerPulse")
        val pulse by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
            label = "newMarkerAlpha",
        )
        pulse
    }
    val cells = pixelTextCellWidth("NEW")
    val px = 1.6.dp
    Canvas(Modifier.size(px * (cells + 4), px * (GLYPH_H + 4))) {
        val pixel = px.toPx()
        val color = PixelDropColors.SynthSun.copy(alpha = alpha)
        drawRect(color, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(pixel * 0.7f))
        drawPixelText("NEW", androidx.compose.ui.geometry.Offset(pixel * 2f, pixel * 2f), pixel, color)
    }
}

@Composable
private fun RetroSoundSwitch(enabled: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(220),
        label = "retroSoundSwitch",
    )
    Canvas(Modifier.size(74.dp, 34.dp)) {
        val border = if (enabled) PixelDropColors.SynthGrid else LabMuted
        val pixel = size.height * 0.12f
        drawRect(border, size = size)
        drawRect(LabPanel, topLeft = androidx.compose.ui.geometry.Offset(pixel, pixel), size = androidx.compose.ui.geometry.Size(size.width - pixel * 2f, size.height - pixel * 2f))
        val knob = size.height - pixel * 4f
        val left = pixel * 2f + (size.width - knob - pixel * 4f) * progress
        drawRect(
            color = if (enabled) PixelDropColors.SynthSun else LabMuted,
            topLeft = androidx.compose.ui.geometry.Offset(left, pixel * 2f),
            size = androidx.compose.ui.geometry.Size(knob, knob),
        )
        val textX = if (enabled) pixel * 2f else size.width - pixel * 7f
        val bars = if (enabled) 3 else 1
        for (i in 0 until bars) {
            drawRect(
                color = border,
                topLeft = androidx.compose.ui.geometry.Offset(textX + i * pixel * 1.5f, size.height * (0.62f - i * 0.08f)),
                size = androidx.compose.ui.geometry.Size(pixel, size.height * (0.12f + i * 0.08f)),
            )
        }
    }
}


/** A modal paint deck. It is deliberately part of the room, not the LCD UI. */
@Composable
fun ConsoleCustomizer(
    visible: Boolean,
    selectedThemeId: String,
    onThemeSelected: (ConsoleTheme) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** GAME THEME is on: each game repaints the shell, and the pick below is the menu/base paint. */
    gameThemeEnabled: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(180)) + scaleIn(
            initialScale = 0.86f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 360f),
        ),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .semantics { contentDescription = "Close console customization" },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 82.dp)
                    .widthIn(max = 380.dp)
                    .heightIn(max = 650.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LabPanel)
                    .border(2.dp, PixelDropColors.SynthGrid, RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "SHELL LAB",
                            color = LabInk,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = "COLOR DECK // 06",
                            color = PixelDropColors.SynthGrid,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                    Text(
                        text = "[ X ]",
                        color = LabInk,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .padding(8.dp)
                            .semantics { contentDescription = "Close" },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Pick a factory paint job. Your console updates live and remembers it.",
                    color = LabMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (gameThemeEnabled) {
                    Spacer(Modifier.height(12.dp))
                    GameThemeOverrideNotice()
                }
                Spacer(Modifier.height(18.dp))

                CONSOLE_THEMES.chunked(2).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowThemes.forEach { theme ->
                            ThemeCard(
                                theme = theme,
                                isSelected = selectedThemeId == theme.id,
                                onClick = { onThemeSelected(theme) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    text = "A/B BUTTONS ARE COLOR-MATCHED",
                    color = LabMuted.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: ConsoleTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val borderColor by animateColorAsState(
        if (isSelected) PixelDropColors.SynthSun else LabMuted.copy(alpha = 0.32f),
        tween(220),
        label = "themeCardBorder",
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 3f else 0f,
        animationSpec = spring(stiffness = 650f),
        label = "themeCardPress",
    )

    Column(
        modifier = modifier
            .height(94.dp)
            .graphicsLayer { translationY = lift }
            .clip(RoundedCornerShape(3.dp))
            .background(if (isSelected) LabPanelRaised else Color(0xFF201A29))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(3.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { selected = isSelected }
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ConsolePaintSwatch(theme, Modifier.fillMaxWidth().height(44.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = theme.label,
                color = LabInk,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
            )
            if (isSelected) {
                Text(
                    text = "ON",
                    color = PixelDropColors.SynthSun,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun ConsolePaintSwatch(theme: ConsoleTheme, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val shellWidth = size.width * 0.72f
        val shellLeft = (size.width - shellWidth) / 2f
        drawRoundRect(
            color = theme.bodyDark,
            topLeft = androidx.compose.ui.geometry.Offset(shellLeft, 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(shellWidth, size.height - 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
        )
        drawRoundRect(
            color = theme.bodyLight,
            topLeft = androidx.compose.ui.geometry.Offset(shellLeft + 2.dp.toPx(), 4.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(shellWidth - 4.dp.toPx(), size.height * 0.42f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        )
        drawRect(
            color = Color(0xFF343A35),
            topLeft = androidx.compose.ui.geometry.Offset(shellLeft + shellWidth * 0.16f, size.height * 0.23f),
            size = androidx.compose.ui.geometry.Size(shellWidth * 0.50f, size.height * 0.35f),
        )
        drawCircle(
            color = theme.button,
            radius = size.height * 0.12f,
            center = androidx.compose.ui.geometry.Offset(shellLeft + shellWidth * 0.78f, size.height * 0.69f),
        )
        drawCircle(
            color = theme.buttonLight,
            radius = size.height * 0.06f,
            center = androidx.compose.ui.geometry.Offset(shellLeft + shellWidth * 0.75f, size.height * 0.65f),
        )
    }
}

@Composable
private fun PixelGear(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cyan = PixelDropColors.SynthGrid
        val cell = size.minDimension / 9f
        val rows = arrayOf(
            "001010100",
            "011111110",
            "111101111",
            "011000110",
            "111000111",
            "011000110",
            "111101111",
            "011111110",
            "001010100",
        )
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, pixel ->
                if (pixel == '1') {
                    drawRect(
                        color = cyan,
                        topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
            }
        }
    }
}

@Composable
private fun GameThemeOverrideNotice() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(LabPanelRaised)
            .border(1.dp, PixelDropColors.SynthSun, RoundedCornerShape(3.dp))
            .semantics { contentDescription = "Game theme is on. Games repaint the console while you play. Your pick is used on the menu." }
            .padding(10.dp),
    ) {
        Text(
            text = "GAME THEME IS ON",
            color = PixelDropColors.SynthSun,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "EACH GAME REPAINTS THE SHELL WHILE YOU PLAY. YOUR PICK (BASE) SHOWS ON THE MENU.",
            color = LabMuted,
            fontSize = 9.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

