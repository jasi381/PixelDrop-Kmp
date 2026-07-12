package com.jasmeet.pixeldrop.orbitblaster

enum class OrbitBlasterPhase { Running, Paused, GameOver }

enum class OrbitPowerUpType { Shield, Dash }

/** One projectile, from either side; [velocityY] sign gives the direction
 *  (up for the player, down for enemies/boss). */
data class OrbitBullet(
    val id: Int,
    val x: Float,
    val y: Float,
    val velocityY: Float,
    val fromEnemy: Boolean,
)

data class OrbitEnemy(
    val id: Int,
    val x: Float,
    val y: Float,
)

data class OrbitBoss(
    val x: Float,
    val y: Float,
    val health: Int,
    val maxHealth: Int,
    val direction: Int,
    val shootCooldownMs: Long,
)

/** A short-lived pixel burst; [ageMs] grows each tick and the engine drops
 *  it once it passes its lifetime. */
data class OrbitExplosion(
    val id: Int,
    val x: Float,
    val y: Float,
    val ageMs: Long,
)

data class OrbitPowerUp(
    val id: Int,
    val type: OrbitPowerUpType,
    val x: Float,
    val y: Float,
    val velocityY: Float,
)

data class OrbitBlasterState(
    val score: Int,
    val lives: Int,
    val wave: Int,
    val playerX: Float,
    val playerBullets: List<OrbitBullet>,
    val enemyBullets: List<OrbitBullet>,
    val enemies: List<OrbitEnemy>,
    /** Non-null only during a boss wave (every [OrbitBlasterEngine] 5th). */
    val boss: OrbitBoss?,
    val explosions: List<OrbitExplosion>,
    val powerUps: List<OrbitPowerUp>,
    val fireCooldownMs: Long,
    val dashCooldownMs: Long,
    /** Remaining dash-ability window; B dashes only while this is positive. */
    val dashMs: Long,
    /** Remaining shield window; blocks one hit, then clears immediately. */
    val shieldMs: Long,
    /** Post-hit grace period; the ship blinks and can't be hit again. */
    val invulnerableMs: Long,
    /** +1/-1 formation drift; flips (and the wave steps down) at the walls. */
    val enemyDirection: Int,
    /** Direction of the most recent D-pad step; dash reuses it. */
    val lastMoveDirection: Int,
    /** Monotonic kill counter (enemies + bosses) across the whole run; lets
     *  the UI layer fire a one-shot chime per kill without diffing lists. */
    val enemiesKilled: Int,
    val phase: OrbitBlasterPhase = OrbitBlasterPhase.Running,
)

/** Same fixed normalized playfield trick as BRICK PULSE: physics lives in
 *  this space and the renderer scales it uniformly onto the LCD. */
const val ORBIT_FIELD_WIDTH = 100f
const val ORBIT_FIELD_HEIGHT = 130f
const val PLAYER_Y = 120f
const val PLAYER_WIDTH = 8f
const val PLAYER_HEIGHT = 5f
const val ORBIT_BULLET_WIDTH = 1.4f
const val ORBIT_BULLET_HEIGHT = 3f
const val ENEMY_WIDTH = 7f
const val ENEMY_HEIGHT = 5f
const val BOSS_WIDTH = 16f
const val BOSS_HEIGHT = 10f
const val BOSS_Y = 24f
const val ORBIT_POWERUP_SIZE = 6f
/** An enemy whose bottom edge crosses this line has breached the defense. */
const val DANGER_Y = PLAYER_Y - 8f
