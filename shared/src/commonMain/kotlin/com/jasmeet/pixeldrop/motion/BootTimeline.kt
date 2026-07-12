package com.jasmeet.pixeldrop.motion

/**
 * Deterministic boot timing (matches the approved option-2 boot video):
 * power-off -> LED -> LCD wake -> scan sweep -> logo build -> subtitle +
 * loading bar -> system check -> block drop -> final press-start frame.
 */
object BootTimeline {
    const val TOTAL_MS = 6000

    const val LED_START = 600
    const val LED_END = 1100
    const val LCD_WAKE_START = 600
    const val LCD_WAKE_END = 1500
    const val SWEEP_START = 1200
    const val SWEEP_END = 2000
    const val FLICKER_START = 1350
    const val FLICKER_END = 1950
    const val LOGO_START = 2000
    const val LOGO_END = 2900
    const val SUBTITLE_START = 3000
    const val SUBTITLE_END = 3350
    const val BAR_START = 3000
    const val BAR_END = 4700
    const val SYS_START = 3800
    const val SYS_LINE_STEP = 170
    const val SYS_FADE_START = 4800
    const val SYS_FADE_END = 5050
    const val BLOCKS_START = 4800
    const val BLOCKS_END = 5500
    const val FINAL_START = 5500
    const val FINAL_END = 5900

    fun ramp(t: Int, start: Int, end: Int): Float =
        ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    fun ledBrightness(t: Int): Float = ramp(t, LED_START, LED_END)

    fun lcdBrightness(t: Int): Float = ramp(t, LCD_WAKE_START, LCD_WAKE_END)
}
