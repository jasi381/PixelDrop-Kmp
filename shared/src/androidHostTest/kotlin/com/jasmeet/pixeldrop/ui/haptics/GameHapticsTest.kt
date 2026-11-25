package com.jasmeet.pixeldrop.ui.haptics

import com.jasmeet.pixeldrop.bombgrid.BombGridEngine
import com.jasmeet.pixeldrop.bombgrid.BombGridPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameHapticsTest {

    @Test
    fun `bomb grid event map`() {
        val s = BombGridEngine().state
        assertEquals(HapticEvent.Light, GameHaptics.bombGrid(s, s.copy(bombsPlaced = s.bombsPlaced + 1)))
        assertEquals(HapticEvent.Success, GameHaptics.bombGrid(s, s.copy(enemiesDestroyed = s.enemiesDestroyed + 1)))
        assertEquals(HapticEvent.Impact, GameHaptics.bombGrid(s, s.copy(lives = s.lives - 1)))
        assertEquals(HapticEvent.Success, GameHaptics.bombGrid(s, s.copy(grid = s.grid + 1)))
        assertNull(GameHaptics.bombGrid(s, s.copy(lives = 0, phase = BombGridPhase.GameOver)))
    }

    @Test
    fun `bomb grid blasts pulse per chain step, capped at three`() {
        val s = BombGridEngine().state
        assertEquals(HapticEvent.Medium, GameHaptics.bombGrid(s, s.copy(currentChain = 1)))
        assertEquals(HapticEvent.MediumChain2, GameHaptics.bombGrid(s, s.copy(currentChain = 2)))
        assertEquals(HapticEvent.MediumChain3, GameHaptics.bombGrid(s, s.copy(currentChain = 3)))
        assertEquals(HapticEvent.MediumChain3, GameHaptics.bombGrid(s, s.copy(currentChain = 8)))
        // Chain resetting to zero is not a blast.
        val chained = s.copy(currentChain = 3)
        assertNull(GameHaptics.bombGrid(chained, chained.copy(currentChain = 0)))
        // A shield absorbing a hit is medium, not the full impact.
        val shielded = s.copy(shieldHits = 1)
        assertEquals(HapticEvent.Medium, GameHaptics.bombGrid(shielded, shielded.copy(shieldHits = 0)))
    }
}
