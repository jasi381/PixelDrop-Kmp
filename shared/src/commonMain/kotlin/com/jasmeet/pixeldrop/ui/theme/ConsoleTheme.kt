package com.jasmeet.pixeldrop.ui.theme

import androidx.compose.ui.graphics.Color

/** A paint job for the console shell: body plastic plus A/B button colors.
 *  D-pad, pills and labels stay constant across themes, like real hardware
 *  color editions. */
data class ConsoleTheme(
    val id: String,
    val label: String,
    val bodyLight: Color,
    val body: Color,
    val bodyDark: Color,
    val edge: Color,
    val buttonLight: Color,
    val button: Color,
    val buttonDark: Color,
)

/** First entry is the factory ivory look and the fallback for unknown ids. */
val CONSOLE_THEMES: List<ConsoleTheme> = listOf(
    ConsoleTheme(
        id = "ivory", label = "CLASSIC",
        bodyLight = PixelDropColors.ConsoleBodyLight,
        body = PixelDropColors.ConsoleBody,
        bodyDark = PixelDropColors.ConsoleBodyDark,
        edge = PixelDropColors.ConsoleEdge,
        buttonLight = PixelDropColors.ButtonPurpleLight,
        button = PixelDropColors.ButtonPurple,
        buttonDark = PixelDropColors.ButtonPurpleDark,
    ),
    ConsoleTheme(
        id = "midnight", label = "MIDNIGHT",
        bodyLight = Color(0xFF4C4C56),
        body = Color(0xFF3A3A44),
        bodyDark = Color(0xFF27272E),
        edge = Color(0xFF1B1B21),
        buttonLight = Color(0xFF9D6BF0),
        button = Color(0xFF7C4DDE),
        buttonDark = Color(0xFF45278C),
    ),
    ConsoleTheme(
        id = "vapor", label = "VAPOR",
        bodyLight = Color(0xFFF8CBE2),
        body = Color(0xFFEFA9CF),
        bodyDark = Color(0xFFD983B4),
        edge = Color(0xFFB46C96),
        buttonLight = Color(0xFF57DED6),
        button = Color(0xFF2BB8B0),
        buttonDark = Color(0xFF166F6A),
    ),
    ConsoleTheme(
        id = "matcha", label = "MATCHA",
        bodyLight = Color(0xFFCDD8A8),
        body = Color(0xFFB8C58D),
        bodyDark = Color(0xFF9BAA71),
        edge = Color(0xFF7C8A55),
        buttonLight = Color(0xFFE0656B),
        button = Color(0xFFC74850),
        buttonDark = Color(0xFF7E2A30),
    ),
    ConsoleTheme(
        id = "crimson", label = "CRIMSON",
        bodyLight = Color(0xFFE06A70),
        body = Color(0xFFC94A52),
        bodyDark = Color(0xFFA03740),
        edge = Color(0xFF77252C),
        buttonLight = Color(0xFFF2E9D4),
        button = Color(0xFFDECFAE),
        buttonDark = Color(0xFF9E8C64),
    ),
    ConsoleTheme(
        id = "glacier", label = "GLACIER",
        bodyLight = Color(0xFFC4E6F3),
        body = Color(0xFFA2D3E8),
        bodyDark = Color(0xFF7BB2CB),
        edge = Color(0xFF5B8CA3),
        buttonLight = Color(0xFFFFB35C),
        button = Color(0xFFF28C33),
        buttonDark = Color(0xFFA85818),
    ),
)

fun consoleThemeFor(id: String?): ConsoleTheme =
    CONSOLE_THEMES.firstOrNull { it.id == id } ?: CONSOLE_THEMES.first()
