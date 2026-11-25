package com.jasmeet.pixeldrop.bombgrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BombGridEngineTest {
    private fun tick(engine: BombGridEngine, totalMs: Long) { repeat((totalMs / 50L).toInt()) { engine.tick(50L) } }

    @Test fun `initial grid starts with three lives and hunters`() {
        val s = BombGridEngine(randomInt = { 0 }).state
        assertEquals(1, s.grid); assertEquals(3, s.lives); assertTrue(s.hunters.isNotEmpty())
    }

    @Test fun `hard pillar blocks movement`() {
        val engine = BombGridEngine(randomInt = { 0 })
        engine.forceForTest(player = BombCell(2,1), crates = emptySet())
        engine.move(0,1)
        assertEquals(BombCell(2,1), engine.state.player)
    }

    @Test fun `placement obeys capacity and increments audio counter once`() {
        val engine = BombGridEngine(randomInt = { 0 })
        assertTrue(engine.placeBomb()); assertFalse(engine.placeBomb())
        assertEquals(1, engine.state.bombsPlaced); assertEquals(1, engine.state.bombs.size)
    }

    @Test fun `bomb waits for its complete fuse`() {
        val engine = BombGridEngine(randomInt = { 0 })
        engine.placeBomb(); tick(engine, BOMB_FUSE_MS - 100)
        assertEquals(1, engine.state.bombs.size); assertTrue(engine.state.blasts.isEmpty())
        tick(engine, 150)
        assertTrue(engine.state.bombs.isEmpty()); assertTrue(engine.state.blasts.isNotEmpty())
    }

    @Test fun `blast destroys first crate and stops behind it`() {
        val engine = BombGridEngine(randomInt = { 0 })
        engine.forceForTest(
            player = BombCell(1,1), hunters = listOf(GridHunter(9,BombCell(13,9),9_000)),
            crates = setOf(BombCell(3,1), BombCell(4,1)),
            bombs = listOf(GridBomb(1,BombCell(2,1),0,3)), invulnerableMs = 9_000,
        )
        engine.tick(16)
        assertFalse(BombCell(3,1) in engine.state.crates); assertTrue(BombCell(4,1) in engine.state.crates)
    }

    @Test fun `blast kills hunter and awards score`() {
        val engine = BombGridEngine(randomInt = { 0 })
        engine.forceForTest(
            hunters = listOf(GridHunter(1,BombCell(2,1),9_000)), crates = emptySet(),
            bombs = listOf(GridBomb(1,BombCell(1,1),0,2)), invulnerableMs = 9_000,
        )
        engine.tick(16)
        assertTrue(engine.state.score >= 200); assertEquals(1, engine.state.enemiesDestroyed)
    }

    @Test fun `one bomb triggers another and records chain two`() {
        val engine = BombGridEngine(randomInt = { 0 })
        engine.forceForTest(
            hunters = listOf(GridHunter(9,BombCell(13,9),9_000)), crates = emptySet(), invulnerableMs = 9_000,
            bombs = listOf(GridBomb(1,BombCell(1,1),0,2), GridBomb(2,BombCell(3,1),9_000,2)), bombCapacity = 2,
        )
        engine.tick(16)
        assertEquals(2, engine.state.bestChain); assertTrue(engine.state.bombs.isEmpty())
    }

    @Test fun `last life lost to blast ends game`() {
        val engine = BombGridEngine(randomInt = { 0 })
        engine.forceForTest(lives = 1, invulnerableMs = 0, hunters = listOf(GridHunter(9,BombCell(13,9),9_000)), crates = emptySet(), bombs = listOf(GridBomb(1,BombCell(1,1),0,1)))
        engine.tick(16)
        assertEquals(0, engine.state.lives); assertTrue(engine.isGameOver())
    }

    @Test fun `pause freezes fuse and hunters`() {
        val engine = BombGridEngine(randomInt = { 0 })
        engine.placeBomb(); engine.pause(); val before = engine.state
        tick(engine, 2_000)
        assertEquals(before, engine.state)
    }

    @Test fun `reset clears score chain and placement count`() {
        val engine = BombGridEngine(randomInt = { 0 })
        engine.placeBomb(); engine.reset()
        assertEquals(0, engine.state.score); assertEquals(0, engine.state.bestChain); assertEquals(0, engine.state.bombsPlaced)
    }
}
