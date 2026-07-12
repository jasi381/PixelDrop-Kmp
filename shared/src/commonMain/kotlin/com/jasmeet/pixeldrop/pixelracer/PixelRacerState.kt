package com.jasmeet.pixeldrop.pixelracer

enum class PixelRacerPhase { Running, Paused, GameOver }

/** Why the run ended; the game-over screen words its headline off this. */
enum class RacerGameOverCause { Wrecked, OutOfFuel }

/** Traffic variants: [speedFactor] scales the road scroll (how fast the car
 *  approaches the player), sizes are in field units. Trucks are slow but
 *  long lane-blockers; sports close distance half again as fast. */
enum class TrafficKind(val speedFactor: Float, val width: Float, val height: Float) {
    Sedan(1.0f, 12f, 16f),
    Truck(0.8f, 13f, 26f),
    Sport(1.5f, 11f, 14f),
}

enum class RacerPickupKind { Fuel, Coin, Shield, SlowMo, Turbo }

data class TrafficCar(
    val id: Int,
    val lane: Int,
    /** Center y in field units; spawns above the field, removed below it. */
    val y: Float,
    val kind: TrafficKind,
    /** Set once the car has crossed the player's row — arms the near-miss
     *  bonus so each car can only ever score it once. */
    val passed: Boolean = false,
)

data class RacerPickup(
    val id: Int,
    val lane: Int,
    val y: Float,
    val kind: RacerPickupKind,
)

data class PixelRacerState(
    val score: Int,
    /** Total road scrolled in field units; HUD shows it as meters (/10). */
    val distancePx: Float,
    val survivedMs: Long,
    /** 1-based difficulty tier, stepping up every 20s, capped. */
    val speedLevel: Int,
    val lives: Int,
    val fuel: Float,
    /** Monotonic count of low-fuel threshold crossings; the UI beeps once
     *  per increment without diffing floats. */
    val lowFuelWarnings: Int,
    /** Lane the player is steering toward (0..3). */
    val playerLane: Int,
    /** Actual x center, sliding toward the target lane each tick. */
    val playerX: Float,
    /** Post-crash mercy window; collisions are ignored while positive. */
    val invulnMs: Long,
    val shieldActive: Boolean,
    val slowMoMs: Long,
    val turboMs: Long,
    /** Near-miss streak 0..max; the effective score multiplier is
     *  max(combo, 1). Resets on crash, decays when the window lapses. */
    val combo: Int,
    val comboMs: Long,
    val traffic: List<TrafficCar>,
    val pickups: List<RacerPickup>,
    val spawnTimerMs: Long,
    val wavesSpawned: Int,
    /** The guaranteed-open lane of the last wave; the next wave's open lane
     *  stays within one lane of it so a survivable path always exists. */
    val lastFreeLane: Int,
    // Monotonic one-shot counters: the UI fires SFX on increments.
    val coinsCollected: Int,
    val fuelCollected: Int,
    val powerupsCollected: Int,
    val nearMisses: Int,
    val crashes: Int,
    val phase: PixelRacerPhase = PixelRacerPhase.Running,
    val gameOverCause: RacerGameOverCause? = null,
)

/** Same fixed normalized playfield trick as the other games: physics lives
 *  in this space and the renderer scales it uniformly onto the LCD. */
const val RACER_FIELD_WIDTH = 100f
const val RACER_FIELD_HEIGHT = 130f
const val RACER_LANE_COUNT = 4
const val RACER_ROAD_LEFT = 10f
const val RACER_ROAD_RIGHT = 90f
const val RACER_LANE_WIDTH = (RACER_ROAD_RIGHT - RACER_ROAD_LEFT) / RACER_LANE_COUNT
const val RACER_PLAYER_WIDTH = 12f
const val RACER_PLAYER_HEIGHT = 16f
/** Fixed player row: the car never moves vertically, the road does. */
const val RACER_PLAYER_Y = 112f
const val RACER_PICKUP_SIZE = 7f

/** Center x of a lane (0..3) in field units: 20 / 40 / 60 / 80. */
fun racerLaneCenterX(lane: Int): Float =
    RACER_ROAD_LEFT + RACER_LANE_WIDTH * (lane + 0.5f)
