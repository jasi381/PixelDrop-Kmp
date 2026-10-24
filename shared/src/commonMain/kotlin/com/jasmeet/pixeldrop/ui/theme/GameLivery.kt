package com.jasmeet.pixeldrop.ui.theme

import androidx.compose.runtime.Immutable
import com.jasmeet.pixeldrop.game.GameId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Decal band grid: pixel-art cells, 64 wide by 10 tall. */
const val LIVERY_COLS = 64
const val LIVERY_ROWS = 10

/**
 * Ink weight of a decal cell. The shell maps each tone to a colour derived from
 * the game's [ConsoleTheme] (label ink or key colours at partial alpha), so the
 * decals stay tone-on-tone with the plastic they are printed on.
 */
enum class LiveryTone { Faint, Soft, Accent, AccentDark }

/**
 * Silk-screen decals for one game, as integer pixel cells inside a
 * [LIVERY_COLS] x [LIVERY_ROWS] grid (packed x, y, w, h, tone ordinal). The grid
 * maps onto the decal band of the shell body (see `ShellGeometry`), which is
 * kept clear of the LCD, the D-pad, the keys and the caption shelves, so any
 * cell inside the grid is by construction not under a control. Normalised
 * coordinates are `x / LIVERY_COLS` and `y / LIVERY_ROWS`.
 *
 * Arrays instead of lists so the draw loop is index-based and allocation-free.
 */
@Immutable
class Livery(val id: GameId, val badge: String, private val packed: IntArray) {
    val count: Int get() = packed.size / 5
    fun x(i: Int): Int = packed[i * 5]
    fun y(i: Int): Int = packed[i * 5 + 1]
    fun w(i: Int): Int = packed[i * 5 + 2]
    fun h(i: Int): Int = packed[i * 5 + 3]
    fun tone(i: Int): LiveryTone = TONES[packed[i * 5 + 4]]

    private companion object {
        val TONES = LiveryTone.entries.toTypedArray()
    }
}

private class LiveryBuilder {
    private val data = ArrayList<Int>(128)

    fun rect(x: Int, y: Int, w: Int, h: Int, tone: LiveryTone) {
        data.add(x); data.add(y); data.add(w); data.add(h); data.add(tone.ordinal)
    }

    fun dot(x: Int, y: Int, tone: LiveryTone) = rect(x, y, 1, 1, tone)

    fun build(id: GameId): Livery = Livery(id, gameConsoleTheme(id).label, data.toIntArray())
}

private inline fun livery(id: GameId, block: LiveryBuilder.() -> Unit): Livery =
    LiveryBuilder().apply(block).build(id)

/** Triangle wave in 0..amp with the given period, for ridge silhouettes. */
private fun tri(x: Int, period: Int, amp: Int): Int {
    val p = ((x % period) + period) % period
    val half = period / 2
    val d = if (p < half) p else period - p
    return d * amp / half
}

/** Fills columns of a silhouette, merging runs of equal height into wide rects. */
private fun LiveryBuilder.skyline(tone: LiveryTone, height: (Int) -> Int) {
    var start = 0
    var current = height(0)
    for (col in 1..LIVERY_COLS) {
        val h = if (col < LIVERY_COLS) height(col) else -1
        if (h != current) {
            if (current > 0) rect(start, LIVERY_ROWS - current, col - start, current, tone)
            start = col
            current = h
        }
    }
}

private fun buildLivery(id: GameId): Livery = when (id) {
    // Tetromino cluster settled on a gapped stack, one piece still falling.
    GameId.PixelDrop -> livery(id) {
        for (i in 0 until 32) if (i % 9 != 4) rect(i * 2, 8, 2, 2, LiveryTone.Faint)
        // T
        for (x in intArrayOf(8, 10, 12)) rect(x, 6, 2, 2, LiveryTone.Soft)
        rect(10, 4, 2, 2, LiveryTone.Soft)
        // L
        for (x in intArrayOf(30, 32, 34)) rect(x, 6, 2, 2, LiveryTone.Accent)
        rect(34, 4, 2, 2, LiveryTone.Accent)
        // S
        rect(48, 6, 2, 2, LiveryTone.Soft); rect(50, 6, 2, 2, LiveryTone.Soft)
        rect(50, 4, 2, 2, LiveryTone.Soft); rect(52, 4, 2, 2, LiveryTone.Soft)
        // falling I
        rect(58, 0, 2, 2, LiveryTone.AccentDark); rect(58, 2, 2, 2, LiveryTone.AccentDark)
    }
    // A little snake trail winding towards a food pixel.
    GameId.PixelTrail -> livery(id) {
        rect(2, 6, 14, 2, LiveryTone.Soft)
        rect(14, 2, 2, 6, LiveryTone.Soft)
        rect(14, 2, 18, 2, LiveryTone.Soft)
        rect(30, 2, 2, 6, LiveryTone.Soft)
        rect(30, 6, 16, 2, LiveryTone.Soft)
        rect(44, 3, 2, 5, LiveryTone.Soft)
        rect(44, 3, 10, 2, LiveryTone.Soft)
        rect(52, 3, 2, 2, LiveryTone.AccentDark)
        rect(58, 3, 2, 2, LiveryTone.Accent)
    }
    // Staggered brick rows.
    GameId.BrickPulse -> livery(id) {
        for (k in 0..8) rect(1 + 7 * k, 1, 6, 2, if (k % 3 == 0) LiveryTone.Accent else LiveryTone.Soft)
        for (k in 0..7) rect(4 + 7 * k, 4, 6, 2, if (k % 4 == 1) LiveryTone.Accent else LiveryTone.Soft)
        for (k in 0..8) rect(1 + 7 * k, 7, 6, 2, LiveryTone.Faint)
    }
    // Starfield speckle around an orbit ring with a moon.
    GameId.OrbitBlaster -> livery(id) {
        var seed = 0x2F6B
        repeat(26) {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            val x = (seed shr 8) % LIVERY_COLS
            val y = (seed shr 16) % LIVERY_ROWS
            if (x < 28 || x > 60) dot(x, y, if (it % 3 == 0) LiveryTone.Soft else LiveryTone.Faint)
        }
        val cx = 44; val cy = 5; val rx = 14; val ry = 4
        var lastX = -1; var lastY = -1
        for (step in 0 until 56) {
            val a = 2.0 * PI * step / 56
            val x = cx + (rx * cos(a)).roundToInt()
            val y = cy + (ry * sin(a)).roundToInt()
            if (x != lastX || y != lastY) dot(x, y, LiveryTone.Soft)
            lastX = x; lastY = y
        }
        rect(cx - 1, cy - 1, 3, 3, LiveryTone.Accent)
        rect(cx + 12, cy + 2, 2, 2, LiveryTone.AccentDark)
    }
    // Rocks falling on a diagonal with fading tails.
    GameId.MeteorDodge -> livery(id) {
        val rocks = intArrayOf(12, 3, 3, 30, 5, 2, 46, 2, 3, 58, 6, 2)
        for (r in 0 until 4) {
            val x = rocks[r * 3]; val y = rocks[r * 3 + 1]; val s = rocks[r * 3 + 2]
            for (t in 3 downTo 1) {
                if (x - t >= 0 && y - t >= 0) dot(x - t, y - t, if (t == 1) LiveryTone.Soft else LiveryTone.Faint)
            }
            rect(x, y, s, s, LiveryTone.Accent)
        }
        dot(5, 8, LiveryTone.Faint); dot(22, 1, LiveryTone.Faint); dot(38, 8, LiveryTone.Faint); dot(52, 5, LiveryTone.Faint)
    }
    // Road edges, lane dashes and a chequered flag.
    GameId.PixelRacer -> livery(id) {
        rect(0, 1, 54, 1, LiveryTone.Faint)
        rect(0, 8, 54, 1, LiveryTone.Faint)
        for (k in 0..6) rect(2 + 8 * k, 4, 4, 2, LiveryTone.Soft)
        rect(54, 2, 1, 7, LiveryTone.Soft)
        for (r in 0..3) for (c in 0..7) if ((r + c) % 2 == 0) dot(55 + c, 3 + r, LiveryTone.AccentDark)
    }
    // Teaser slots never play, so they carry no decals.
    GameId.Soon1, GameId.Soon2, GameId.Soon3, GameId.Soon4 -> livery(id) {}
}

private val LIVERIES: List<Livery> = GameId.entries.map(::buildLivery)

/** Decals + badge text for [id]; built once, indexed by ordinal. */
fun gameLivery(id: GameId): Livery = LIVERIES[id.ordinal]

/** All liveries in menu order. */
val GAME_LIVERIES: List<Livery> get() = LIVERIES
