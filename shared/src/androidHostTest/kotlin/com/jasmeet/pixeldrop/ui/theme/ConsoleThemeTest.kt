package com.jasmeet.pixeldrop.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleThemeTest {

    @Test
    fun `every console theme chooses readable body label ink`() {
        for (theme in CONSOLE_THEMES) {
            val ratio = contrastRatio(theme.body, theme.onBody)
            assertTrue("${theme.label} label contrast was $ratio", ratio >= 4.5f)
        }
    }

    @Test
    fun `contrast ink flips between light and dark shells`() {
        val classic = consoleThemeFor("ivory")
        val midnight = consoleThemeFor("midnight")

        assertTrue(classic.onBody != midnight.onBody)
        assertTrue(contrastRatio(classic.body, classic.onBody) >= 4.5f)
        assertTrue(contrastRatio(midnight.body, midnight.onBody) >= 4.5f)
    }

    @Test
    fun `every themed dpad keeps its arrows readable`() {
        for (theme in CONSOLE_THEMES) {
            val ratio = contrastRatio(theme.dpadFace, theme.onDpad)
            assertTrue("${theme.label} D-pad arrow contrast was $ratio", ratio >= 3f)
        }
    }
}
