package com.jasmeet.pixeldrop.ui.theme

import androidx.compose.ui.graphics.Color
import com.jasmeet.pixeldrop.game.GameId

/**
 * Per-game console paint for the opt-in GAME THEME setting. Each palette is a
 * regular [ConsoleTheme], so label/D-pad ink still comes from
 * [highestContrastInk] and every shell surface keeps its contrast guarantee.
 *
 * The `when` is exhaustive on purpose: adding a [GameId] fails the build until
 * it gets a palette.
 */
fun gameConsoleTheme(id: GameId): ConsoleTheme = when (id) {
    // Classic ivory shell with the purple A/B keys: the factory look.
    GameId.PixelDrop -> ConsoleTheme(
        id = "game_pixeldrop", label = "PIXEL DROP",
        bodyLight = PixelDropColors.ConsoleBodyLight,
        body = PixelDropColors.ConsoleBody,
        bodyDark = PixelDropColors.ConsoleBodyDark,
        edge = PixelDropColors.ConsoleEdge,
        buttonLight = PixelDropColors.ButtonPurpleLight,
        button = PixelDropColors.ButtonPurple,
        buttonDark = PixelDropColors.ButtonPurpleDark,
    )
    // Moss green shell, clay-amber keys.
    GameId.PixelTrail -> ConsoleTheme(
        id = "game_pixeltrail", label = "PIXEL TRAIL",
        bodyLight = Color(0xFF8AA766),
        body = Color(0xFF6E8B4E),
        bodyDark = Color(0xFF56703A),
        edge = Color(0xFF3B4F27),
        buttonLight = Color(0xFFF0C070),
        button = Color(0xFFE0A548),
        buttonDark = Color(0xFF9A6A1E),
    )
    // Arcade orange shell, cabinet-blue keys.
    GameId.BrickPulse -> ConsoleTheme(
        id = "game_brickpulse", label = "BRICK PULSE",
        bodyLight = Color(0xFFFFA84D),
        body = Color(0xFFF08A24),
        bodyDark = Color(0xFFC66A10),
        edge = Color(0xFF8C4708),
        buttonLight = Color(0xFF5B95F0),
        button = Color(0xFF2F6FD6),
        buttonDark = Color(0xFF163F8A),
    )
    // Deep space navy shell, electric cyan keys.
    GameId.OrbitBlaster -> ConsoleTheme(
        id = "game_orbitblaster", label = "ORBIT BLASTER",
        bodyLight = Color(0xFF2C3E78),
        body = Color(0xFF1B2A55),
        bodyDark = Color(0xFF111B3A),
        edge = Color(0xFF0A1128),
        buttonLight = Color(0xFF6FD8F7),
        button = Color(0xFF2BB6E6),
        buttonDark = Color(0xFF13688C),
    )
    // Charcoal shell, ember keys.
    GameId.MeteorDodge -> ConsoleTheme(
        id = "game_meteordodge", label = "METEOR DODGE",
        bodyLight = Color(0xFF4E4946),
        body = Color(0xFF3A3634),
        bodyDark = Color(0xFF26231F),
        edge = Color(0xFF161412),
        buttonLight = Color(0xFFFF8047),
        button = Color(0xFFE8541E),
        buttonDark = Color(0xFF9C2F0A),
    )
    // Sunset red shell, graphite keys.
    GameId.PixelRacer -> ConsoleTheme(
        id = "game_pixelracer", label = "PIXEL RACER",
        bodyLight = Color(0xFFEE5A55),
        body = Color(0xFFD1373A),
        bodyDark = Color(0xFFA02528),
        edge = Color(0xFF6A1518),
        buttonLight = Color(0xFF6A7079),
        button = Color(0xFF3F444B),
        buttonDark = Color(0xFF20232A),
    )
    // Teaser slots never play; they wear the factory paint if ever asked.
    GameId.Soon1, GameId.Soon2, GameId.Soon3, GameId.Soon4 -> gameConsoleTheme(GameId.PixelDrop)
}

/** Game paints in menu order. */
val GAME_CONSOLE_THEMES: List<ConsoleTheme> = GameId.entries.map(::gameConsoleTheme)

/**
 * The paint the shell should wear. GAME THEME off, or no game in play, always
 * yields the user's own [base] theme, so the customizer pick stays the
 * fallback everywhere outside a round.
 */
fun resolveConsoleTheme(base: ConsoleTheme, playingGame: GameId?, gameThemeEnabled: Boolean): ConsoleTheme =
    if (gameThemeEnabled && playingGame != null) gameConsoleTheme(playingGame) else base

/** True when GAME THEME is currently overriding the user's paint. */
fun isGameThemeOverriding(playingGame: GameId?, gameThemeEnabled: Boolean): Boolean =
    gameThemeEnabled && playingGame != null
