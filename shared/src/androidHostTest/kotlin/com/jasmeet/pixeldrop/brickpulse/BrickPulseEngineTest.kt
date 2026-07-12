package com.jasmeet.pixeldrop.brickpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BrickPulseEngineTest {

    private fun tickMany(engine: BrickPulseEngine, times: Int, deltaMs: Long = 16) {
        repeat(times) { engine.tick(deltaMs) }
    }

    @Test
    fun `initial state has 3 lives, level 1, zero score, all bricks alive`() {
        val engine = BrickPulseEngine()
        assertEquals(3, engine.state.lives)
        assertEquals(1, engine.state.level)
        assertEquals(0, engine.state.score)
        assertEquals(BrickPulsePhase.Running, engine.state.phase)
        assertTrue(engine.state.balls.single().attached)
        assertTrue(engine.state.bricks.isNotEmpty())
        assertTrue(engine.state.bricks.all { it.isAlive })
    }

    @Test
    fun `paddle cannot move outside the field bounds`() {
        val engine = BrickPulseEngine()
        val half = engine.state.paddleWidth / 2f

        repeat(50) { engine.movePaddleLeft() }
        assertEquals(half, engine.state.paddleX, 0.01f)

        repeat(100) { engine.movePaddleRight() }
        assertEquals(FIELD_WIDTH - half, engine.state.paddleX, 0.01f)
    }

    @Test
    fun `ball launches upward from the attached state`() {
        val engine = BrickPulseEngine()
        engine.launchBall()
        val ball = engine.state.balls.single()
        assertTrue(!ball.attached)
        assertTrue("expected upward velocity", ball.velocityY < 0f)
    }

    @Test
    fun `ball stays in bounds and eventually bounces off a wall`() {
        val engine = BrickPulseEngine()
        engine.launchBall()
        var vyTurnedDownward = false
        repeat(500) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                tickMany(engine, 1)
                val ball = engine.state.balls.first()
                assertTrue("ballX in bounds", ball.x in 0f..FIELD_WIDTH)
                assertTrue("ballY not far past the field", ball.y <= FIELD_HEIGHT + BALL_SIZE)
                if (ball.velocityY > 0f) vyTurnedDownward = true
            }
        }
        // The top wall (or the paddle) must have reflected the ball at least
        // once for its vertical direction to ever turn positive again.
        assertTrue("expected at least one bounce to reverse vertical direction", vyTurnedDownward)
    }

    @Test
    fun `ball bounces off a paddle actively tracking it`() {
        val engine = BrickPulseEngine()
        engine.launchBall()
        repeat(400) {
            val s = engine.state
            if (s.phase != BrickPulsePhase.Running) return@repeat
            val ballX = s.balls.first().x
            when {
                s.paddleX < ballX - 2f -> engine.movePaddleRight()
                s.paddleX > ballX + 2f -> engine.movePaddleLeft()
            }
            tickMany(engine, 1)
        }
        // With the paddle tracking the ball the whole time, it should never
        // have fallen past — proving every descent was caught (bounced).
        assertEquals(3, engine.state.lives)
        assertEquals(BrickPulsePhase.Running, engine.state.phase)
    }

    @Test
    fun `brick collision removes a brick and increases score`() {
        val engine = BrickPulseEngine()
        engine.launchBall()
        tickMany(engine, 300)
        assertTrue("expected at least one brick broken", engine.state.bricksBroken > 0)
        assertTrue(engine.state.score > 0)
        assertTrue(engine.state.bricks.any { !it.isAlive })
    }

    @Test
    fun `clearing all bricks advances the level and resets the layout`() {
        // A single brick spanning nearly the whole width is effectively
        // unmissable on the way up, making "clear everything" deterministic
        // without needing to simulate a realistic full board.
        val engine = BrickPulseEngine(brickRows = 1, brickCols = 1)
        engine.launchBall()
        var guard = 0
        while (engine.state.level == 1) {
            tickMany(engine, 1)
            guard++
            check(guard < 500) { "level never advanced" }
        }
        assertEquals(2, engine.state.level)
        // 1 brick * 10 * level(1) + 100 * level(1) clear bonus.
        assertEquals(110, engine.state.score)
        assertTrue(engine.state.balls.single().attached)
        assertTrue(engine.state.bricks.all { it.isAlive })
    }

    @Test
    fun `missing the ball decreases a life and reattaches it`() {
        val engine = BrickPulseEngine()
        // Pin the paddle far from the ball's default up-and-rightward path.
        repeat(20) { engine.movePaddleLeft() }
        engine.launchBall()
        var guard = 0
        while (engine.state.lives == 3) {
            tickMany(engine, 1)
            guard++
            check(guard < 2000) { "ball never fell past the paddle" }
        }
        assertEquals(2, engine.state.lives)
        assertEquals(BrickPulsePhase.Running, engine.state.phase)
        assertTrue(engine.state.balls.single().attached)
    }

    @Test
    fun `lives reaching zero triggers game over`() {
        val engine = BrickPulseEngine()
        repeat(20) { engine.movePaddleLeft() } // keep the paddle out of the landing path
        var guard = 0
        while (engine.state.phase != BrickPulsePhase.GameOver) {
            if (engine.state.balls.any { it.attached }) engine.launchBall()
            tickMany(engine, 1)
            guard++
            check(guard < 5000) { "never reached game over" }
        }
        assertEquals(0, engine.state.lives)
        assertEquals(BrickPulsePhase.GameOver, engine.state.phase)
    }

    // --- Levels ---------------------------------------------------------

    @Test
    fun `level 1 and level 2 have different brick layouts`() {
        val level1 = BrickPulseLevels.bricks(BrickPulseLevels.forLevel(1))
        val level2 = BrickPulseLevels.bricks(BrickPulseLevels.forLevel(2))
        assertNotEquals(level1.toSet(), level2.toSet())
    }

    @Test
    fun `strong-line level marks exactly its middle row as strong`() {
        val cells = BrickPulseLevels.bricks(BrickPulseLevels.forLevel(6))
        val strongRows = cells.filter { it.third }.map { it.first }.toSet()
        assertEquals(setOf(2), strongRows)
    }

    @Test
    fun `a strong brick survives one hit and dies on the second`() {
        val strong = Brick(row = 0, col = 0, bounds = BrickRect(0f, 0f, 1f, 1f), maxHits = 2)
        assertTrue(strong.isAlive)
        assertTrue(!strong.isDamaged)

        val damaged = strong.copy(hitsRemaining = strong.hitsRemaining - 1)
        assertTrue(damaged.isAlive)
        assertTrue(damaged.isDamaged)

        val broken = damaged.copy(hitsRemaining = damaged.hitsRemaining - 1)
        assertTrue(!broken.isAlive)
    }

    // --- Powerups ---------------------------------------------------------

    @Test
    fun `breaking a brick can spawn a powerup when the drop roll succeeds`() {
        val engine = BrickPulseEngine(randomFloat = { 0f }) // always drops, always the lowest bucket
        engine.launchBall()
        var spawned = false
        repeat(300) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                engine.tick(16)
                if (engine.state.powerUps.isNotEmpty()) spawned = true
            }
        }
        assertTrue("expected a powerup to spawn from a guaranteed drop roll", spawned)
    }

    @Test
    fun `a spawned powerup falls downward over time`() {
        val engine = BrickPulseEngine(randomFloat = { 0f })
        repeat(20) { engine.movePaddleLeft() } // keep it away from the paddle so it isn't caught
        engine.launchBall()
        var firstY: Float? = null
        var laterY: Float? = null
        repeat(300) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                engine.tick(16)
                val p = engine.state.powerUps.firstOrNull()
                if (p != null) {
                    if (firstY == null) firstY = p.y else laterY = p.y
                }
            }
        }
        assertTrue("expected the powerup to have appeared", firstY != null)
        assertTrue("expected it to have fallen further later", (laterY ?: -1f) > (firstY ?: Float.MAX_VALUE))
    }

    @Test
    fun `paddle catching a powerup applies its effect and removes it`() {
        val engine = BrickPulseEngine(randomFloat = { 0.05f }) // guaranteed WidePaddle drop
        engine.launchBall()
        val baseWidth = engine.state.paddleWidth
        var widened = false
        repeat(400) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                val p = engine.state.powerUps.firstOrNull()
                if (p != null) {
                    when {
                        engine.state.paddleX < p.x - 2f -> engine.movePaddleRight()
                        engine.state.paddleX > p.x + 2f -> engine.movePaddleLeft()
                    }
                }
                engine.tick(16)
                if (engine.state.paddleWidth > baseWidth) widened = true
            }
        }
        assertTrue("expected the paddle to widen after catching WidePaddle", widened)
    }

    @Test
    fun `WidePaddle widens the paddle and expires back to the level width`() {
        val engine = BrickPulseEngine(randomFloat = { 0.05f })
        engine.launchBall()
        repeat(400) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                val p = engine.state.powerUps.firstOrNull()
                if (p != null) {
                    when {
                        engine.state.paddleX < p.x - 2f -> engine.movePaddleRight()
                        engine.state.paddleX > p.x + 2f -> engine.movePaddleLeft()
                    }
                }
                engine.tick(16)
            }
        }
        assertTrue("expected WidePaddle to be active", engine.state.activeEffects.any { it.type == BrickPulsePowerUpType.WidePaddle })

        // Randomfloat is a constant here, so every further brick break would
        // also drop (and re-catch would just refresh the timer) — get the
        // paddle clear first, then jump straight past the duration in one
        // big tick so nothing more can be caught in between.
        repeat(20) { engine.movePaddleLeft() }
        engine.tick(15_000L)
        assertTrue(engine.state.activeEffects.none { it.type == BrickPulsePowerUpType.WidePaddle })
        val expectedWidth = PADDLE_WIDTH * BrickPulseLevels.forLevel(engine.state.level).paddleWidthMultiplier
        assertEquals(expectedWidth, engine.state.paddleWidth, 0.01f)
    }

    @Test
    fun `SlowBall reduces ball speed and expires back to normal`() {
        val rolls = ArrayDeque(listOf(0.02f, 0.9f)) // drop-gate roll, then a type roll landing on SlowBall
        val engine = BrickPulseEngine(randomFloat = { rolls.removeFirstOrNull() ?: 0.9f })
        engine.launchBall()
        val speedBefore = engine.state.balls.first().let { hypot(it.velocityX, it.velocityY) }
        var caughtSpeed: Float? = null
        repeat(500) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                val p = engine.state.powerUps.firstOrNull()
                if (p != null) {
                    when {
                        engine.state.paddleX < p.x - 2f -> engine.movePaddleRight()
                        engine.state.paddleX > p.x + 2f -> engine.movePaddleLeft()
                    }
                }
                engine.tick(16)
                if (caughtSpeed == null && engine.state.activeEffects.any { it.type == BrickPulsePowerUpType.SlowBall }) {
                    caughtSpeed = engine.state.balls.first().let { hypot(it.velocityX, it.velocityY) }
                }
            }
        }
        assertTrue("expected SlowBall to be caught", caughtSpeed != null)
        assertTrue("expected noticeably reduced speed while slowed", (caughtSpeed ?: 0f) < speedBefore * 0.9f)

        // Tick well past the 8s duration to confirm it expires.
        repeat(700) { engine.tick(16) }
        assertTrue(engine.state.activeEffects.none { it.type == BrickPulsePowerUpType.SlowBall })
    }

    @Test
    fun `ExtraLife increases lives, capped at 5`() {
        val engine = BrickPulseEngine(randomFloat = { 0f }) // every break drops, always the lowest bucket
        engine.launchBall()
        repeat(3000) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                if (engine.state.balls.any { it.attached }) engine.launchBall()
                val p = engine.state.powerUps.firstOrNull()
                if (p != null) {
                    when {
                        engine.state.paddleX < p.x - 2f -> engine.movePaddleRight()
                        engine.state.paddleX > p.x + 2f -> engine.movePaddleLeft()
                    }
                }
                engine.tick(16)
            }
        }
        assertTrue("expected at least one ExtraLife catch", engine.state.lives > 3)
        assertTrue("lives must never exceed the cap", engine.state.lives <= 5)
    }

    @Test
    fun `a powerup falling past the paddle is removed`() {
        val engine = BrickPulseEngine(randomFloat = { 0f })
        repeat(20) { engine.movePaddleLeft() } // keep it away so it's never caught
        engine.launchBall()
        var sawPowerUp = false
        // Generous budget: the ball needs time to reach the wall, a powerup
        // falls slowly (18 units/sec over ~100 units), and with a pinned
        // paddle the ball itself eventually misses too (life lost, ball
        // reattached, no more hits) well before this runs out.
        repeat(3000) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                engine.tick(16)
                if (engine.state.powerUps.isNotEmpty()) sawPowerUp = true
            }
        }
        assertTrue("expected the powerup to have appeared at some point", sawPowerUp)
        assertTrue("expected it to be gone (missed) by the end", engine.state.powerUps.isEmpty())
    }

    // --- MultiBall ---------------------------------------------------------

    @Test
    fun `catching MultiBall spawns a second ball with a different horizontal velocity`() {
        // First break: drop-gate roll 0.02 (below every level's chance), then
        // type roll 0.5 (the MultiBall bucket); afterwards 0.9 fails every
        // later drop gate so no second powerup muddies the run.
        val rolls = ArrayDeque(listOf(0.02f, 0.5f))
        val engine = BrickPulseEngine(randomFloat = { rolls.removeFirstOrNull() ?: 0.9f })
        engine.launchBall()
        var separationChecked = false
        repeat(600) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                val p = engine.state.powerUps.firstOrNull()
                if (p != null) {
                    when {
                        engine.state.paddleX < p.x - 2f -> engine.movePaddleRight()
                        engine.state.paddleX > p.x + 2f -> engine.movePaddleLeft()
                    }
                }
                engine.tick(16)
                if (!separationChecked && engine.state.balls.size == 2) {
                    separationChecked = true
                    val (a, b) = engine.state.balls
                    assertTrue("expected differing x velocities so the pair separates", a.velocityX != b.velocityX)
                }
            }
        }
        assertTrue("expected the caught MultiBall to have spawned a second ball", separationChecked)
    }

    @Test
    fun `MultiBall never pushes the ball count past two`() {
        // Alternating rolls: every brick break passes the drop gate (0.02)
        // and lands in the MultiBall bucket (0.5), so catches keep coming.
        var i = 0
        val engine = BrickPulseEngine(randomFloat = { if (i++ % 2 == 0) 0.02f else 0.5f })
        var sawTwo = false
        repeat(1500) {
            if (engine.state.phase == BrickPulsePhase.Running) {
                if (engine.state.balls.any { it.attached }) engine.launchBall()
                val p = engine.state.powerUps.firstOrNull()
                if (p != null) {
                    when {
                        engine.state.paddleX < p.x - 2f -> engine.movePaddleRight()
                        engine.state.paddleX > p.x + 2f -> engine.movePaddleLeft()
                    }
                }
                engine.tick(16)
                assertTrue("ball count must never exceed 2", engine.state.balls.size <= 2)
                if (engine.state.balls.size == 2) sawTwo = true
            }
        }
        assertTrue("expected to reach two balls at least once", sawTwo)
    }

    @Test
    fun `losing one of two balls keeps lives and the other ball`() {
        val engine = BrickPulseEngine()
        // One ball rising safely mid-field, one about to fall far from the
        // paddle (default paddle spans x 37..63, so x=10 can't be saved).
        engine.forceForTest(
            balls = listOf(
                BrickPulseBall(id = 100, x = 50f, y = 60f, velocityX = 0f, velocityY = -40f),
                BrickPulseBall(id = 101, x = 10f, y = FIELD_HEIGHT - 1f, velocityX = 0f, velocityY = 60f),
            ),
        )
        repeat(20) { engine.tick(16) }
        assertEquals(3, engine.state.lives)
        assertEquals(1, engine.state.balls.size)
        assertEquals(100, engine.state.balls.single().id)
    }

    @Test
    fun `losing all balls costs one life and resets a single attached ball`() {
        val engine = BrickPulseEngine()
        engine.forceForTest(
            balls = listOf(
                BrickPulseBall(id = 100, x = 10f, y = FIELD_HEIGHT - 1f, velocityX = 0f, velocityY = 60f),
                BrickPulseBall(id = 101, x = 90f, y = FIELD_HEIGHT - 1f, velocityX = 0f, velocityY = 60f),
            ),
        )
        repeat(20) { engine.tick(16) }
        assertEquals(2, engine.state.lives)
        assertEquals(BrickPulsePhase.Running, engine.state.phase)
        assertTrue(engine.state.balls.single().attached)
    }

    @Test
    fun `losing all balls on the last life is game over with no balls left`() {
        val engine = BrickPulseEngine()
        engine.forceForTest(
            balls = listOf(
                BrickPulseBall(id = 100, x = 10f, y = FIELD_HEIGHT - 1f, velocityX = 0f, velocityY = 60f),
                BrickPulseBall(id = 101, x = 90f, y = FIELD_HEIGHT - 1f, velocityX = 0f, velocityY = 60f),
            ),
            lives = 1,
        )
        repeat(20) { engine.tick(16) }
        assertEquals(0, engine.state.lives)
        assertEquals(BrickPulsePhase.GameOver, engine.state.phase)
        assertTrue("no balls may keep moving after game over", engine.state.balls.isEmpty())
        assertTrue(engine.isGameOver())
    }

    @Test
    fun `two balls can each break a brick in the same tick`() {
        // 2x2 override grid: row 1 bricks span y 22..28 (x 6..49 and 51..94).
        val engine = BrickPulseEngine(brickRows = 2, brickCols = 2)
        engine.forceForTest(
            balls = listOf(
                BrickPulseBall(id = 100, x = 27f, y = 30f, velocityX = 0f, velocityY = -60f),
                BrickPulseBall(id = 101, x = 72f, y = 30f, velocityX = 0f, velocityY = -60f),
            ),
        )
        engine.tick(16)
        assertEquals(2, engine.state.bricks.count { !it.isAlive })
        assertEquals(20, engine.state.score) // two normal bricks at level 1
        assertEquals(2, engine.state.bricksBroken)
    }

    @Test
    fun `two balls hitting the same brick in one tick break it only once`() {
        val engine = BrickPulseEngine(brickRows = 2, brickCols = 2)
        // Both balls under brick (1,0), which spans x 6..49.
        engine.forceForTest(
            balls = listOf(
                BrickPulseBall(id = 100, x = 20f, y = 30f, velocityX = 0f, velocityY = -60f),
                BrickPulseBall(id = 101, x = 34f, y = 30f, velocityX = 0f, velocityY = -60f),
            ),
        )
        engine.tick(16)
        assertEquals(1, engine.state.bricks.count { !it.isAlive })
        assertEquals(10, engine.state.score)
        assertEquals(1, engine.state.bricksBroken)
    }

    @Test
    fun `clearing the level with two balls resets to a single attached ball`() {
        // 1x2 override grid: both bricks span y 14..20; kill both in one tick.
        val engine = BrickPulseEngine(brickRows = 1, brickCols = 2)
        engine.forceForTest(
            balls = listOf(
                BrickPulseBall(id = 100, x = 27f, y = 22f, velocityX = 0f, velocityY = -60f),
                BrickPulseBall(id = 101, x = 72f, y = 22f, velocityX = 0f, velocityY = -60f),
            ),
        )
        engine.tick(16)
        assertEquals(2, engine.state.level)
        // 2 bricks * 10 * level(1) + 100 * level(1) clear bonus.
        assertEquals(120, engine.state.score)
        assertTrue(engine.state.balls.single().attached)
        assertTrue(engine.state.bricks.all { it.isAlive })
    }
}
