package com.jasmeet.pixeldrop.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * Console-body label styles. LCD text is never drawn with these —
 * it uses the Canvas pixel font in PixelText.kt.
 */
object PixelDropType {
    /** Tiny embossed plastic labels: BATTERY, SELECT, START, A, B. */
    val ConsoleLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.14.em,
    )

    /** Wider tracked accent labels: CANVAS CLASSICS, DOT MATRIX line. */
    val AccentLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.24.em,
    )
}
