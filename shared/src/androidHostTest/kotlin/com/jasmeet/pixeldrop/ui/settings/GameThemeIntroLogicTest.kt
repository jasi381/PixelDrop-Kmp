package com.jasmeet.pixeldrop.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameThemeIntroLogicTest {
    @Test fun `intro offered once, never when already on`() {
        assertTrue(shouldShowGameThemeHint(false, false, true))
        assertFalse(shouldShowGameThemeHint(true, false, true))
        assertFalse(shouldShowGameThemeHint(false, true, true))
        assertFalse(shouldShowGameThemeHint(false, false, false))
    }

    @Test fun `preview index wraps both ways and skips teaser slots`() {
        assertEquals(5, nextIntroIndex(0, -1, GAME_THEME_INTRO_GAMES.size))
        assertEquals(0, nextIntroIndex(5, 1, GAME_THEME_INTRO_GAMES.size))
        assertEquals(6, GAME_THEME_INTRO_GAMES.size)
    }
}
