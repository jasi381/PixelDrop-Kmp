package com.jasmeet.pixeldrop.ui.screens

import com.jasmeet.pixeldrop.game.GameId
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Pure, allocation-free frame logic for the live menu previews.
 *
 * Every demo is a deterministic function of a loop phase t in [0, 1) that emits
 * rectangles, in preview-grid units (1.0 = one cell of the 9x16 field), into a
 * [DemoSink]. No Compose, no Android: the JVM tests record the rectangles and
 * check loops, bounds and coverage. Positions are stepped/snapped (whole cells,
 * or quarter cells for the road and quarter-cell sprites for Ridge Runner) so the
 * motion reads like an LCD, not a tween.
 */

internal const val DEMO_COLS = 9
internal const val DEMO_ROWS = 16

/** One loop of every demo. A multiple of 900 ms so the chevron blink still wraps cleanly. */
internal const val DEMO_LOOP_MS = 3600

/** Loop phase held when the system animator scale is 0 (reduced motion): mid-action in every demo. */
internal const val DEMO_STILL_T = 0.55f

/** Stepped ink tones: full, then three alpha steps, then the LCD paper for cut-outs. */
internal const val TONE_FULL = 0
internal const val TONE_MID = 1
internal const val TONE_LOW = 2
internal const val TONE_FAINT = 3
internal const val TONE_PAPER = 4

internal interface DemoSink {
    /** Rect in cell units; the sink decides how to snap it to device pixels. */
    fun rect(x: Float, y: Float, w: Float, h: Float, tone: Int)
}

/** Clips to the 9x16 field so demos may overhang the edges (cars, meteors, sprites). */
internal fun DemoSink.box(x: Float, y: Float, w: Float, h: Float, tone: Int) {
    val x1 = if (x < 0f) 0f else x
    val y1 = if (y < 0f) 0f else y
    val x2 = if (x + w > DEMO_COLS) DEMO_COLS.toFloat() else x + w
    val y2 = if (y + h > DEMO_ROWS) DEMO_ROWS.toFloat() else y + h
    if (x2 <= x1 || y2 <= y1) return
    rect(x1, y1, x2 - x1, y2 - y1, tone)
}

private const val CELL_GAP = 0.08f

/** One LCD cell with the usual hairline gap; out-of-field cells are skipped. */
internal fun DemoSink.cell(r: Int, c: Int, tone: Int = TONE_FULL) {
    if (r < 0 || r >= DEMO_ROWS || c < 0 || c >= DEMO_COLS) return
    rect(c + CELL_GAP, r + CELL_GAP, 1f - 2f * CELL_GAP, 1f - 2f * CELL_GAP, tone)
}

/** Draws the live demo for [game] at loop phase [t]. Exhaustive over [GameId]. */
internal fun renderDemo(game: GameId, t: Float, out: DemoSink) = when (game) {
    GameId.PixelDrop -> demoPixelDrop(t, out)
    GameId.PixelTrail -> demoPixelTrail(t, out)
    GameId.BrickPulse -> demoBrickPulse(t, out)
    GameId.OrbitBlaster -> demoOrbitBlaster(t, out)
    GameId.MeteorDodge -> demoMeteorDodge(t, out)
    GameId.PixelRacer -> demoPixelRacer(t, out)
    // Teaser slots draw a text sprite instead (see drawComingSoonSprite).
    GameId.Soon1, GameId.Soon2, GameId.Soon3, GameId.Soon4 -> Unit
}

// ---- small helpers -------------------------------------------------------

/** 0..1 progress of [t] through [a, b], clamped. */
private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

/** Square-wave blink at [rate] on/off pairs per loop unit. */
private fun blinkOn(t: Float, rate: Float): Boolean = ((t * rate).toInt() and 1) == 0

private fun mask(vararg cols: Int): Int {
    var m = 0
    for (c in cols) m = m or (1 shl c)
    return m
}

private fun DemoSink.maskRow(row: Int, m: Int, tone: Int = TONE_FULL) {
    for (c in 0 until DEMO_COLS) if (((m shr c) and 1) == 1) cell(row, c, tone)
}

// ---- PIXEL DROP: piece falls, locks, the line flashes and clears ----------

private val DROP_INIT = IntArray(DEMO_ROWS).also {
    it[13] = mask(0, 8)
    it[14] = mask(0, 1, 7, 8)
    it[15] = mask(0, 1, 2, 5, 6, 7, 8)
}
private val DROP_POST = IntArray(DEMO_ROWS).also {
    it[14] = mask(0, 8)
    it[15] = mask(0, 1, 3, 4, 7, 8)
}

private fun DemoSink.dropStack(rows: IntArray, skipRow: Int = -1) {
    for (r in 12 until DEMO_ROWS) if (r != skipRow) maskRow(r, rows[r])
}

private fun DemoSink.dropPiece(top: Int) {
    cell(top, 3); cell(top, 4); cell(top + 1, 3); cell(top + 1, 4)
}

private fun demoPixelDrop(t: Float, out: DemoSink) {
    when {
        t < 0.5f -> {
            out.dropStack(DROP_INIT)
            // Ghost of the landing spot, then the piece falling one row per step.
            out.cell(14, 3, TONE_FAINT); out.cell(14, 4, TONE_FAINT)
            out.cell(15, 3, TONE_FAINT); out.cell(15, 4, TONE_FAINT)
            out.dropPiece((seg(t, 0f, 0.5f) * 15f).toInt())
        }
        t < 0.6f -> {
            out.dropStack(DROP_INIT)
            out.dropPiece(14)
        }
        t < 0.8f -> {
            // Locked: the bottom row is complete and flashes.
            out.dropStack(DROP_INIT, skipRow = 15)
            out.cell(14, 3); out.cell(14, 4)
            if (blinkOn(t - 0.6f, 40f)) {
                out.maskRow(15, DROP_INIT[15]); out.cell(15, 3); out.cell(15, 4)
            }
        }
        t < 0.9f -> out.dropStack(DROP_POST)
        else -> out.dropStack(DROP_INIT)
    }
}

// ---- PIXEL TRAIL: trail moves, eats the dot, grows ------------------------

private const val TRAIL_LEN = 26

/** Closed circuit rows 4..11 x cols 1..7, packed as row * DEMO_COLS + col. */
private val TRAIL_PATH: IntArray = IntArray(TRAIL_LEN).also {
    var i = 0
    for (c in 1..7) it[i++] = 4 * DEMO_COLS + c
    for (r in 5..11) it[i++] = r * DEMO_COLS + 7
    for (c in 6 downTo 1) it[i++] = 11 * DEMO_COLS + c
    for (r in 10 downTo 5) it[i++] = r * DEMO_COLS + 1
}
private const val TRAIL_FOOD_STEP = 13

private fun demoPixelTrail(t: Float, out: DemoSink) {
    val step = (t * TRAIL_LEN).toInt().coerceAtMost(TRAIL_LEN - 1)
    val eaten = step >= TRAIL_FOOD_STEP
    val len = if (eaten) 6 else 5
    if (blinkOn(t, 24f)) {
        if (eaten) out.cell(7, 4) else {
            val p = TRAIL_PATH[TRAIL_FOOD_STEP]
            out.cell(p / DEMO_COLS, p % DEMO_COLS)
        }
    }
    for (k in 0 until len) {
        val p = TRAIL_PATH[((step - k) % TRAIL_LEN + TRAIL_LEN) % TRAIL_LEN]
        val r = p / DEMO_COLS
        val c = p % DEMO_COLS
        out.cell(r, c, if (k == len - 1) TONE_MID else TONE_FULL)
        if (k == 0) out.box(c + 0.55f, r + 0.3f, 0.2f, 0.2f, TONE_PAPER)
    }
}

// ---- BRICK PULSE: ball bounces, brick pops, paddle tracks -----------------

private const val BRICK_HIT_COL = 6

private fun demoBrickPulse(t: Float, out: DemoSink) {
    // The struck brick is gone from the hit until the wall rebuilds at the end.
    val hitGone = t >= 0.5f && t < 0.9f
    val rebuilding = t >= 0.9f && t < 0.95f
    for (c in 0 until DEMO_COLS) {
        out.cell(2, c)
        if (c == BRICK_HIT_COL) {
            if (!hitGone && (!rebuilding || blinkOn(t, 60f))) out.cell(3, c, TONE_MID)
        } else out.cell(3, c, TONE_MID)
    }
    if (t >= 0.5f && t < 0.62f) {
        // Pop: sparks fly out sideways, then drift down a row.
        val drift = if (t < 0.56f) 0 else 1
        out.cell(4 + drift, BRICK_HIT_COL - 1, TONE_MID)
        out.cell(4 + drift, BRICK_HIT_COL + 1, TONE_MID)
        if (blinkOn(t, 60f)) out.cell(3, BRICK_HIT_COL, TONE_FULL)
    }
    // Ball: up 8 rows / 4 cols, then back down to where it started.
    val ballRow: Int
    val ballCol: Int
    val trailRow: Int
    val trailCol: Int
    if (t < 0.5f) {
        val k = (seg(t, 0f, 0.5f) * 8f).toInt()
        ballRow = 12 - k; ballCol = 2 + k / 2
        trailRow = ballRow + 1; trailCol = 2 + (k - 1).coerceAtLeast(0) / 2
    } else {
        val k = (seg(t, 0.5f, 1f) * 8f).toInt()
        ballRow = 4 + k; ballCol = 6 - k / 2
        trailRow = ballRow - 1; trailCol = 6 - (k - 1).coerceAtLeast(0) / 2
    }
    out.cell(trailRow, trailCol, TONE_FAINT)
    out.cell(ballRow, ballCol)
    // Paddle mirrors the ball's column, clamped so all three cells stay on the field.
    val pc = ballCol.coerceIn(1, DEMO_COLS - 2)
    out.cell(13, pc - 1); out.cell(13, pc); out.cell(13, pc + 1)
}

// ---- ORBIT BLASTER: ship slides, shot fires, target bursts ----------------

private val ORBIT_COLS = intArrayOf(1, 4, 7)

/** Enemy at [col]: dead from [deadFrom] to [respawn]; a burst plays for the first stretch. */
private fun DemoSink.orbitEnemy(t: Float, col: Int, deadFrom: Float, respawn: Float, alt: Boolean) {
    val dead = t >= deadFrom && t < respawn
    val respawning = t >= respawn && t < respawn + 0.05f
    if (!dead && (!respawning || blinkOn(t, 60f))) {
        cell(1, col)
        cell(2, col)
        if (alt) { cell(2, col - 1); cell(2, col + 1) }
    }
    if (t >= deadFrom && t < deadFrom + 0.15f) {
        val stage = seg(t, deadFrom, deadFrom + 0.15f)
        if (stage < 0.5f) {
            cell(1, col - 1, TONE_MID); cell(1, col + 1, TONE_MID)
            cell(3, col - 1, TONE_MID); cell(3, col + 1, TONE_MID)
            cell(2, col)
        } else {
            cell(0, col - 2, TONE_LOW); cell(0, col + 2, TONE_LOW)
            cell(4, col - 2, TONE_LOW); cell(4, col + 2, TONE_LOW)
            cell(2, col - 2, TONE_LOW); cell(2, col + 2, TONE_LOW)
        }
    }
}

private fun DemoSink.orbitShot(t: Float, col: Int, fireT: Float, hitT: Float) {
    if (t < fireT || t >= hitT) return
    val k = (seg(t, fireT, hitT) * 10f).toInt()
    cell(13 - k, col)
    cell(14 - k, col, TONE_FAINT)
}

private fun demoOrbitBlaster(t: Float, out: DemoSink) {
    val alt = ((t * 8f).toInt() and 1) == 1
    out.orbitEnemy(t, ORBIT_COLS[0], 2f, 3f, alt)          // never shot: stays alive
    out.orbitEnemy(t, ORBIT_COLS[1], 0.72f, 0.93f, alt)
    out.orbitEnemy(t, ORBIT_COLS[2], 0.45f, 0.85f, alt)
    out.orbitShot(t, 7, 0.25f, 0.45f)
    out.orbitShot(t, 4, 0.52f, 0.72f)
    // Ship glides side to side, stepping cell by cell.
    val col = (4f + 3f * sin(2.0 * PI * t).toFloat()).roundToInt()
    out.cell(14, col)
    out.cell(15, col - 1); out.cell(15, col); out.cell(15, col + 1)
}

// ---- METEOR DODGE: meteors fall, ship sidesteps, near-miss spark ----------

private const val METEOR_TRAVEL = 20 // rows per loop, from 3 above the field to 17 below it

private fun DemoSink.meteor(t: Float, col: Int, phase: Float) {
    var u = t + phase
    if (u >= 1f) u -= 1f
    val r = (u * METEOR_TRAVEL).toInt() - 3
    cell(r, col); cell(r - 1, col); cell(r + 1, col); cell(r, col - 1); cell(r, col + 1)
}

private fun demoMeteorDodge(t: Float, out: DemoSink) {
    out.meteor(t, 7, 0f)
    out.meteor(t, 4, 0.3f)
    out.meteor(t, 1, 0.6f)
    val ship = when {
        t < 0.40f -> 4
        t < 0.43f -> 5
        t < 0.46f -> 6
        t < 0.72f -> 7
        t < 0.75f -> 6
        t < 0.78f -> 5
        else -> 4
    }
    out.cell(14, ship)
    out.cell(15, ship - 1); out.cell(15, ship); out.cell(15, ship + 1)
    // Near-miss: the middle meteor grazes past the ship's wing.
    if (t >= 0.48f && t < 0.62f && blinkOn(t, 50f)) {
        out.cell(13, 6, TONE_MID)
        out.cell(15, 5, TONE_MID)
    }
}

// ---- PIXEL RACER: lanes scroll, car changes lane past traffic -------------

private val RACER_LANE_X = floatArrayOf(1.75f, 4.5f, 7.25f)

private fun DemoSink.racerCar(cx: Float, top: Float) {
    box(cx - 0.5f, top, 1f, 2f, TONE_FULL)
    box(cx - 0.25f, top + 0.5f, 0.5f, 0.5f, TONE_PAPER)
    box(cx - 0.75f, top + 0.25f, 0.25f, 0.5f, TONE_FULL)
    box(cx + 0.5f, top + 0.25f, 0.25f, 0.5f, TONE_FULL)
    box(cx - 0.75f, top + 1.25f, 0.25f, 0.5f, TONE_FULL)
    box(cx + 0.5f, top + 1.25f, 0.25f, 0.5f, TONE_FULL)
}

/** Traffic car: top edge moves in quarter-row steps, 22 rows per loop, slower than the road. */
private fun DemoSink.racerTraffic(t: Float, lane: Int, phase: Float) {
    var u = t + phase
    if (u >= 1f) u -= 1f
    racerCar(RACER_LANE_X[lane], (u * 88f).toInt() * 0.25f - 4f)
}

private fun demoPixelRacer(t: Float, out: DemoSink) {
    // Shoulders, then two dashed lane dividers scrolling in quarter-row steps.
    out.box(0f, 0f, 0.5f, DEMO_ROWS.toFloat(), TONE_FULL)
    out.box(8.5f, 0f, 0.5f, DEMO_ROWS.toFloat(), TONE_FULL)
    val scroll = ((t * 128f).toInt() % 8) * 0.25f
    for (k in -1 until 9) {
        val y = 2f * k + scroll
        out.box(2.875f, y, 0.25f, 1f, TONE_MID)
        out.box(5.875f, y, 0.25f, 1f, TONE_MID)
    }
    out.racerTraffic(t, 2, 0.55f)
    out.racerTraffic(t, 1, 0.31f)
    out.racerTraffic(t, 0, 0.86f)
    // Player: hold lane 2, slide left past the oncoming car, slide back.
    val px = when {
        t < 0.22f -> RACER_LANE_X[1]
        t < 0.30f -> RACER_LANE_X[1] - (RACER_LANE_X[1] - RACER_LANE_X[0]) * ((seg(t, 0.22f, 0.30f) * 11f).toInt() / 11f)
        t < 0.60f -> RACER_LANE_X[0]
        t < 0.68f -> RACER_LANE_X[0] + (RACER_LANE_X[1] - RACER_LANE_X[0]) * ((seg(t, 0.60f, 0.68f) * 11f).toInt() / 11f)
        else -> RACER_LANE_X[1]
    }
    out.racerCar(px, 12.5f)
}
