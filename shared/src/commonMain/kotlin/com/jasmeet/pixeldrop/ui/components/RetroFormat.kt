package com.jasmeet.pixeldrop.ui.components

/**
 * Multiplatform stand-ins for the JVM-only `String.format` counters the LCD
 * screens use. Zero-padded fixed-width numbers are the whole retro look, so
 * these are deliberately tiny and allocation-light.
 */
fun retroPad(value: Int, width: Int): String = value.toString().padStart(width, '0')

fun retroPad(value: Long, width: Int): String = value.toString().padStart(width, '0')

/** Thousands-grouped score (the old `"%,d"`): 1234567 -> 1,234,567. */
fun retroGrouped(value: Int): String {
    val digits = value.toString()
    val negative = digits.startsWith('-')
    val body = if (negative) digits.substring(1) else digits
    val grouped = buildString {
        body.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) append(',')
            append(c)
        }
    }.reversed()
    return if (negative) "-$grouped" else grouped
}
