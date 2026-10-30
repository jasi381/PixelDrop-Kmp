package com.jasmeet.pixeldrop.ui.components

import androidx.compose.ui.unit.Dp

/** Half-open fold state reported by the platform; [Flat] also covers closed and non-foldable devices. */
enum class FoldPosture {
    Flat,

    /** Half-open with a horizontal hinge (Flip / clamshell): one half above, one below. */
    Tabletop,

    /** Half-open with a vertical hinge (Fold / iPhone Duo held like a book): one half left, one right. */
    Book,
}

enum class ConsoleLayout { Portrait, Wide, Clamshell, BookSplit }

/** The wide shell beats the portrait one on LCD size from about this width:height onward. */
private const val WIDE_MIN_ASPECT = 1.2f

/**
 * shortcut: assumes the hinge sits at the window centre (true for full-screen Fold, Flip and
 * iPhone Duo); use FoldingFeature.bounds if an off-centre device appears.
 */
fun consoleLayoutFor(width: Dp, height: Dp, posture: FoldPosture): ConsoleLayout = when {
    posture == FoldPosture.Tabletop -> ConsoleLayout.Clamshell
    posture == FoldPosture.Book -> ConsoleLayout.BookSplit
    width.value >= height.value * WIDE_MIN_ASPECT -> ConsoleLayout.Wide
    else -> ConsoleLayout.Portrait
}
