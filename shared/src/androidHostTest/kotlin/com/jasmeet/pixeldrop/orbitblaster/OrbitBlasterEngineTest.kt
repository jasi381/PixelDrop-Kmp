package com.jasmeet.pixeldrop.orbitblaster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitBlasterEngineTest {

    private fun tickMany(engine: OrbitBlasterEngine, times: Int, deltaMs: Long = 16) {
        repeat(times) { engine.tick(deltaMs) }
    }

    /** An enemy parked high up and far right — keeps a wave "alive" without
     *  interfering with whatever the test is aiming at around the player. */
    private fun safeEnemy(id: Int = 900) = OrbitEnemy(id = id, x = 90f, y = 16f)

    // --- MVP ---------------------------------------------------------------

    @Test
    fun `initial state has score 0, lives 3, wave 1, enemies present`() {
        val engine = OrbitBlasterEngine()
        val s = engine.state
        assertEquals(0, s.score)
        assertEquals(3, s.lives)
        assertEquals(1, s.wave)
        assertEquals(OrbitBlasterPhase.Running, s.phase)
        assertEquals(5, s.enemies.size) // wave 1: one row of five
        assertNull(s.boss)
        assertTrue(s.playerBullets.isEmpty())
        assertTrue(s.enemyBullets.isEmpty())
        assertEquals(ORBIT_FIELD_WIDTH / 2f, s.playerX, 0.01f)
    }

    @Test
    fun `player cannot move outside the playfield`() {
        val engine = OrbitBlasterEngine()
        val half = PLAYER_WIDTH / 2f

        repeat(50) { engine.moveLeft() }
        assertEquals(half, engine.state.playerX, 0.01f)

        repeat(100) { engine.moveRight() }
        assertEquals(ORBIT_FIELD_WIDTH - half, engine.state.playerX, 0.01f)
    }

    @Test
    fun `shooting creates one bullet and respects the cooldown`() {
        val engine = OrbitBlasterEngine()
        engine.shoot()
        assertEquals(1, engine.state.playerBullets.size)
        assertTrue("player bullets fly up", engine.state.playerBullets.single().velocityY < 0f)

        engine.shoot() // still cooling down
        assertEquals(1, engine.state.playerBullets.size)

        engine.tick(300) // past the 250ms cooldown
        engine.shoot()
        assertEquals(2, engine.state.playerBullets.size)
    }

    @Test
    fun `player bullet count is capped`() {
        val engine = OrbitBlasterEngine()
        engine.forceForTest(
            playerBullets = (0 until 4).map { OrbitBullet(id = 700 + it, x = 10f + it, y = 60f, velocityY = -95f, fromEnemy = false) },
        )
        engine.shoot()
        assertEquals(4, engine.state.playerBullets.size)
    }

    @Test
    fun `bullets move upward and are removed offscreen`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        repeat(20) { engine.moveLeft() } // fire lane far from the formation
        engine.forceForTest(enemies = listOf(safeEnemy()))
        engine.shoot()
        val startY = engine.state.playerBullets.single().y
        engine.tick(16)
        assertTrue("bullet rose", engine.state.playerBullets.single().y < startY)
        tickMany(engine, 100)
        assertTrue("bullet left the field", engine.state.playerBullets.isEmpty())
    }

    @Test
    fun `bullet hitting an enemy removes it and adds score`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f }) // no drops, no enemy fire
        // Bullet pre-placed a hair under the target — formation drift can't
        // carry the enemy out of the overlap within one tick.
        engine.forceForTest(
            enemies = listOf(OrbitEnemy(id = 100, x = 50f, y = 60f), safeEnemy()),
            playerBullets = listOf(OrbitBullet(id = 600, x = 50f, y = 63f, velocityY = -95f, fromEnemy = false)),
        )
        engine.tick(16)
        assertEquals(1, engine.state.enemies.size)
        assertEquals(900, engine.state.enemies.single().id)
        assertEquals(20, engine.state.score)
        assertEquals(1, engine.state.enemiesKilled)
        assertTrue("bullet spent on the hit", engine.state.playerBullets.isEmpty())
        assertTrue("explosion spawned", engine.state.explosions.isNotEmpty())
    }

    @Test
    fun `clearing all enemies advances the wave and banks the bonus`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            enemies = listOf(OrbitEnemy(id = 100, x = 50f, y = 60f)),
            playerBullets = listOf(OrbitBullet(id = 600, x = 50f, y = 63f, velocityY = -95f, fromEnemy = false)),
        )
        engine.tick(16)
        assertEquals(2, engine.state.wave)
        // 20 for the kill + 100 * cleared wave 1.
        assertEquals(120, engine.state.score)
        assertTrue("next formation spawned", engine.state.enemies.isNotEmpty())
        assertNull(engine.state.boss)
    }

    @Test
    fun `enemy crossing the danger line costs a life and dies in place`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(enemies = listOf(OrbitEnemy(id = 100, x = 90f, y = DANGER_Y + 1f), safeEnemy()))
        engine.tick(16)
        assertEquals(2, engine.state.lives)
        assertEquals(1, engine.state.enemies.size)
        assertEquals(900, engine.state.enemies.single().id)
        assertTrue("post-hit grace armed", engine.state.invulnerableMs > 0)
        assertEquals(ORBIT_FIELD_WIDTH / 2f, engine.state.playerX, 0.01f)
        assertEquals(OrbitBlasterPhase.Running, engine.state.phase)
    }

    @Test
    fun `lives reaching zero causes game over`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(lives = 1, enemies = listOf(OrbitEnemy(id = 100, x = 90f, y = DANGER_Y + 1f), safeEnemy()))
        engine.tick(16)
        assertEquals(0, engine.state.lives)
        assertEquals(OrbitBlasterPhase.GameOver, engine.state.phase)
        assertTrue(engine.isGameOver())
        assertTrue("no bullets keep flying after game over", engine.state.playerBullets.isEmpty() && engine.state.enemyBullets.isEmpty())
    }

    @Test
    fun `restart resets score lives and wave`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(lives = 1, wave = 7, enemies = listOf(OrbitEnemy(id = 100, x = 90f, y = DANGER_Y + 1f)))
        engine.tick(16)
        assertTrue(engine.isGameOver())

        engine.reset()
        val s = engine.state
        assertEquals(0, s.score)
        assertEquals(3, s.lives)
        assertEquals(1, s.wave)
        assertEquals(OrbitBlasterPhase.Running, s.phase)
        assertTrue(s.enemies.isNotEmpty())
        assertNull(s.boss)
    }

    // --- Enemy bullets -------------------------------------------------------

    @Test
    fun `enemies never shoot on wave 1`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0f }) // every roll passes
        tickMany(engine, 50)
        assertTrue(engine.state.enemyBullets.isEmpty())
    }

    @Test
    fun `enemies shoot from wave 2 and respect the bullet cap`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0f })
        engine.forceForTest(wave = 2, enemies = listOf(safeEnemy()))
        engine.tick(16)
        assertEquals(1, engine.state.enemyBullets.size)
        val bullet = engine.state.enemyBullets.single()
        assertTrue(bullet.fromEnemy)
        assertTrue("enemy bullets fall down", bullet.velocityY > 0f)
        tickMany(engine, 20)
        assertTrue("cap holds", engine.state.enemyBullets.size <= 4)
        assertTrue(engine.state.enemyBullets.size >= 2)
    }

    @Test
    fun `enemy bullets move down and are removed offscreen`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            enemies = listOf(safeEnemy()),
            enemyBullets = listOf(OrbitBullet(id = 500, x = 90f, y = 100f, velocityY = 45f, fromEnemy = true)),
        )
        engine.tick(16)
        val y = engine.state.enemyBullets.single().y
        assertTrue("bullet fell", y > 100f)
        tickMany(engine, 100)
        assertTrue(engine.state.enemyBullets.isEmpty())
    }

    @Test
    fun `enemy bullet hitting the player costs a life`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            enemies = listOf(safeEnemy()),
            enemyBullets = listOf(OrbitBullet(id = 500, x = 50f, y = 116f, velocityY = 45f, fromEnemy = true)),
        )
        engine.tick(16)
        assertEquals(2, engine.state.lives)
        assertTrue("bullets sweep clean on life loss", engine.state.enemyBullets.isEmpty())
        assertTrue(engine.state.invulnerableMs > 0)
    }

    @Test
    fun `shield blocks the hit and is consumed`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            enemies = listOf(safeEnemy()),
            enemyBullets = listOf(OrbitBullet(id = 500, x = 50f, y = 116f, velocityY = 45f, fromEnemy = true)),
            shieldMs = 5_000L,
        )
        engine.tick(16)
        assertEquals(3, engine.state.lives)
        assertEquals(0L, engine.state.shieldMs)
        assertTrue("brief grace after the block", engine.state.invulnerableMs > 0)
    }

    @Test
    fun `invulnerability lets bullets pass through`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            enemies = listOf(safeEnemy()),
            enemyBullets = listOf(OrbitBullet(id = 500, x = 50f, y = 116f, velocityY = 45f, fromEnemy = true)),
            invulnerableMs = 2_000L,
        )
        engine.tick(16)
        assertEquals(3, engine.state.lives)
        assertEquals(1, engine.state.enemyBullets.size)
    }

    // --- Boss ---------------------------------------------------------------

    @Test
    fun `every 5th wave spawns a boss instead of a formation`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            wave = 4,
            enemies = listOf(OrbitEnemy(id = 100, x = 50f, y = 60f)),
            playerBullets = listOf(OrbitBullet(id = 600, x = 50f, y = 63f, velocityY = -95f, fromEnemy = false)),
        )
        engine.tick(16)
        assertEquals(5, engine.state.wave)
        val boss = engine.state.boss
        assertNotNull(boss)
        assertEquals(8, boss!!.health)
        assertEquals(8, boss.maxHealth)
        assertTrue(engine.state.enemies.isEmpty())
    }

    @Test
    fun `player bullets reduce boss health`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            wave = 5,
            enemies = emptyList(),
            boss = OrbitBoss(x = 50f, y = BOSS_Y, health = 8, maxHealth = 8, direction = 1, shootCooldownMs = 60_000L),
            playerBullets = listOf(OrbitBullet(id = 600, x = 50f, y = 30f, velocityY = -95f, fromEnemy = false)),
        )
        engine.tick(16)
        assertEquals(7, engine.state.boss!!.health)
        assertTrue("bullet spent on the hit", engine.state.playerBullets.isEmpty())
    }

    @Test
    fun `boss destroyed pays the bonus, drops a powerup and advances the wave`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            wave = 5,
            enemies = emptyList(),
            boss = OrbitBoss(x = 50f, y = BOSS_Y, health = 1, maxHealth = 8, direction = 1, shootCooldownMs = 60_000L),
            playerBullets = listOf(OrbitBullet(id = 600, x = 50f, y = 30f, velocityY = -95f, fromEnemy = false)),
        )
        engine.tick(16)
        assertNull(engine.state.boss)
        assertEquals(6, engine.state.wave)
        // 300 boss bonus + 100 * cleared wave 5.
        assertEquals(800, engine.state.score)
        assertTrue("wave 6 formation spawned", engine.state.enemies.isNotEmpty())
        // The guaranteed boss drop survives the same-tick wave transition
        // (type roll 0.99 lands past the 60% Shield share = Dash).
        assertEquals(1, engine.state.powerUps.size)
        assertEquals(OrbitPowerUpType.Dash, engine.state.powerUps.single().type)
        assertEquals(1, engine.state.enemiesKilled)
    }

    @Test
    fun `boss shoots on its own cooldown`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            wave = 5,
            enemies = emptyList(),
            boss = OrbitBoss(x = 50f, y = BOSS_Y, health = 8, maxHealth = 8, direction = 1, shootCooldownMs = 0L),
        )
        engine.tick(16)
        assertEquals(1, engine.state.enemyBullets.size)
        assertTrue(engine.state.enemyBullets.single().fromEnemy)
        assertTrue("cooldown re-armed", engine.state.boss!!.shootCooldownMs > 0)
    }

    // --- Powerups -------------------------------------------------------------

    @Test
    fun `destroyed enemy drops a Shield when the rolls say so`() {
        // Drop-gate roll 0f (< 10%), then type roll 0f (< 60% = Shield).
        val engine = OrbitBlasterEngine(randomFloat = { 0f })
        engine.forceForTest(enemies = listOf(OrbitEnemy(id = 100, x = 50f, y = 60f), safeEnemy()))
        engine.shoot()
        tickMany(engine, 60)
        assertEquals(1, engine.state.powerUps.size)
        assertEquals(OrbitPowerUpType.Shield, engine.state.powerUps.single().type)
    }

    @Test
    fun `drop type roll can land on Dash`() {
        // Queue: drop gate 0f, then type 0.7f (>= 60% = Dash).
        val rolls = ArrayDeque(listOf(0f, 0.7f))
        val engine = OrbitBlasterEngine(randomFloat = { rolls.removeFirstOrNull() ?: 0.99f })
        engine.forceForTest(enemies = listOf(OrbitEnemy(id = 100, x = 50f, y = 60f), safeEnemy()))
        engine.shoot()
        tickMany(engine, 60)
        assertEquals(1, engine.state.powerUps.size)
        assertEquals(OrbitPowerUpType.Dash, engine.state.powerUps.single().type)
    }

    @Test
    fun `catching Shield arms the shield window`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            enemies = listOf(safeEnemy()),
            powerUps = listOf(OrbitPowerUp(id = 800, type = OrbitPowerUpType.Shield, x = 50f, y = 117f, velocityY = 20f)),
        )
        engine.tick(16)
        assertEquals(9_000L, engine.state.shieldMs)
        assertTrue(engine.state.powerUps.isEmpty())
    }

    @Test
    fun `catching Dash arms dash and B dashes with cooldown and clamping`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            enemies = listOf(safeEnemy()),
            powerUps = listOf(OrbitPowerUp(id = 800, type = OrbitPowerUpType.Dash, x = 50f, y = 117f, velocityY = 20f)),
        )
        engine.tick(16)
        assertEquals(10_000L, engine.state.dashMs)

        val before = engine.state.playerX
        engine.dash()
        assertEquals(before + 22f, engine.state.playerX, 0.01f)

        val afterFirst = engine.state.playerX
        engine.dash() // still cooling down
        assertEquals(afterFirst, engine.state.playerX, 0.01f)

        // Cooldown over: dashing keeps clamping at the right wall.
        repeat(5) {
            engine.tick(1_300)
            engine.dash()
        }
        assertTrue(engine.state.playerX <= ORBIT_FIELD_WIDTH - PLAYER_WIDTH / 2f + 0.01f)
    }

    @Test
    fun `dash without the powerup is a no-op`() {
        val engine = OrbitBlasterEngine()
        val before = engine.state.playerX
        engine.dash()
        assertEquals(before, engine.state.playerX, 0.01f)
    }

    @Test
    fun `shield window expires on its own`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(enemies = listOf(safeEnemy()), shieldMs = 100L)
        engine.tick(200)
        assertEquals(0L, engine.state.shieldMs)
    }

    @Test
    fun `powerup falling past the bottom is removed uncaught`() {
        val engine = OrbitBlasterEngine(randomFloat = { 0.99f })
        engine.forceForTest(
            enemies = listOf(safeEnemy()),
            powerUps = listOf(OrbitPowerUp(id = 800, type = OrbitPowerUpType.Shield, x = 90f, y = 128f, velocityY = 20f)),
        )
        tickMany(engine, 40)
        assertTrue(engine.state.powerUps.isEmpty())
        assertEquals(0L, engine.state.shieldMs)
    }
}
