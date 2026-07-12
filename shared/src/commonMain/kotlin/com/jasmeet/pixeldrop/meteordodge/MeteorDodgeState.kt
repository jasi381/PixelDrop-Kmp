package com.jasmeet.pixeldrop.meteordodge

enum class MeteorDodgePhase { Running, Paused, GameOver }

enum class MeteorBonusType { Score, Shield }

/** One falling rock; [radius] is the cluster's rough half-size (small or
 *  large variant) and [driftX] a gentle sideways lean that flips at walls. */
data class Meteor(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val velocityY: Float,
    val driftX: Float,
)

data class MeteorBonus(
    val id: Int,
    val type: MeteorBonusType,
    val x: Float,
    val y: Float,
    val velocityY: Float,
)

data class MeteorDodgeState(
    val score: Int,
    val survivedMs: Long,
    /** 1-based difficulty tier, stepping up every 15s of survival. */
    val speedLevel: Int,
    val playerX: Float,
    val playerY: Float,
    val meteors: List<Meteor>,
    val bonuses: List<MeteorBonus>,
    val dashCooldownMs: Long,
    /** Direction of the most recent horizontal step; dash reuses it. */
    val lastMoveDirection: Int,
    /** One-hit protection from a caught Shield bonus; spent on the block. */
    val shieldActive: Boolean,
    /** Counts down to the next meteor spawn. */
    val spawnTimerMs: Long,
    /** Counts down to the next bonus-dot spawn. */
    val bonusSpawnTimerMs: Long,
    /** Sub-100ms remainder of survival time not yet turned into score. */
    val scoreCarryMs: Long,
    /** Monotonic pickup counter; lets the UI fire a one-shot chime per
     *  catch without diffing lists. */
    val bonusesCollected: Int,
    val phase: MeteorDodgePhase = MeteorDodgePhase.Running,
)

/** Same fixed normalized playfield trick as the other games: physics lives
 *  in this space and the renderer scales it uniformly onto the LCD. */
const val METEOR_FIELD_WIDTH = 100f
const val METEOR_FIELD_HEIGHT = 130f
const val METEOR_PLAYER_WIDTH = 7f
const val METEOR_PLAYER_HEIGHT = 5f
/** Vertical movement stays confined to the lower part of the field. */
const val METEOR_PLAYER_MIN_Y = METEOR_FIELD_HEIGHT * 0.6f
const val METEOR_PLAYER_MAX_Y = METEOR_FIELD_HEIGHT - 6f
const val METEOR_BONUS_SIZE = 3.5f
