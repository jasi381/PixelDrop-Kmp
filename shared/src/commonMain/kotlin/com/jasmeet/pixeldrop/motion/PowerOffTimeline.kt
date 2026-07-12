package com.jasmeet.pixeldrop.motion

import com.jasmeet.pixeldrop.motion.BootTimeline.ramp

/**
 * Shutdown choreography: freeze + LED flicker -> LCD fade -> screen collapses
 * to a glowing horizontal line -> line shrinks to a dot -> dot blinks out ->
 * dark powered-off panel. Console shell never moves.
 */
object PowerOffTimeline {
    const val TOTAL_MS = 2600

    const val FREEZE_END = 300      // content frozen, LED flicker, slight dim
    const val FADE_END = 800        // LCD fades toward dark green
    const val COLLAPSE_END = 1300   // content collapses into horizontal line
    const val SHRINK_END = 1800     // line shrinks from both sides to a dot
    const val DOT_END = 2200        // dot blinks once and disappears, LED fades

    /** How long POWER must be held to switch the console off. */
    const val COMBO_HOLD_MS = 1000L

    fun lcdBrightness(t: Int): Float = when {
        t < FREEZE_END -> 1f - 0.15f * ramp(t, 0, FREEZE_END)
        t < FADE_END -> 0.85f - 0.60f * ramp(t, FREEZE_END, FADE_END)
        t < DOT_END -> 0.25f - 0.20f * ramp(t, FADE_END, DOT_END)
        else -> 0.04f
    }

    fun ledBrightness(t: Int): Float = when {
        // Single quick flicker right as shutdown begins.
        t in 60 until 140 -> 0.15f
        t in 140 until 220 -> 0.8f
        t < DOT_END - 400 -> 1f
        t < DOT_END -> 1f - ramp(t, DOT_END - 400, DOT_END)
        else -> 0f
    }
}
