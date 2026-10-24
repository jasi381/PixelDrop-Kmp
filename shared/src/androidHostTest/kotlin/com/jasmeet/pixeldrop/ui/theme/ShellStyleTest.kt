package com.jasmeet.pixeldrop.ui.theme

import com.jasmeet.pixeldrop.game.GameId
import com.jasmeet.pixeldrop.game.isPlayable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellStyleTest {
    private val playable = GameId.entries.filter { it.isPlayable }

    @Test fun `every playable game has controls and a livery badge`() {
        for (g in playable) {
            assertTrue("$g has no used control", with(gameControls(g)) { usesUp || usesDown || usesLeft || usesRight || a != null || b != null })
            assertEquals(gameConsoleTheme(g).label, gameLivery(g).badge)
        }
    }

    @Test fun `captions teach once and only with game theme in a real game`() {
        assertTrue(shouldTeachCaptions(true, GameId.PixelDrop, alreadySeen = false))
        assertFalse(shouldTeachCaptions(true, GameId.PixelDrop, alreadySeen = true))
        assertFalse(shouldTeachCaptions(false, GameId.PixelDrop, alreadySeen = false))
        assertFalse(shouldTeachCaptions(true, null, alreadySeen = false))
    }

    @Test fun `style is plain outside game theme`() {
        assertNotNull(gameShellStyleFor(GameId.PixelRacer, true, true))
        assertEquals(GameShellStyle.Default, gameShellStyleFor(null, true, false))
        assertEquals(GameShellStyle.Default, gameShellStyleFor(GameId.PixelRacer, false, false))
    }
}
