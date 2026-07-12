package com.jasmeet.pixeldrop.meteordodge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

/**
 * One-hit falling-obstacle survival engine, independent of any UI drawing.
 * Positions/velocities live in the fixed [METEOR_FIELD_WIDTH] x
 * [METEOR_FIELD_HEIGHT] space; [tick] runs off a frame loop with a real
 * delta, same as BRICK PULSE and ORBIT BLASTER.
 *
 * [randomFloat] feeds spawn positions, meteor variants and bonus typing;
 * injectable for deterministic tests.
 */
class MeteorDodgeEngine(
    private val randomFloat: () -> Float = { Random.Default.nextFloat() },
) {
    private var nextMeteorId = 0
    private var nextBonusId = 0

    var state: MeteorDodgeState by mutableStateOf(initialState())
        private set

    fun moveLeft() = nudgePlayer(dx = -PLAYER_STEP)

    fun moveRight() = nudgePlayer(dx = PLAYER_STEP)

    fun moveUp() = nudgePlayer(dy = -VERTICAL_STEP)

    fun moveDown() = nudgePlayer(dy = VERTICAL_STEP)

    private fun nudgePlayer(dx: Float = 0f, dy: Float = 0f) {
        if (state.phase != MeteorDodgePhase.Running) return
        val half = METEOR_PLAYER_WIDTH / 2f
        state = state.copy(
            playerX = (state.playerX + dx).coerceIn(half, METEOR_FIELD_WIDTH - half),
            playerY = (state.playerY + dy).coerceIn(METEOR_PLAYER_MIN_Y, METEOR_PLAYER_MAX_Y),
            lastMoveDirection = if (dx < 0f) -1 else if (dx > 0f) 1 else state.lastMoveDirection,
        )
    }

    /** Instant sideways burst in the last movement direction; always
     *  available, rate-limited by its own cooldown. Not an i-frame — a
     *  dash into a rock still ends the run. */
    fun dash() {
        val s = state
        if (s.phase != MeteorDodgePhase.Running || s.dashCooldownMs > 0) return
        val half = METEOR_PLAYER_WIDTH / 2f
        state = s.copy(
            playerX = (s.playerX + DASH_DISTANCE * s.lastMoveDirection).coerceIn(half, METEOR_FIELD_WIDTH - half),
            dashCooldownMs = DASH_COOLDOWN_MS,
        )
    }

    fun pause() {
        if (state.phase == MeteorDodgePhase.Running) state = state.copy(phase = MeteorDodgePhase.Paused)
    }

    fun resume() {
        if (state.phase == MeteorDodgePhase.Paused) state = state.copy(phase = MeteorDodgePhase.Running)
    }

    fun reset() {
        state = initialState()
    }

    fun isGameOver(): Boolean = state.phase == MeteorDodgePhase.GameOver

    fun tick(deltaMs: Long) {
        val s = state
        if (s.phase != MeteorDodgePhase.Running) return
        val dt = deltaMs / 1000f

        val survivedMs = s.survivedMs + deltaMs
        val speedLevel = (1 + survivedMs / SPEED_LEVEL_EVERY_MS).toInt().coerceAtMost(MAX_SPEED_LEVEL)
        val dashCooldown = (s.dashCooldownMs - deltaMs).coerceAtLeast(0)

        // Survival score: +1 per 100ms (reads as +10 per second) with the
        // sub-slice remainder carried so no time is ever lost to rounding.
        var carry = s.scoreCarryMs + deltaMs
        var scoreGain = (carry / SCORE_SLICE_MS).toInt()
        carry %= SCORE_SLICE_MS

        // Meteors fall and lean sideways; the lean bounces off the walls.
        var meteors = s.meteors.mapNotNull { m ->
            var x = m.x + m.driftX * dt
            var driftX = m.driftX
            if (x < m.radius) {
                x = m.radius
                driftX = -driftX
            } else if (x > METEOR_FIELD_WIDTH - m.radius) {
                x = METEOR_FIELD_WIDTH - m.radius
                driftX = -driftX
            }
            val y = m.y + m.velocityY * dt
            if (y - m.radius > METEOR_FIELD_HEIGHT) null else m.copy(x = x, y = y, driftX = driftX)
        }

        // Bonuses fall; the ship catches them by overlap, the floor eats them.
        var shieldActive = s.shieldActive
        var bonusesCollected = s.bonusesCollected
        val bonuses = mutableListOf<MeteorBonus>()
        for (bonus in s.bonuses) {
            val y = bonus.y + bonus.velocityY * dt
            val caught = overlaps(
                bonus.x, y, METEOR_BONUS_SIZE, METEOR_BONUS_SIZE,
                s.playerX, s.playerY, METEOR_PLAYER_WIDTH, METEOR_PLAYER_HEIGHT,
            )
            when {
                caught -> {
                    bonusesCollected++
                    when (bonus.type) {
                        MeteorBonusType.Score -> scoreGain += BONUS_SCORE
                        MeteorBonusType.Shield -> shieldActive = true
                    }
                }
                y - METEOR_BONUS_SIZE / 2f > METEOR_FIELD_HEIGHT -> Unit
                else -> bonuses += bonus.copy(y = y)
            }
        }

        // Spawning. Interval shrinks with the speed level; each rock rolls
        // its own position, size variant, fall speed wobble and lean.
        var spawnTimer = s.spawnTimerMs - deltaMs
        if (spawnTimer <= 0) {
            spawnTimer = spawnIntervalForLevel(speedLevel)
            val radius = if (randomFloat() < LARGE_METEOR_SHARE) METEOR_RADIUS_LARGE else METEOR_RADIUS_SMALL
            val x = radius + randomFloat() * (METEOR_FIELD_WIDTH - radius * 2)
            val speed = meteorSpeedForLevel(speedLevel) * (0.8f + randomFloat() * 0.4f)
            meteors = meteors + Meteor(
                id = nextMeteorId++,
                x = x, y = -radius,
                radius = radius,
                velocityY = speed,
                driftX = (randomFloat() - 0.5f) * METEOR_MAX_DRIFT,
            )
        }

        var bonusSpawnTimer = s.bonusSpawnTimerMs - deltaMs
        if (bonusSpawnTimer <= 0) {
            val x = METEOR_BONUS_SIZE + randomFloat() * (METEOR_FIELD_WIDTH - METEOR_BONUS_SIZE * 2)
            // Never spawn a pickup inside a rock; retry shortly instead.
            val blocked = meteors.any {
                overlaps(x, -METEOR_BONUS_SIZE, METEOR_BONUS_SIZE, METEOR_BONUS_SIZE, it.x, it.y, it.radius * 2, it.radius * 2)
            }
            if (blocked) {
                bonusSpawnTimer = BONUS_RETRY_MS
            } else {
                bonusSpawnTimer = BONUS_SPAWN_EVERY_MS
                val type = if (randomFloat() < SHIELD_BONUS_SHARE) MeteorBonusType.Shield else MeteorBonusType.Score
                bonuses += MeteorBonus(
                    id = nextBonusId++,
                    type = type,
                    x = x, y = -METEOR_BONUS_SIZE,
                    velocityY = BONUS_FALL_SPEED,
                )
            }
        }

        // Collision: hitbox a touch smaller than the drawn ship. A shield
        // eats the rock (and itself); otherwise the run is over.
        var phase = s.phase
        val hit = meteors.firstOrNull {
            overlaps(it.x, it.y, it.radius * 2, it.radius * 2, s.playerX, s.playerY, PLAYER_HITBOX_WIDTH, PLAYER_HITBOX_HEIGHT)
        }
        if (hit != null) {
            if (shieldActive) {
                shieldActive = false
                meteors = meteors - hit
            } else {
                phase = MeteorDodgePhase.GameOver
            }
        }

        state = s.copy(
            score = s.score + scoreGain,
            survivedMs = survivedMs,
            speedLevel = speedLevel,
            meteors = meteors,
            bonuses = bonuses,
            dashCooldownMs = dashCooldown,
            shieldActive = shieldActive,
            spawnTimerMs = spawnTimer,
            bonusSpawnTimerMs = bonusSpawnTimer,
            scoreCarryMs = carry,
            bonusesCollected = bonusesCollected,
            phase = phase,
        )
    }

    /** Test-only escape hatch: pins exact situations (a rock overlapping the
     *  ship, a pickup a hair above it) that honest play can't set up
     *  deterministically. */
    internal fun forceForTest(
        meteors: List<Meteor>? = null,
        bonuses: List<MeteorBonus>? = null,
        shieldActive: Boolean? = null,
    ) {
        state = state.copy(
            meteors = meteors ?: state.meteors,
            bonuses = bonuses ?: state.bonuses,
            shieldActive = shieldActive ?: state.shieldActive,
        )
    }

    private fun spawnIntervalForLevel(level: Int): Long =
        (BASE_SPAWN_INTERVAL_MS - (level - 1) * SPAWN_INTERVAL_STEP_MS).coerceAtLeast(MIN_SPAWN_INTERVAL_MS)

    private fun meteorSpeedForLevel(level: Int): Float =
        (METEOR_BASE_SPEED + (level - 1) * METEOR_SPEED_PER_LEVEL).coerceAtMost(METEOR_MAX_SPEED)

    /** Center-based AABB overlap. */
    private fun overlaps(
        ax: Float, ay: Float, aw: Float, ah: Float,
        bx: Float, by: Float, bw: Float, bh: Float,
    ): Boolean = abs(ax - bx) <= (aw + bw) / 2f && abs(ay - by) <= (ah + bh) / 2f

    private fun initialState(): MeteorDodgeState = MeteorDodgeState(
        score = 0,
        survivedMs = 0L,
        speedLevel = 1,
        playerX = METEOR_FIELD_WIDTH / 2f,
        playerY = METEOR_FIELD_HEIGHT - 10f,
        meteors = emptyList(),
        bonuses = emptyList(),
        dashCooldownMs = 0L,
        lastMoveDirection = 1,
        shieldActive = false,
        spawnTimerMs = BASE_SPAWN_INTERVAL_MS,
        bonusSpawnTimerMs = BONUS_SPAWN_EVERY_MS,
        scoreCarryMs = 0L,
        bonusesCollected = 0,
        phase = MeteorDodgePhase.Running,
    )

    private companion object {
        const val PLAYER_STEP = 7f
        const val VERTICAL_STEP = 5f
        const val PLAYER_HITBOX_WIDTH = 5f
        const val PLAYER_HITBOX_HEIGHT = 4f
        const val DASH_DISTANCE = 20f
        const val DASH_COOLDOWN_MS = 1_500L
        const val SCORE_SLICE_MS = 100L
        const val SPEED_LEVEL_EVERY_MS = 15_000L
        const val MAX_SPEED_LEVEL = 8
        const val BASE_SPAWN_INTERVAL_MS = 1_000L
        const val SPAWN_INTERVAL_STEP_MS = 90L
        const val MIN_SPAWN_INTERVAL_MS = 400L
        const val METEOR_BASE_SPEED = 26f
        const val METEOR_SPEED_PER_LEVEL = 7f
        const val METEOR_MAX_SPEED = 75f
        const val METEOR_RADIUS_SMALL = 2.5f
        const val METEOR_RADIUS_LARGE = 4f
        const val LARGE_METEOR_SHARE = 0.3f
        const val METEOR_MAX_DRIFT = 10f
        const val BONUS_SPAWN_EVERY_MS = 6_000L
        const val BONUS_RETRY_MS = 500L
        const val BONUS_FALL_SPEED = 30f
        const val BONUS_SCORE = 100
        const val SHIELD_BONUS_SHARE = 0.25f
    }
}
