package com.jasmeet.pixeldrop.ui.settings

import com.jasmeet.pixeldrop.game.GAMES
import com.jasmeet.pixeldrop.game.GameId

/**
 * Gate for the one-time interactive GAME THEME intro on the games menu: offered
 * until seen, and never to someone who already turned the feature on.
 */
fun shouldShowGameThemeHint(introDone: Boolean, gameThemeEnabled: Boolean, onMenuWithNoOverlay: Boolean): Boolean =
    !introDone && !gameThemeEnabled && onMenuWithNoOverlay

const val GAME_THEME_HINT_TEXT = "TRY GAME THEME: SETTINGS"

/** How long the hint stays if no button dismisses it first. */
const val GAME_THEME_HINT_MS = 6_000L

/** Pause between arriving on the menu and the intro opening, so the menu lands first. */
const val GAME_THEME_INTRO_DELAY_MS = 400L

/** The intro previews the next game's paint this often until the player takes over. */
const val GAME_THEME_INTRO_CYCLE_MS = 1_500L

/** How long the "GAME THEME ON" confirmation stays before it closes by itself. */
const val GAME_THEME_INTRO_CONFIRM_MS = 2_200L

/** Games the intro can preview: teaser slots have no paint of their own. */
val GAME_THEME_INTRO_GAMES: List<GameId> = GAMES.filter { !it.comingSoon }.map { it.id }

/** Wrap-around step through the preview list; [delta] may be negative. */
fun nextIntroIndex(index: Int, delta: Int, count: Int): Int =
    if (count <= 0) 0 else ((index + delta) % count + count) % count

/** What the intro is showing: the offer (previewing paints) or the confirmation. */
enum class GameThemeIntroStep { Offer, Confirmed }
