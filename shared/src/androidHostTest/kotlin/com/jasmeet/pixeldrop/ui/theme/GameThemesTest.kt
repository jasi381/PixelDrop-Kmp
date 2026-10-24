package com.jasmeet.pixeldrop.ui.theme

import com.jasmeet.pixeldrop.game.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GameThemesTest {
    private val base = CONSOLE_THEMES.last()

    @Test fun `game theme off or no game keeps the user paint`() {
        assertSame(base, resolveConsoleTheme(base, GameId.PixelRacer, gameThemeEnabled = false))
        assertSame(base, resolveConsoleTheme(base, null, gameThemeEnabled = true))
    }

    @Test fun `game theme on repaints per game`() {
        assertEquals("game_pixelracer", resolveConsoleTheme(base, GameId.PixelRacer, true).id)
        assertNotEquals(gameConsoleTheme(GameId.OrbitBlaster).id, gameConsoleTheme(GameId.BrickPulse).id)
    }

    @Test fun `shell ink contrasts with every game paint`() {
        for (t in GAME_CONSOLE_THEMES) assertEquals(true, contrastRatio(t.body, t.onBody) >= 4.5f)
    }
}
