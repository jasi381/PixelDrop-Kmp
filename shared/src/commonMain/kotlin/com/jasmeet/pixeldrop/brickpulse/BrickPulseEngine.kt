package com.jasmeet.pixeldrop.brickpulse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Continuous-physics paddle-ball engine, independent of any UI drawing.
 * Position/velocity live in the fixed [FIELD_WIDTH]x[FIELD_HEIGHT] space;
 * [tick] is driven by a frame loop with a real delta so movement stays
 * smooth regardless of the caller's frame rate.
 *
 * [brickRows]/[brickCols] bypass the level-pattern system
 * entirely in favor of one plain full grid — only ever used by tests that
 * want a small, unmissable board and exact legacy speed/width numbers
 * (no level multiplier, no powerup drops) rather than the real layouts.
 * [randomFloat] is powerup-drop RNG, injectable for deterministic tests.
 */
class BrickPulseEngine(
    private val brickRows: Int? = null,
    private val brickCols: Int? = null,
    private val randomFloat: () -> Float = { Random.Default.nextFloat() },
) {
    // Declared before [state] so initialState() can mint the first ball's id
    // (Kotlin runs property initializers in declaration order).
    private var nextBallId = 0

    var state: BrickPulseState by mutableStateOf(initialState())
        private set

    private var nextPowerUpId = 0
    private val isOverride = brickRows != null || brickCols != null

    // The D-pad only ever delivers discrete pulses (an initial tap, then a
    // ~110ms auto-repeat while held — see Controls.kt's consolePressable) —
    // there's no raw press/release signal to drive a continuous velocity
    // from inside tick(). So the paddle moves immediately by a fixed step
    // per call, exactly like PIXEL DROP's own moveLeft()/moveRight().
    fun movePaddleLeft() = nudgePaddle(-PADDLE_STEP)

    fun movePaddleRight() = nudgePaddle(PADDLE_STEP)

    private fun nudgePaddle(delta: Float) {
        if (state.phase != BrickPulsePhase.Running) return
        val half = state.paddleWidth / 2f
        val newX = (state.paddleX + delta).coerceIn(half, FIELD_WIDTH - half)
        state = state.copy(
            paddleX = newX,
            balls = state.balls.map { if (it.attached) it.copy(x = newX) else it },
        )
    }

    fun launchBall() {
        if (state.phase != BrickPulsePhase.Running || state.balls.none { it.attached }) return
        var speed = baseSpeedForLevel(state.level)
        if (state.activeEffects.any { it.type == BrickPulsePowerUpType.SlowBall }) speed *= SLOW_BALL_MULTIPLIER
        state = state.copy(
            balls = state.balls.map {
                if (it.attached) it.copy(attached = false, velocityX = speed * 0.35f, velocityY = -speed * 0.94f) else it
            },
        )
    }

    fun pause() {
        if (state.phase == BrickPulsePhase.Running) state = state.copy(phase = BrickPulsePhase.Paused)
    }

    fun resume() {
        if (state.phase == BrickPulsePhase.Paused) state = state.copy(phase = BrickPulsePhase.Running)
    }

    fun reset() {
        state = initialState()
    }

    fun isGameOver(): Boolean = state.phase == BrickPulsePhase.GameOver

    fun tick(deltaMs: Long) {
        if (state.phase != BrickPulsePhase.Running) return
        val dt = deltaMs / 1000f
        val s = state
        val levelDef = levelDefFor(s.level)

        // Paddle width is re-derived every tick from the level's own
        // multiplier and whichever timed effects are active (rather than
        // patched at each mutation site), so WidePaddle's expiry and the
        // per-level multiplier both just fall out of this one place.
        val agedActiveEffects = s.activeEffects
            .map { it.copy(remainingMs = it.remainingMs - deltaMs) }
            .filter { it.remainingMs > 0 }
        val wideActive = agedActiveEffects.any { it.type == BrickPulsePowerUpType.WidePaddle }
        val paddleWidth = PADDLE_WIDTH * levelDef.paddleWidthMultiplier * (if (wideActive) WIDE_PADDLE_MULTIPLIER else 1f)
        val paddleHalf = paddleWidth / 2f
        val paddleX = s.paddleX.coerceIn(paddleHalf, FIELD_WIDTH - paddleHalf)

        val agedSparks = s.effects.map { it.copy(age = it.age + dt) }.filter { it.age < it.maxAge }

        val powerUpResult = advancePowerUps(s.powerUps, dt, paddleX, paddleHalf, s.lives, agedActiveEffects)

        // A SlowBall catch or expiry (either direction, in this same tick)
        // rescales each ball's current speed once rather than fighting the
        // paddle-bounce math's own speed-preserving logic every frame.
        val wasSlowBall = s.activeEffects.any { it.type == BrickPulsePowerUpType.SlowBall }
        val isSlowBallNow = powerUpResult.effects.any { it.type == BrickPulsePowerUpType.SlowBall }
        val slowScale = when {
            isSlowBallNow && !wasSlowBall -> SLOW_BALL_MULTIPLIER
            wasSlowBall && !isSlowBallNow -> 1f / SLOW_BALL_MULTIPLIER
            else -> 1f
        }

        val half = BALL_SIZE / 2f
        var bricks = s.bricks
        var scoreGain = 0
        var newSparks = agedSparks
        var bricksBroken = s.bricksBroken
        var newPowerUps = powerUpResult.remaining
        val survivors = mutableListOf<BrickPulseBall>()

        for (ball in s.balls) {
            if (ball.attached) {
                survivors += ball.copy(x = paddleX)
                continue
            }
            var vx = ball.velocityX * slowScale
            var vy = ball.velocityY * slowScale
            var bx = ball.x + vx * dt
            var by = ball.y + vy * dt

            // Wall collisions: left/right/top.
            if (bx - half < 0f) {
                bx = half
                vx = -vx
            } else if (bx + half > FIELD_WIDTH) {
                bx = FIELD_WIDTH - half
                vx = -vx
            }
            if (by - half < 0f) {
                by = half
                vy = -vy
            }

            // Paddle collision: only while descending into its band.
            if (vy > 0f &&
                by + half >= PADDLE_Y && by - half <= PADDLE_Y + PADDLE_HEIGHT &&
                bx + half >= paddleX - paddleHalf && bx - half <= paddleX + paddleHalf
            ) {
                by = PADDLE_Y - half
                // -1 (left edge) .. 0 (center) .. +1 (right edge); redirects the
                // angle while preserving total speed so bounces never accelerate.
                val hit = ((bx - paddleX) / paddleHalf).coerceIn(-1f, 1f)
                val speed = hypot(vx, vy)
                vx = hit * speed * 0.8f
                vy = -sqrt((speed * speed - vx * vx).coerceAtLeast(0f))
            }

            // Brick collision — at most one per ball per tick, kept simple per
            // the MVP. Balls run sequentially against the same evolving list,
            // so two balls can never double-kill one brick, double-count its
            // score, or drive a strong brick's durability negative.
            val hitIndex = bricks.indexOfFirst { it.isAlive && ballIntersects(bx, by, half, it.bounds) }
            if (hitIndex >= 0) {
                val brick = bricks[hitIndex]
                val hit = brick.copy(hitsRemaining = brick.hitsRemaining - 1)
                bricks = bricks.toMutableList().also { it[hitIndex] = hit }
                vy = -vy
                scoreGain += 10 * s.level
                val cx = (brick.bounds.left + brick.bounds.right) / 2f
                val cy = (brick.bounds.top + brick.bounds.bottom) / 2f
                newSparks = newSparks + BrickPulseEffect(x = cx, y = cy, age = 0f, maxAge = EFFECT_LIFETIME_SECONDS)
                if (!hit.isAlive) {
                    bricksBroken++
                    if (randomFloat() < levelDef.powerUpDropChance) {
                        newPowerUps = newPowerUps + BrickPulsePowerUp(
                            id = nextPowerUpId++,
                            type = rollPowerUpType(),
                            x = cx, y = cy, velocityY = POWERUP_FALL_SPEED,
                        )
                    }
                }
            }

            // Fell past the paddle — only this ball is lost; a life goes only
            // once the whole list is empty (checked after the loop).
            if (by - half > FIELD_HEIGHT) continue

            survivors += ball.copy(x = bx, y = by, velocityX = vx, velocityY = vy)
        }

        // MultiBall: clone the first moving survivor with mirrored horizontal
        // velocity so the pair separates naturally. Caught at the ball cap, or
        // with nothing in flight (all balls attached — a launch would then put
        // two balls on one trajectory), it is consumed with no effect.
        repeat(powerUpResult.multiBallCatches) {
            if (survivors.size >= MAX_BALLS) return@repeat
            val source = survivors.firstOrNull { !it.attached } ?: return@repeat
            val newVx = if (source.velocityX != 0f) -source.velocityX else baseSpeedForLevel(s.level) * 0.35f
            survivors += BrickPulseBall(
                id = nextBallId++,
                x = source.x, y = source.y,
                velocityX = newVx, velocityY = source.velocityY,
            )
        }

        // Missed — every ball fell past the paddle; that's when a life goes.
        if (survivors.isEmpty()) {
            val livesLeft = powerUpResult.lives - 1
            state = if (livesLeft <= 0) {
                s.copy(
                    lives = 0, phase = BrickPulsePhase.GameOver,
                    balls = emptyList(),
                    paddleX = paddleX, paddleWidth = paddleWidth,
                    bricks = bricks, score = s.score + scoreGain,
                    effects = newSparks, powerUps = newPowerUps, activeEffects = powerUpResult.effects,
                    bricksBroken = bricksBroken,
                )
            } else {
                s.copy(
                    lives = livesLeft,
                    balls = listOf(newAttachedBall(paddleX)),
                    paddleX = paddleX, paddleWidth = paddleWidth,
                    bricks = bricks, score = s.score + scoreGain,
                    effects = newSparks, powerUps = newPowerUps, activeEffects = powerUpResult.effects,
                    bricksBroken = bricksBroken,
                )
            }
            return
        }

        state = if (bricks.none { it.isAlive }) {
            nextLevel(
                s.copy(
                    bricks = bricks,
                    score = s.score + scoreGain + LEVEL_CLEAR_BONUS_PER_LEVEL * s.level,
                    paddleX = paddleX, paddleWidth = paddleWidth,
                    effects = newSparks, bricksBroken = bricksBroken,
                    lives = powerUpResult.lives,
                )
            )
        } else {
            s.copy(
                balls = survivors,
                paddleX = paddleX, paddleWidth = paddleWidth,
                bricks = bricks,
                score = s.score + scoreGain,
                effects = newSparks,
                powerUps = newPowerUps,
                activeEffects = powerUpResult.effects,
                lives = powerUpResult.lives,
                bricksBroken = bricksBroken,
            )
        }
    }

    /** Test-only escape hatch: pins exact ball/lives situations (two balls
     *  about to fall on the last life, balls poised under specific bricks)
     *  that honest play can't set up deterministically. */
    internal fun forceForTest(balls: List<BrickPulseBall>? = null, lives: Int? = null) {
        state = state.copy(balls = balls ?: state.balls, lives = lives ?: state.lives)
    }

    private data class PowerUpTickResult(
        val remaining: List<BrickPulsePowerUp>,
        val effects: List<BrickPulseActiveEffect>,
        val lives: Int,
        val multiBallCatches: Int,
    )

    /** Falls, catches, and expires powerups against the paddle for one tick. */
    private fun advancePowerUps(
        powerUps: List<BrickPulsePowerUp>,
        dt: Float,
        paddleX: Float,
        paddleHalf: Float,
        lives: Int,
        activeEffects: List<BrickPulseActiveEffect>,
    ): PowerUpTickResult {
        var effects = activeEffects
        var newLives = lives
        var multiBallCatches = 0
        val remaining = mutableListOf<BrickPulsePowerUp>()
        val half = POWERUP_SIZE / 2f
        for (powerUp in powerUps) {
            val y = powerUp.y + powerUp.velocityY * dt
            val caught = y + half >= PADDLE_Y && y - half <= PADDLE_Y + PADDLE_HEIGHT &&
                powerUp.x + half >= paddleX - paddleHalf && powerUp.x - half <= paddleX + paddleHalf
            when {
                caught -> when (powerUp.type) {
                    BrickPulsePowerUpType.ExtraLife -> newLives = (newLives + 1).coerceAtMost(MAX_LIVES)
                    BrickPulsePowerUpType.WidePaddle ->
                        effects = replaceEffect(effects, BrickPulsePowerUpType.WidePaddle, WIDE_PADDLE_DURATION_MS)
                    BrickPulsePowerUpType.SlowBall ->
                        effects = replaceEffect(effects, BrickPulsePowerUpType.SlowBall, SLOW_BALL_DURATION_MS)
                    // Spawning needs the post-physics ball list, so tick()
                    // applies the catch after its ball loop.
                    BrickPulsePowerUpType.MultiBall -> multiBallCatches++
                }
                y - half > FIELD_HEIGHT -> Unit // fell past the paddle — dropped, no penalty
                else -> remaining += powerUp.copy(y = y)
            }
        }
        return PowerUpTickResult(remaining, effects, newLives, multiBallCatches)
    }

    private fun replaceEffect(
        effects: List<BrickPulseActiveEffect>,
        type: BrickPulsePowerUpType,
        durationMs: Long,
    ): List<BrickPulseActiveEffect> = effects.filterNot { it.type == type } + BrickPulseActiveEffect(type, durationMs)

    /** WidePaddle/SlowBall dominate the pool; MultiBall is an occasional
     *  treat and ExtraLife is deliberately rare. */
    private fun rollPowerUpType(): BrickPulsePowerUpType {
        val roll = randomFloat()
        val wideShare = (1f - EXTRA_LIFE_SHARE - MULTI_BALL_SHARE) / 2f
        return when {
            roll < EXTRA_LIFE_SHARE -> BrickPulsePowerUpType.ExtraLife
            roll < EXTRA_LIFE_SHARE + wideShare -> BrickPulsePowerUpType.WidePaddle
            roll < EXTRA_LIFE_SHARE + wideShare + MULTI_BALL_SHARE -> BrickPulsePowerUpType.MultiBall
            else -> BrickPulsePowerUpType.SlowBall
        }
    }

    private fun newAttachedBall(x: Float): BrickPulseBall = BrickPulseBall(
        id = nextBallId++,
        x = x, y = PADDLE_Y - BALL_SIZE / 2f,
        velocityX = 0f, velocityY = 0f,
        attached = true,
    )

    private fun nextLevel(cleared: BrickPulseState): BrickPulseState {
        val level = cleared.level + 1
        return cleared.copy(
            level = level,
            bricks = freshBricks(level),
            // Any MultiBall extras end with the level — a clean slate, same
            // as buffs below.
            balls = listOf(newAttachedBall(cleared.paddleX)),
            // A fresh level is a clean slate rather than carrying a buff
            // whose duration was balanced against the previous layout.
            powerUps = emptyList(),
            activeEffects = emptyList(),
        )
    }

    private fun levelDefFor(levelNumber: Int): BrickPulseLevel =
        if (isOverride) NEUTRAL_LEVEL.copy(number = levelNumber) else BrickPulseLevels.forLevel(levelNumber)

    private fun baseSpeedForLevel(levelNumber: Int): Float {
        val levelDef = levelDefFor(levelNumber)
        return (BASE_BALL_SPEED * SPEED_GROWTH_PER_LEVEL.pow(levelNumber - 1) * levelDef.ballSpeedMultiplier)
            .coerceAtMost(MAX_BALL_SPEED)
    }

    private fun initialState(): BrickPulseState = BrickPulseState(
        score = 0,
        lives = LIVES_START,
        level = 1,
        paddleX = FIELD_WIDTH / 2f,
        paddleWidth = PADDLE_WIDTH,
        balls = listOf(newAttachedBall(FIELD_WIDTH / 2f)),
        bricks = freshBricks(1),
        effects = emptyList(),
        phase = BrickPulsePhase.Running,
    )

    private fun freshBricks(levelNumber: Int): List<Brick> {
        val overrideRows = brickRows
        val overrideCols = brickCols
        val specs: List<Triple<Int, Int, Boolean>>
        val cols: Int
        if (overrideRows != null && overrideCols != null) {
            cols = overrideCols
            specs = (0 until overrideRows).flatMap { row -> (0 until overrideCols).map { col -> Triple(row, col, false) } }
        } else {
            cols = BrickPulseLevels.GRID_COLS
            specs = BrickPulseLevels.bricks(levelDefFor(levelNumber))
        }
        val marginX = 6f
        val marginTop = 14f
        val gap = 2f
        val brickW = (FIELD_WIDTH - marginX * 2 - gap * (cols - 1)) / cols
        val brickH = 6f
        return specs.map { (row, col, strong) ->
            val left = marginX + col * (brickW + gap)
            val top = marginTop + row * (brickH + gap)
            val maxHits = if (strong) 2 else 1
            Brick(row = row, col = col, bounds = BrickRect(left, top, left + brickW, top + brickH), maxHits = maxHits)
        }
    }

    private fun ballIntersects(bx: Float, by: Float, half: Float, r: BrickRect): Boolean =
        bx + half >= r.left && bx - half <= r.right && by + half >= r.top && by - half <= r.bottom

    private companion object {
        val NEUTRAL_LEVEL = BrickPulseLevel(1, BrickPattern.ClassicWall, 1f, 1f, 0f, false)
        const val LIVES_START = 3
        const val MAX_LIVES = 5
        const val PADDLE_STEP = 9f
        const val BASE_BALL_SPEED = 58f
        const val SPEED_GROWTH_PER_LEVEL = 1.07f
        const val MAX_BALL_SPEED = 100f
        const val LEVEL_CLEAR_BONUS_PER_LEVEL = 100
        const val EFFECT_LIFETIME_SECONDS = 0.25f
        const val POWERUP_FALL_SPEED = 18f
        const val WIDE_PADDLE_MULTIPLIER = 1.5f
        const val WIDE_PADDLE_DURATION_MS = 10_000L
        const val SLOW_BALL_MULTIPLIER = 0.78f
        const val SLOW_BALL_DURATION_MS = 8_000L
        const val EXTRA_LIFE_SHARE = 0.03f
        const val MULTI_BALL_SHARE = 0.2f
        const val MAX_BALLS = 2
    }
}
