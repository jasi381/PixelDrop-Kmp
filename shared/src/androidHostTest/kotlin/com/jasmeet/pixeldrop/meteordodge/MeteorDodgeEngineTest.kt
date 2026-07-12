package com.jasmeet.pixeldrop.meteordodge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteorDodgeEngineTest {

    private fun tickMany(engine: MeteorDodgeEngine, times: Int, deltaMs: Long = 16) {
        repeat(times) { engine.tick(deltaMs) }
    }

    /** Rolls of 0.1 keep everything deterministic and harmless: spawns land
     *  far left (x ~15) while the ship sits at center, so nothing collides
     *  by accident. */
    private fun engine() = MeteorDodgeEngine(randomFloat = { 0.1f })

    @Test
    fun `initial state has score 0, speed level 1, player centered low`() {
        val engine = engine()
        val s = engine.state
        assertEquals(0, s.score)
        assertEquals(0L, s.survivedMs)
        assertEquals(1, s.speedLevel)
        assertEquals(MeteorDodgePhase.Running, s.phase)
        assertTrue(s.meteors.isEmpty())
        assertTrue(s.bonuses.isEmpty())
        assertTrue(!s.shieldActive)
        assertEquals(METEOR_FIELD_WIDTH / 2f, s.playerX, 0.01f)
        assertTrue(s.playerY > METEOR_FIELD_HEIGHT / 2f)
    }

    @Test
    fun `player cannot leave the playfield or the lower zone`() {
        val engine = engine()
        val half = METEOR_PLAYER_WIDTH / 2f

        repeat(50) { engine.moveLeft() }
        assertEquals(half, engine.state.playerX, 0.01f)
        repeat(100) { engine.moveRight() }
        assertEquals(METEOR_FIELD_WIDTH - half, engine.state.playerX, 0.01f)

        repeat(50) { engine.moveUp() }
        assertEquals(METEOR_PLAYER_MIN_Y, engine.state.playerY, 0.01f)
        repeat(100) { engine.moveDown() }
        assertEquals(METEOR_PLAYER_MAX_Y, engine.state.playerY, 0.01f)
    }

    @Test
    fun `dash bursts sideways and respects its cooldown`() {
        val engine = engine()
        val before = engine.state.playerX
        engine.dash() // default direction is right
        assertEquals(before + 20f, engine.state.playerX, 0.01f)

        val afterFirst = engine.state.playerX
        engine.dash() // still cooling down
        assertEquals(afterFirst, engine.state.playerX, 0.01f)

        engine.tick(1_600)
        engine.moveLeft()
        engine.dash() // dashes left now
        assertTrue(engine.state.playerX < afterFirst)
        assertTrue("stays in bounds", engine.state.playerX >= METEOR_PLAYER_WIDTH / 2f)
    }

    @Test
    fun `meteors spawn over time`() {
        val engine = engine()
        tickMany(engine, 70) // ~1.1s past the initial 1s spawn timer
        assertTrue(engine.state.meteors.isNotEmpty())
    }

    @Test
    fun `meteors fall and are removed below the field`() {
        val engine = engine()
        engine.forceForTest(meteors = listOf(Meteor(id = 100, x = 20f, y = 120f, radius = 2.5f, velocityY = 40f, driftX = 0f)))
        engine.tick(16)
        assertTrue("meteor fell", engine.state.meteors.first { it.id == 100 }.y > 120f)
        tickMany(engine, 30)
        assertTrue(engine.state.meteors.none { it.id == 100 })
    }

    @Test
    fun `score grows with survival time`() {
        val engine = engine()
        engine.tick(1_000)
        assertEquals(10, engine.state.score) // +10 per second survived
        assertEquals(1_000L, engine.state.survivedMs)
    }

    @Test
    fun `speed level steps up every 15 seconds`() {
        val engine = engine()
        engine.tick(15_100)
        assertEquals(2, engine.state.speedLevel)
        engine.tick(15_000)
        assertEquals(3, engine.state.speedLevel)
    }

    @Test
    fun `meteor hitting the player ends the run`() {
        val engine = engine()
        engine.forceForTest(
            meteors = listOf(Meteor(id = 100, x = engine.state.playerX, y = engine.state.playerY - 2f, radius = 2.5f, velocityY = 40f, driftX = 0f)),
        )
        engine.tick(16)
        assertEquals(MeteorDodgePhase.GameOver, engine.state.phase)
        assertTrue(engine.isGameOver())
    }

    @Test
    fun `shield blocks one hit and is spent`() {
        val engine = engine()
        engine.forceForTest(
            meteors = listOf(Meteor(id = 100, x = engine.state.playerX, y = engine.state.playerY - 2f, radius = 2.5f, velocityY = 40f, driftX = 0f)),
            shieldActive = true,
        )
        engine.tick(16)
        assertEquals(MeteorDodgePhase.Running, engine.state.phase)
        assertTrue("shield spent", !engine.state.shieldActive)
        assertTrue("blocking rock destroyed", engine.state.meteors.none { it.id == 100 })
    }

    @Test
    fun `score bonus dot pays out on catch`() {
        val engine = engine()
        engine.forceForTest(
            bonuses = listOf(MeteorBonus(id = 200, type = MeteorBonusType.Score, x = engine.state.playerX, y = engine.state.playerY - 3f, velocityY = 30f)),
        )
        engine.tick(16)
        assertEquals(100, engine.state.score)
        assertTrue(engine.state.bonuses.isEmpty())
        assertEquals(1, engine.state.bonusesCollected)
    }

    @Test
    fun `shield bonus arms the shield on catch`() {
        val engine = engine()
        engine.forceForTest(
            bonuses = listOf(MeteorBonus(id = 200, type = MeteorBonusType.Shield, x = engine.state.playerX, y = engine.state.playerY - 3f, velocityY = 30f)),
        )
        engine.tick(16)
        assertTrue(engine.state.shieldActive)
        assertEquals(1, engine.state.bonusesCollected)
    }

    @Test
    fun `bonus falling past the bottom is removed uncaught`() {
        val engine = engine()
        engine.forceForTest(
            bonuses = listOf(MeteorBonus(id = 200, type = MeteorBonusType.Score, x = 15f, y = 128f, velocityY = 30f)),
        )
        tickMany(engine, 20)
        assertTrue(engine.state.bonuses.isEmpty())
        assertEquals(0, engine.state.bonusesCollected)
    }

    @Test
    fun `bonuses spawn on their own timer, dodging occupied spots`() {
        val engine = engine()
        // First attempt at 6.1s lands next to the meteor spawned the same
        // tick, so the spawn defers; the 500ms retry finds the spot clear.
        engine.tick(6_100)
        assertTrue(engine.state.bonuses.isEmpty())
        engine.tick(500)
        assertTrue(engine.state.bonuses.isNotEmpty())
    }

    @Test
    fun `restart resets score time speed and phase`() {
        val engine = engine()
        engine.forceForTest(
            meteors = listOf(Meteor(id = 100, x = engine.state.playerX, y = engine.state.playerY, radius = 2.5f, velocityY = 40f, driftX = 0f)),
        )
        engine.tick(16)
        assertTrue(engine.isGameOver())

        engine.reset()
        val s = engine.state
        assertEquals(0, s.score)
        assertEquals(0L, s.survivedMs)
        assertEquals(1, s.speedLevel)
        assertEquals(MeteorDodgePhase.Running, s.phase)
        assertTrue(s.meteors.isEmpty())
    }
}
