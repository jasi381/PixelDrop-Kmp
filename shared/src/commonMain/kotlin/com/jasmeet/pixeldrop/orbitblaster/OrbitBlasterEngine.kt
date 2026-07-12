package com.jasmeet.pixeldrop.orbitblaster

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

/**
 * Fixed-space shooter engine, independent of any UI drawing. Positions and
 * velocities live in the [ORBIT_FIELD_WIDTH]x[ORBIT_FIELD_HEIGHT] space and
 * [tick] runs off a frame loop with a real delta, same as BRICK PULSE.
 *
 * [randomFloat] feeds enemy-fire rolls, shooter selection and powerup drops;
 * injectable for deterministic tests.
 */
class OrbitBlasterEngine(
    private val randomFloat: () -> Float = { Random.Default.nextFloat() },
) {
    // Declared before [state] so initialState() can mint the first wave's
    // enemy ids (Kotlin runs property initializers in declaration order).
    private var nextBulletId = 0
    private var nextEnemyId = 0
    private var nextExplosionId = 0
    private var nextPowerUpId = 0

    var state: OrbitBlasterState by mutableStateOf(initialState())
        private set

    // Discrete D-pad pulses move the ship a fixed step per call, exactly like
    // BRICK PULSE's paddle — there's no press/release pair to integrate from.
    fun moveLeft() = nudgePlayer(-PLAYER_STEP)

    fun moveRight() = nudgePlayer(PLAYER_STEP)

    private fun nudgePlayer(delta: Float) {
        if (state.phase != OrbitBlasterPhase.Running) return
        val half = PLAYER_WIDTH / 2f
        state = state.copy(
            playerX = (state.playerX + delta).coerceIn(half, ORBIT_FIELD_WIDTH - half),
            lastMoveDirection = if (delta < 0f) -1 else 1,
        )
    }

    fun shoot() {
        val s = state
        if (s.phase != OrbitBlasterPhase.Running) return
        if (s.fireCooldownMs > 0 || s.playerBullets.size >= MAX_PLAYER_BULLETS) return
        state = s.copy(
            playerBullets = s.playerBullets + OrbitBullet(
                id = nextBulletId++,
                x = s.playerX,
                y = PLAYER_Y - PLAYER_HEIGHT,
                velocityY = -PLAYER_BULLET_SPEED,
                fromEnemy = false,
            ),
            fireCooldownMs = FIRE_COOLDOWN_MS,
        )
    }

    /** Quick sideways hop in the last movement direction; only usable while
     *  a caught Dash powerup's window is open, then rate-limited on its own
     *  cooldown. No-op otherwise, so B stays safe to mash. */
    fun dash() {
        val s = state
        if (s.phase != OrbitBlasterPhase.Running) return
        if (s.dashMs <= 0 || s.dashCooldownMs > 0) return
        val half = PLAYER_WIDTH / 2f
        state = s.copy(
            playerX = (s.playerX + DASH_DISTANCE * s.lastMoveDirection).coerceIn(half, ORBIT_FIELD_WIDTH - half),
            dashCooldownMs = DASH_COOLDOWN_MS,
        )
    }

    fun pause() {
        if (state.phase == OrbitBlasterPhase.Running) state = state.copy(phase = OrbitBlasterPhase.Paused)
    }

    fun resume() {
        if (state.phase == OrbitBlasterPhase.Paused) state = state.copy(phase = OrbitBlasterPhase.Running)
    }

    fun reset() {
        state = initialState()
    }

    fun isGameOver(): Boolean = state.phase == OrbitBlasterPhase.GameOver

    fun tick(deltaMs: Long) {
        val s = state
        if (s.phase != OrbitBlasterPhase.Running) return
        val dt = deltaMs / 1000f

        val fireCooldown = (s.fireCooldownMs - deltaMs).coerceAtLeast(0)
        val dashCooldown = (s.dashCooldownMs - deltaMs).coerceAtLeast(0)
        var invulnerableMs = (s.invulnerableMs - deltaMs).coerceAtLeast(0)

        val explosions = s.explosions
            .map { it.copy(ageMs = it.ageMs + deltaMs) }
            .filter { it.ageMs < EXPLOSION_LIFETIME_MS }
            .toMutableList()

        var playerBullets = s.playerBullets
            .map { it.copy(y = it.y + it.velocityY * dt) }
            .filter { it.y > -ORBIT_BULLET_HEIGHT }
            .toMutableList()
        var enemyBullets = s.enemyBullets
            .map { it.copy(y = it.y + it.velocityY * dt) }
            .filter { it.y < ORBIT_FIELD_HEIGHT + ORBIT_BULLET_HEIGHT }
            .toMutableList()

        // Powerups fall; the ship catches them by overlap, the floor eats them.
        var shieldCaught = false
        var dashCaught = false
        val powerUps = mutableListOf<OrbitPowerUp>()
        for (powerUp in s.powerUps) {
            val y = powerUp.y + powerUp.velocityY * dt
            val caught = overlaps(
                powerUp.x, y, ORBIT_POWERUP_SIZE, ORBIT_POWERUP_SIZE,
                s.playerX, PLAYER_Y, PLAYER_WIDTH, PLAYER_HEIGHT,
            )
            when {
                caught -> when (powerUp.type) {
                    OrbitPowerUpType.Shield -> shieldCaught = true
                    OrbitPowerUpType.Dash -> dashCaught = true
                }
                y - ORBIT_POWERUP_SIZE / 2f > ORBIT_FIELD_HEIGHT -> Unit
                else -> powerUps += powerUp.copy(y = y)
            }
        }
        // A fresh catch restarts the full window rather than stacking.
        var shieldMs = if (shieldCaught) SHIELD_DURATION_MS else (s.shieldMs - deltaMs).coerceAtLeast(0)
        val dashMs = if (dashCaught) DASH_DURATION_MS else (s.dashMs - deltaMs).coerceAtLeast(0)

        // Formation drift: everyone slides together; touching a wall flips
        // the direction and steps the whole wave down instead.
        var enemyDirection = s.enemyDirection
        var enemies = s.enemies
        if (enemies.isNotEmpty()) {
            val dx = enemyDirection * enemySpeedForWave(s.wave) * dt
            val hitsWall = enemies.any { (it.x + dx) !in ENEMY_MIN_X..ENEMY_MAX_X }
            enemies = if (hitsWall) {
                enemyDirection = -enemyDirection
                enemies.map { it.copy(y = it.y + ENEMY_DESCEND) }
            } else {
                enemies.map { it.copy(x = it.x + dx) }
            }
        }

        // Boss drifts and bounces inside its own margins near the top.
        var boss = s.boss?.let { b ->
            var direction = b.direction
            var x = b.x + direction * bossSpeedForWave(s.wave) * dt
            if (x < BOSS_MIN_X) {
                x = BOSS_MIN_X
                direction = 1
            } else if (x > BOSS_MAX_X) {
                x = BOSS_MAX_X
                direction = -1
            }
            b.copy(x = x, direction = direction, shootCooldownMs = (b.shootCooldownMs - deltaMs).coerceAtLeast(0))
        }

        // Enemy fire: a wave-scaled chance per second, gated to keep the LCD
        // readable — never on wave 1, never past the live-bullet cap.
        if (s.wave >= ENEMY_FIRE_MIN_WAVE && enemies.isNotEmpty() && enemyBullets.size < MAX_ENEMY_BULLETS) {
            val chancePerSecond = (ENEMY_FIRE_BASE_PER_SECOND + (s.wave - ENEMY_FIRE_MIN_WAVE) * ENEMY_FIRE_GROWTH_PER_WAVE)
                .coerceAtMost(ENEMY_FIRE_MAX_PER_SECOND)
            if (randomFloat() < chancePerSecond * dt) {
                val shooter = enemies[(randomFloat() * enemies.size).toInt().coerceIn(0, enemies.size - 1)]
                enemyBullets += OrbitBullet(
                    id = nextBulletId++,
                    x = shooter.x, y = shooter.y + ENEMY_HEIGHT,
                    velocityY = ENEMY_BULLET_SPEED,
                    fromEnemy = true,
                )
            }
        }
        val bossReady = boss
        if (bossReady != null && bossReady.shootCooldownMs <= 0 && enemyBullets.size < MAX_ENEMY_BULLETS) {
            enemyBullets += OrbitBullet(
                id = nextBulletId++,
                x = bossReady.x, y = bossReady.y + BOSS_HEIGHT / 2f + 1f,
                velocityY = ENEMY_BULLET_SPEED,
                fromEnemy = true,
            )
            boss = bossReady.copy(shootCooldownMs = bossShootCooldownForWave(s.wave))
        }

        // Player bullets vs enemies/boss — each bullet spends itself on the
        // first thing it touches.
        var scoreGain = 0
        var enemiesKilled = s.enemiesKilled
        val remainingEnemies = enemies.toMutableList()
        val survivingBullets = mutableListOf<OrbitBullet>()
        for (bullet in playerBullets) {
            val hitIndex = remainingEnemies.indexOfFirst {
                overlaps(bullet.x, bullet.y, ORBIT_BULLET_WIDTH, ORBIT_BULLET_HEIGHT, it.x, it.y, ENEMY_WIDTH, ENEMY_HEIGHT)
            }
            val bossTarget = boss
            when {
                hitIndex >= 0 -> {
                    val enemy = remainingEnemies.removeAt(hitIndex)
                    scoreGain += ENEMY_SCORE
                    enemiesKilled++
                    explosions += OrbitExplosion(nextExplosionId++, enemy.x, enemy.y, 0L)
                    if (randomFloat() < POWERUP_DROP_CHANCE) {
                        powerUps += OrbitPowerUp(nextPowerUpId++, rollPowerUpType(), enemy.x, enemy.y, POWERUP_FALL_SPEED)
                    }
                }
                bossTarget != null && overlaps(
                    bullet.x, bullet.y, ORBIT_BULLET_WIDTH, ORBIT_BULLET_HEIGHT,
                    bossTarget.x, bossTarget.y, BOSS_WIDTH, BOSS_HEIGHT,
                ) -> {
                    val health = bossTarget.health - 1
                    explosions += OrbitExplosion(nextExplosionId++, bullet.x, bullet.y, 0L)
                    boss = if (health <= 0) {
                        scoreGain += bossBonusForWave(s.wave)
                        enemiesKilled++
                        explosions += OrbitExplosion(nextExplosionId++, bossTarget.x - 4f, bossTarget.y, 0L)
                        explosions += OrbitExplosion(nextExplosionId++, bossTarget.x + 4f, bossTarget.y + 3f, 0L)
                        // A downed boss always pays out a powerup.
                        powerUps += OrbitPowerUp(nextPowerUpId++, rollPowerUpType(), bossTarget.x, bossTarget.y, POWERUP_FALL_SPEED)
                        null
                    } else {
                        bossTarget.copy(health = health)
                    }
                }
                else -> survivingBullets += bullet
            }
        }
        playerBullets = survivingBullets
        enemies = remainingEnemies

        // Enemy bullets vs the ship. The hitbox is a touch smaller than the
        // drawn ship so near-misses feel fair. Invulnerability lets bullets
        // pass straight through.
        var playerHit = false
        if (invulnerableMs <= 0L) {
            val before = enemyBullets.size
            enemyBullets = enemyBullets.filterNot {
                overlaps(it.x, it.y, ORBIT_BULLET_WIDTH, ORBIT_BULLET_HEIGHT, s.playerX, PLAYER_Y, PLAYER_HITBOX_WIDTH, PLAYER_HITBOX_HEIGHT)
            }.toMutableList()
            if (enemyBullets.size < before) playerHit = true
        }

        // Enemies crossing the danger line breach the defense: they die in
        // place and cost a hit (blocked like any other while invulnerable).
        val breachers = enemies.filter { it.y + ENEMY_HEIGHT / 2f >= DANGER_Y }
        if (breachers.isNotEmpty()) {
            enemies = enemies - breachers.toSet()
            breachers.forEach { explosions += OrbitExplosion(nextExplosionId++, it.x, it.y, 0L) }
            if (invulnerableMs <= 0L) playerHit = true
        }

        // Damage resolution — at most one hit event per tick. An active
        // shield absorbs it (and is spent); otherwise a life goes.
        var lives = s.lives
        var playerX = s.playerX
        var phase = s.phase
        if (playerHit) {
            explosions += OrbitExplosion(nextExplosionId++, s.playerX, PLAYER_Y, 0L)
            if (shieldMs > 0) {
                shieldMs = 0
                invulnerableMs = SHIELD_BLOCK_GRACE_MS
            } else {
                lives -= 1
                if (lives > 0) {
                    playerX = ORBIT_FIELD_WIDTH / 2f
                    invulnerableMs = INVULNERABLE_MS
                    enemyBullets.clear()
                } else {
                    phase = OrbitBlasterPhase.GameOver
                }
            }
        }

        // Wave clear: nothing hostile left. Bank the bonus for the cleared
        // wave, then bring in the next formation — or the boss on every 5th.
        // In-flight powerups deliberately survive the transition: the boss's
        // guaranteed drop spawns on the same tick as its wave's clear and
        // would otherwise never reach the player.
        var wave = s.wave
        if (phase == OrbitBlasterPhase.Running && enemies.isEmpty() && boss == null) {
            scoreGain += WAVE_CLEAR_BONUS * wave
            wave += 1
            playerBullets.clear()
            enemyBullets.clear()
            if (wave % BOSS_WAVE_INTERVAL == 0) {
                boss = spawnBoss(wave)
            } else {
                enemies = spawnFormation(wave)
                enemyDirection = 1
            }
        }

        state = s.copy(
            score = s.score + scoreGain,
            lives = lives,
            wave = wave,
            playerX = playerX,
            playerBullets = if (phase == OrbitBlasterPhase.GameOver) emptyList() else playerBullets,
            enemyBullets = if (phase == OrbitBlasterPhase.GameOver) emptyList() else enemyBullets,
            enemies = enemies,
            boss = boss,
            explosions = explosions,
            powerUps = powerUps,
            fireCooldownMs = fireCooldown,
            dashCooldownMs = dashCooldown,
            dashMs = dashMs,
            shieldMs = shieldMs,
            invulnerableMs = invulnerableMs,
            enemyDirection = enemyDirection,
            enemiesKilled = enemiesKilled,
            phase = phase,
        )
    }

    /** Test-only escape hatch: pins exact situations (a boss at one health,
     *  a bullet a hair from the ship on the last life) that honest play
     *  can't set up deterministically. [keepBoss] distinguishes "leave the
     *  boss alone" from "set it to null". */
    internal fun forceForTest(
        lives: Int? = null,
        wave: Int? = null,
        enemies: List<OrbitEnemy>? = null,
        boss: OrbitBoss? = null,
        keepBoss: Boolean = boss == null,
        playerBullets: List<OrbitBullet>? = null,
        enemyBullets: List<OrbitBullet>? = null,
        powerUps: List<OrbitPowerUp>? = null,
        shieldMs: Long? = null,
        dashMs: Long? = null,
        invulnerableMs: Long? = null,
    ) {
        state = state.copy(
            lives = lives ?: state.lives,
            wave = wave ?: state.wave,
            enemies = enemies ?: state.enemies,
            boss = if (keepBoss && boss == null) state.boss else boss,
            playerBullets = playerBullets ?: state.playerBullets,
            enemyBullets = enemyBullets ?: state.enemyBullets,
            powerUps = powerUps ?: state.powerUps,
            shieldMs = shieldMs ?: state.shieldMs,
            dashMs = dashMs ?: state.dashMs,
            invulnerableMs = invulnerableMs ?: state.invulnerableMs,
        )
    }

    private fun rollPowerUpType(): OrbitPowerUpType =
        if (randomFloat() < SHIELD_SHARE) OrbitPowerUpType.Shield else OrbitPowerUpType.Dash

    private fun spawnFormation(wave: Int): List<OrbitEnemy> {
        val cols = (4 + wave).coerceAtMost(MAX_FORMATION_COLS)
        val rows = (1 + wave / 2).coerceAtMost(MAX_FORMATION_ROWS)
        val startX = ORBIT_FIELD_WIDTH / 2f - (cols - 1) * FORMATION_SPACING_X / 2f
        return (0 until rows).flatMap { row ->
            (0 until cols).map { col ->
                OrbitEnemy(
                    id = nextEnemyId++,
                    x = startX + col * FORMATION_SPACING_X,
                    y = FORMATION_TOP_Y + row * FORMATION_SPACING_Y,
                )
            }
        }
    }

    private fun spawnBoss(wave: Int): OrbitBoss = OrbitBoss(
        x = ORBIT_FIELD_WIDTH / 2f,
        y = BOSS_Y,
        health = bossHealthForWave(wave),
        maxHealth = bossHealthForWave(wave),
        direction = 1,
        shootCooldownMs = bossShootCooldownForWave(wave),
    )

    /** How many boss waves came before this one (0 for wave 5, 1 for 10...). */
    private fun bossLap(wave: Int): Int = (wave / BOSS_WAVE_INTERVAL - 1).coerceAtLeast(0)

    private fun bossHealthForWave(wave: Int): Int = BOSS_BASE_HEALTH + bossLap(wave) * 4

    private fun bossBonusForWave(wave: Int): Int = BOSS_BASE_BONUS + bossLap(wave) * 200

    private fun bossSpeedForWave(wave: Int): Float = BOSS_BASE_SPEED + bossLap(wave) * 3f

    private fun bossShootCooldownForWave(wave: Int): Long =
        (BOSS_BASE_SHOOT_COOLDOWN_MS - bossLap(wave) * 200L).coerceAtLeast(700L)

    private fun enemySpeedForWave(wave: Int): Float =
        (ENEMY_BASE_SPEED + (wave - 1) * ENEMY_SPEED_PER_WAVE).coerceAtMost(ENEMY_MAX_SPEED)

    /** Center-based AABB overlap. */
    private fun overlaps(
        ax: Float, ay: Float, aw: Float, ah: Float,
        bx: Float, by: Float, bw: Float, bh: Float,
    ): Boolean = abs(ax - bx) <= (aw + bw) / 2f && abs(ay - by) <= (ah + bh) / 2f

    private fun initialState(): OrbitBlasterState = OrbitBlasterState(
        score = 0,
        lives = LIVES_START,
        wave = 1,
        playerX = ORBIT_FIELD_WIDTH / 2f,
        playerBullets = emptyList(),
        enemyBullets = emptyList(),
        enemies = spawnFormation(1),
        boss = null,
        explosions = emptyList(),
        powerUps = emptyList(),
        fireCooldownMs = 0L,
        dashCooldownMs = 0L,
        dashMs = 0L,
        shieldMs = 0L,
        invulnerableMs = 0L,
        enemyDirection = 1,
        lastMoveDirection = 1,
        enemiesKilled = 0,
        phase = OrbitBlasterPhase.Running,
    )

    private companion object {
        const val LIVES_START = 3
        const val PLAYER_STEP = 7f
        const val PLAYER_HITBOX_WIDTH = 6f
        const val PLAYER_HITBOX_HEIGHT = 4f
        const val PLAYER_BULLET_SPEED = 95f
        const val ENEMY_BULLET_SPEED = 45f
        const val FIRE_COOLDOWN_MS = 250L
        const val MAX_PLAYER_BULLETS = 4
        const val MAX_ENEMY_BULLETS = 4
        const val ENEMY_SCORE = 20
        const val WAVE_CLEAR_BONUS = 100
        const val ENEMY_MIN_X = 6f
        const val ENEMY_MAX_X = ORBIT_FIELD_WIDTH - 6f
        const val ENEMY_DESCEND = 4f
        const val ENEMY_BASE_SPEED = 9f
        const val ENEMY_SPEED_PER_WAVE = 2f
        const val ENEMY_MAX_SPEED = 26f
        const val ENEMY_FIRE_MIN_WAVE = 2
        const val ENEMY_FIRE_BASE_PER_SECOND = 0.4f
        const val ENEMY_FIRE_GROWTH_PER_WAVE = 0.1f
        const val ENEMY_FIRE_MAX_PER_SECOND = 1.5f
        const val FORMATION_TOP_Y = 16f
        const val FORMATION_SPACING_X = 10f
        const val FORMATION_SPACING_Y = 9f
        const val MAX_FORMATION_COLS = 8
        const val MAX_FORMATION_ROWS = 3
        const val BOSS_WAVE_INTERVAL = 5
        const val BOSS_BASE_HEALTH = 8
        const val BOSS_BASE_BONUS = 300
        const val BOSS_BASE_SPEED = 14f
        const val BOSS_BASE_SHOOT_COOLDOWN_MS = 1500L
        const val BOSS_MIN_X = 6f + BOSS_WIDTH / 2f
        const val BOSS_MAX_X = ORBIT_FIELD_WIDTH - 6f - BOSS_WIDTH / 2f
        const val EXPLOSION_LIFETIME_MS = 250L
        const val POWERUP_DROP_CHANCE = 0.10f
        const val SHIELD_SHARE = 0.6f
        const val POWERUP_FALL_SPEED = 20f
        const val SHIELD_DURATION_MS = 9_000L
        const val DASH_DURATION_MS = 10_000L
        const val DASH_COOLDOWN_MS = 1_200L
        const val DASH_DISTANCE = 22f
        const val INVULNERABLE_MS = 1_500L
        const val SHIELD_BLOCK_GRACE_MS = 800L
    }
}
