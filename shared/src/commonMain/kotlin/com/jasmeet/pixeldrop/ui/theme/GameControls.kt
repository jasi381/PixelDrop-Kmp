package com.jasmeet.pixeldrop.ui.theme

import androidx.compose.runtime.Immutable
import com.jasmeet.pixeldrop.game.GameId

/** Bit flags for [DpadLegendRow.mask]. */
const val DPAD_UP = 1
const val DPAD_DOWN = 2
const val DPAD_LEFT = 4
const val DPAD_RIGHT = 8

const val LEGEND_ICON_CELLS = 7
const val LEGEND_GAP_CELLS = 2

/** Longest caption the pixel font shelf has room for. */
const val MAX_CAPTION_LENGTH = 6

/** Growth of the emphasized A key and size of an unused round key (never below the hit area, see RoundButton). */
const val A_EMPHASIS_SCALE = 1.12f
const val UNUSED_KEY_SCALE = 0.88f

/** One line of the D-pad caption legend: the arms it covers and their shared caption. */
class DpadLegendRow(val mask: Int, val caption: String) {
    /** Legend cells: 7-cell arm icon, 2-cell gap, caption text (6 cells per char, no trailing gap). */
    val cells: Int get() = LEGEND_ICON_CELLS + LEGEND_GAP_CELLS + (caption.length * 6 - 1)
}

/**
 * Which physical controls a game uses while a round is running, each with a
 * short pixel-font caption; `null` means the control has no job during play.
 * Derived from the input handlers in `PixelDropApp.kt`; pause-menu navigation
 * and game-over confirmations deliberately do not count.
 *
 * START is always used (pause), so it has no nullable slot.
 */
@Immutable
class GameControls(
    val up: String? = null,
    val down: String? = null,
    val left: String? = null,
    val right: String? = null,
    val a: String? = null,
    val b: String? = null,
    val start: String = "PAUSE",
) {
    val usesUp: Boolean get() = up != null
    val usesDown: Boolean get() = down != null
    val usesLeft: Boolean get() = left != null
    val usesRight: Boolean get() = right != null
    val usesA: Boolean get() = a != null
    val usesB: Boolean get() = b != null

    /** Horizontal-only D-pad games (paddle, ship, car): drawn as a rocker. */
    val isRocker: Boolean get() = usesLeft && usesRight && !usesUp && !usesDown

    /** Vertical-only D-pad games (RALLY DUEL's paddle): the left/right arms flatten into a vertical rocker. */
    val isVerticalRocker: Boolean get() = usesUp && usesDown && !usesLeft && !usesRight

    /** Draw scale of the A key: it grows when it is the only action key in use. */
    val aScale: Float get() = if (!usesA) UNUSED_KEY_SCALE else if (!usesB) A_EMPHASIS_SCALE else 1f
    val bScale: Float get() = if (usesB) 1f else UNUSED_KEY_SCALE

    /** Number of used controls, START included. */
    val usedCount: Int
        get() = listOf(up, down, left, right, a, b).count { it != null } + 1

    /** D-pad captions grouped by identical text, so "MOVE" on four arms is one row. */
    val dpadLegend: List<DpadLegendRow> = buildLegend(up, down, left, right)

    /** Widest legend row in font cells (0 when the D-pad has no captions). */
    val legendMaxCells: Int = dpadLegend.maxOfOrNull { it.cells } ?: 0

    /** Every caption that gets printed, for validation. */
    val allCaptions: List<String> = listOfNotNull(up, down, left, right, a, b, start)
}

private fun buildLegend(up: String?, down: String?, left: String?, right: String?): List<DpadLegendRow> {
    val bits = intArrayOf(DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT)
    val captions = arrayOf(up, down, left, right)
    val rows = ArrayList<DpadLegendRow>(4)
    val seen = HashSet<String>()
    for (i in captions.indices) {
        val caption = captions[i] ?: continue
        if (!seen.add(caption)) continue
        var mask = 0
        for (j in captions.indices) if (captions[j] == caption) mask = mask or bits[j]
        rows.add(DpadLegendRow(mask, caption))
    }
    return rows
}

/**
 * Exhaustive per-game control map. Adding a [GameId] fails the build until it
 * declares its controls. Captions are at most [MAX_CAPTION_LENGTH] glyphs of the
 * dot-matrix font.
 */
fun gameControls(id: GameId): GameControls = when (id) {
    // LEFT/RIGHT move, DOWN soft-drops, A/B rotate; UP only navigates the pause menu.
    GameId.PixelDrop -> GameControls(down = "DROP", left = "MOVE", right = "MOVE", a = "SPIN R", b = "SPIN L")
    // The D-pad steers in all four directions; A and B do nothing while running.
    GameId.PixelTrail -> GameControls(up = "TURN", down = "TURN", left = "TURN", right = "TURN")
    // Paddle LEFT/RIGHT, A launches the ball.
    GameId.BrickPulse -> GameControls(left = "MOVE", right = "MOVE", a = "LAUNCH")
    // Ship LEFT/RIGHT, A shoots, B dashes.
    GameId.OrbitBlaster -> GameControls(left = "MOVE", right = "MOVE", a = "SHOOT", b = "DASH")
    // Free four-way movement, A dashes.
    GameId.MeteorDodge -> GameControls(up = "MOVE", down = "MOVE", left = "MOVE", right = "MOVE", a = "DASH")
    // Steering is the whole game: LEFT/RIGHT only, A stays free.
    GameId.PixelRacer -> GameControls(left = "STEER", right = "STEER")
    // Teaser slots never play.
    GameId.Soon1, GameId.Soon2, GameId.Soon3, GameId.Soon4 -> GameControls()
}

/** All control maps, indexed by [GameId.ordinal], so draw code never rebuilds them. */
val GAME_CONTROLS: List<GameControls> = GameId.entries.map(::gameControls)

/** How long the teaching captions stay on screen while a round is running. */
const val CONTROL_CAPTION_MS = 5_000L

/** Captions are taught only with GAME THEME on, in a real game, and only until the first run has been seen. */
fun shouldTeachCaptions(gameThemeEnabled: Boolean, game: GameId?, alreadySeen: Boolean): Boolean =
    gameThemeEnabled && game != null && !alreadySeen

/** Counts the caption clock down by [deltaMs] only while the round is running; pause and menus freeze it. */
fun advanceCaptionClock(remainingMs: Long, deltaMs: Long, roundRunning: Boolean): Long =
    if (roundRunning && deltaMs > 0) maxOf(0L, remainingMs - deltaMs) else remainingMs

/**
 * Everything the shell needs to adapt to the game in play. The default value
 * reproduces the plain shell exactly, so menu/boot/off/GAME THEME off pass it
 * unchanged.
 */
@Immutable
data class GameShellStyle(
    /** Null = no adaptation (all controls full strength). */
    val controls: GameControls? = null,
    /** Null = no decals and no badge. */
    val livery: Livery? = null,
    /** True while the one-time teaching captions are on screen. */
    val captionsVisible: Boolean = false,
) {
    val upUsed: Boolean get() = controls?.usesUp ?: true
    val downUsed: Boolean get() = controls?.usesDown ?: true
    val leftUsed: Boolean get() = controls?.usesLeft ?: true
    val rightUsed: Boolean get() = controls?.usesRight ?: true
    val aUsed: Boolean get() = controls?.usesA ?: true
    val bUsed: Boolean get() = controls?.usesB ?: true
    val rocker: Boolean get() = controls?.isRocker ?: false
    val verticalRocker: Boolean get() = controls?.isVerticalRocker ?: false
    val aScale: Float get() = controls?.aScale ?: 1f
    val bScale: Float get() = controls?.bScale ?: 1f
    val badge: String? get() = livery?.badge

    companion object {
        val Default = GameShellStyle()
    }
}

/** The style the shell should wear: adapted only while GAME THEME is on and a game is in play. */
fun gameShellStyleFor(playingGame: GameId?, gameThemeEnabled: Boolean, captionsVisible: Boolean): GameShellStyle =
    if (gameThemeEnabled && playingGame != null) {
        GameShellStyle(
            controls = GAME_CONTROLS[playingGame.ordinal],
            livery = gameLivery(playingGame),
            captionsVisible = captionsVisible,
        )
    } else {
        GameShellStyle.Default
    }
