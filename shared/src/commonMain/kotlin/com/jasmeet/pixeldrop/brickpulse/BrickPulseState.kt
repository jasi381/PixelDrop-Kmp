package com.jasmeet.pixeldrop.brickpulse

/** Engine's own rect type — kept free of any Compose/Android dependency. */
data class BrickRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class Brick(
    val row: Int,
    val col: Int,
    val bounds: BrickRect,
    /** 1 = normal brick, 2 = strong brick. */
    val maxHits: Int = 1,
    val hitsRemaining: Int = maxHits,
) {
    val isAlive: Boolean get() = hitsRemaining > 0
    val isDamaged: Boolean get() = isAlive && hitsRemaining < maxHits
}

/** A short-lived break spark; [age] grows toward [maxAge] each tick and the
 *  renderer/engine drop it once it expires. Both in seconds. */
data class BrickPulseEffect(val x: Float, val y: Float, val age: Float, val maxAge: Float)

enum class BrickPulsePhase { Running, Paused, GameOver }

enum class BrickPulsePowerUpType { WidePaddle, SlowBall, ExtraLife, MultiBall }

data class BrickPulsePowerUp(
    val id: Int,
    val type: BrickPulsePowerUpType,
    val x: Float,
    val y: Float,
    val velocityY: Float,
)

/** A timed buff currently in effect; [remainingMs] counts down each tick and
 *  the engine removes (and un-applies) it once it hits zero. */
data class BrickPulseActiveEffect(val type: BrickPulsePowerUpType, val remainingMs: Long)

/** One in-flight (or paddle-attached) ball. BRICK PULSE runs a single ball
 *  normally; the MultiBall powerup raises that to two. */
data class BrickPulseBall(
    val id: Int,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val attached: Boolean = false,
)

data class BrickPulseState(
    val score: Int,
    val lives: Int,
    val level: Int,
    val paddleX: Float,
    val paddleWidth: Float,
    /** Never empty while Running — life loss, level clear, and restart all
     *  rebuild exactly one attached ball; only GameOver empties the list. */
    val balls: List<BrickPulseBall>,
    val bricks: List<Brick>,
    val effects: List<BrickPulseEffect>,
    val powerUps: List<BrickPulsePowerUp> = emptyList(),
    val activeEffects: List<BrickPulseActiveEffect> = emptyList(),
    /** Monotonic across the whole game (survives level clears); lets the UI
     *  layer fire a one-shot chime per brick without watching [effects]. */
    val bricksBroken: Int = 0,
    val phase: BrickPulsePhase = BrickPulsePhase.Running,
)

/** Playfield is a fixed normalized coordinate space; the renderer scales it
 *  uniformly (see other games' `min(...)` field-fit pattern) to whatever
 *  pixel rect the LCD actually gives it, so physics never depends on the
 *  real screen size. */
const val FIELD_WIDTH = 100f
const val FIELD_HEIGHT = 130f
const val PADDLE_WIDTH = 26f
const val PADDLE_HEIGHT = 4f
const val PADDLE_Y = FIELD_HEIGHT - 10f
const val BALL_SIZE = 3f
const val POWERUP_SIZE = 6f
const val BRICK_ROWS = 4
const val BRICK_COLS = 6
