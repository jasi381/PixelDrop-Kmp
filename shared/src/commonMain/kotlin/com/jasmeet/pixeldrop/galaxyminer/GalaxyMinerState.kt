package com.jasmeet.pixeldrop.galaxyminer

enum class GalaxyMinerPhase { Running, Paused, GameOver }
enum class MinerPowerType { TractorBeam, Shield, SpeedBoost }

data class SpaceCrystal(
    val id: Int,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val value: Int,
)

data class SpaceMine(
    val id: Int,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
)

data class MinerPowerUp(val id: Int, val type: MinerPowerType, val x: Float, val y: Float)

data class GalaxyMinerState(
    val score: Int,
    val hull: Int,
    val level: Int,
    val shipX: Float,
    val shipY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val cargo: Int,
    val cargoValue: Int,
    val bankedCrystals: Int,
    val elapsedMs: Long,
    val mineCooldownMs: Long,
    val beamMs: Long,
    val beamX: Float,
    val beamY: Float,
    val tractorMs: Long,
    val speedBoostMs: Long,
    val shieldActive: Boolean,
    val invulnerableMs: Long,
    val crystals: List<SpaceCrystal>,
    val mines: List<SpaceMine>,
    val powerUps: List<MinerPowerUp>,
    val crystalsMined: Int,
    val deposits: Int,
    val hits: Int,
    val powerUpsCollected: Int,
    val phase: GalaxyMinerPhase = GalaxyMinerPhase.Running,
)

const val MINER_FIELD_WIDTH = 100f
const val MINER_FIELD_HEIGHT = 130f
const val MINER_STATION_X = 50f
const val MINER_STATION_Y = 116f
const val MINER_SHIP_RADIUS = 3.2f
const val MINER_CRYSTAL_RADIUS = 2.2f
const val MINER_MINE_RADIUS = 3f
const val MINER_POWER_SIZE = 5f

fun GalaxyMinerState.cargoMultiplier(): Int = (1 + cargo / 2).coerceAtMost(5)

