package com.jasmeet.pixeldrop.ui.components

import com.jasmeet.pixeldrop.ui.theme.GameControls
import com.jasmeet.pixeldrop.ui.theme.LIVERY_COLS
import com.jasmeet.pixeldrop.ui.theme.LIVERY_ROWS
import com.jasmeet.pixeldrop.ui.theme.MAX_CAPTION_LENGTH

/** Axis-aligned rectangle in shell units. */
class NRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t

    fun intersects(o: NRect): Boolean = l < o.r && o.l < r && t < o.b && o.t < b
    fun contains(o: NRect): Boolean = o.l >= l && o.r <= r && o.t >= t && o.b <= b
}

/**
 * Where things sit in the control area of [RetroConsoleShell], as fractions of
 * the console width (`cw`). The origin is the top-left of the area under the
 * "DOT MATRIX" label, which spans [AREA_W] horizontally. The control positions
 * mirror the offsets in `RetroConsoleShell` (D-pad, A/B, pills); the decal band,
 * badge and caption shelves are placed in the gaps between them. Keeping this in
 * one pure object lets unit tests prove the decals and captions never collide
 * with a control, which cannot be seen without a device.
 *
 * Vertical positions that depend on text metrics (key labels, the START pill
 * column) use conservative estimates, marked below.
 */
object ShellGeometry {
    const val ASPECT = 0.545f
    const val AREA_W = 0.876f

    /** One dot-matrix cell of a caption, in `cw`. */
    const val CAPTION_PIXEL = 0.0045f
    const val BADGE_PIXEL = 0.0055f

    /** Gap between a key and its caption, in `cw`. */
    private const val CAPTION_GAP = 0.004f

    /** Estimated height of a key's own "A"/"B" label incl. spacer (about 3dp + 7 cells). */
    private const val KEY_LABEL = 0.04f

    private const val KEY = 0.155f
    private const val PILL_W = 0.115f

    val DPAD = NRect(0.015f, 0.035f / ASPECT, 0.315f, 0.035f / ASPECT + 0.30f)

    /** Key A incl. its label and ~12% of growth on every side. */
    val KEY_A = run {
        val r = AREA_W - 0.005f
        val t = 0.018f / ASPECT
        NRect(r - KEY - 0.010f, t - 0.010f, r + 0.010f, t + KEY + 0.010f + KEY_LABEL)
    }

    /** Key B incl. its label. */
    val KEY_B = run {
        val r = AREA_W - 0.005f - 0.17f
        val t = (0.018f + 0.052f) / ASPECT
        NRect(r - KEY, t, r, t + KEY + KEY_LABEL)
    }

    /** POWER + START pills (the caption for START sits above START only). */
    private const val START_L = 0.16f + PILL_W + 0.05f

    /** Shortest control area we expect (estimate from the shell column math at ~280dp wide). */
    const val AREA_H_MIN = 0.72f

    /** Bottom-anchored pill column: pill + label, 0.03*ch above the area bottom. */
    fun pillTop(areaH: Float): Float = areaH - 0.03f / ASPECT - 0.039f - 0.05f

    // ---- Livery band --------------------------------------------------------------------------

    const val BAND_L = 0.01f
    const val BAND_W = 0.856f
    const val BAND_T = 0.385f
    val BAND_CELL: Float get() = BAND_W / LIVERY_COLS
    val BAND = NRect(BAND_L, BAND_T, BAND_L + BAND_W, BAND_T + LIVERY_ROWS * BAND_CELL)

    // ---- Badge ---------------------------------------------------------------------------------

    const val BADGE_TOP = 0.008f

    fun badgeRect(text: String): NRect {
        val w = (text.length * 6 - 1) * BADGE_PIXEL
        val l = (AREA_W - w) / 2f
        return NRect(l, BADGE_TOP, l + w, BADGE_TOP + 7 * BADGE_PIXEL)
    }

    // ---- Captions ------------------------------------------------------------------------------

    private fun textW(text: String, pixel: Float) = (text.length * 6 - 1) * pixel

    /** Caption under a key, right-aligned to the key's right edge (label sits above it). */
    fun keyCaptionRect(keyRight: Float, keyBottom: Float, text: String): NRect {
        val w = textW(text, CAPTION_PIXEL)
        val t = keyBottom + CAPTION_GAP
        return NRect(keyRight - w, t, keyRight, t + 7 * CAPTION_PIXEL)
    }

    /** Caption above START, centred on the pill, for an area [areaH] tall. */
    fun startCaptionRect(text: String, areaH: Float): NRect {
        val w = textW(text, CAPTION_PIXEL)
        val cx = START_L + PILL_W / 2f
        val b = pillTop(areaH) - 0.01f
        return NRect(cx - w / 2f, b - 7 * CAPTION_PIXEL, cx + w / 2f, b)
    }

    /** Cell size of the D-pad legend: the default, shrunk so the widest row still clears key B. */
    fun legendPixel(maxCells: Int): Float {
        if (maxCells <= 0) return CAPTION_PIXEL
        val avail = KEY_B.l - LEGEND_MARGIN - (DPAD.r + LEGEND_MARGIN)
        return minOf(CAPTION_PIXEL, avail / maxCells)
    }

    const val LEGEND_MARGIN = 0.02f
    const val LEGEND_ROW_GAP_CELLS = 3

    /** Legend to the right of the D-pad, vertically centred on it. */
    fun legendRect(controls: GameControls): NRect? {
        val rows = controls.dpadLegend.size
        if (rows == 0) return null
        val p = legendPixel(controls.legendMaxCells)
        val h = (rows * 7 + (rows - 1) * LEGEND_ROW_GAP_CELLS) * p
        val cy = (DPAD.t + DPAD.b) / 2f
        val l = DPAD.r + LEGEND_MARGIN
        return NRect(l, cy - h / 2f, l + controls.legendMaxCells * p, cy + h / 2f)
    }

    /** All caption rectangles for [controls] (area of height [areaH]); used by layout tests. */
    fun captionRects(controls: GameControls, areaH: Float = AREA_H_MIN): List<NRect> {
        val out = ArrayList<NRect>(4)
        legendRect(controls)?.let(out::add)
        controls.a?.let { out.add(keyCaptionRect(KEY_A.r - 0.010f, KEY_A.b, it)) }
        controls.b?.let { out.add(keyCaptionRect(KEY_B.r, KEY_B.b, it)) }
        out.add(startCaptionRect(controls.start, areaH))
        return out
    }

    /** Sanity: the longest allowed caption at the default cell size. */
    val MAX_CAPTION_W: Float get() = textW("X".repeat(MAX_CAPTION_LENGTH), CAPTION_PIXEL)
}
