package com.jasmeet.pixeldrop.pixelracer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

/**
 * PIXEL RACER: 4-lane endless dodger. The road scrolls down past a fixed
 * player row; traffic approaches at kind-scaled speed, fuel is a soft clock,
 * and the score wants you close to danger (near-miss combo). Independent of
 * any UI drawing; positions live in the fixed [RACER_FIELD_WIDTH] x
 * [RACER_FIELD_HEIGHT] space and [tick] runs off a frame loop with a real
 * delta, same as the other engines.
 *
 * [randomFloat] feeds lane picks, traffic variants and coin rolls;
 * injectable for deterministic tests.
 */
class PixelRacerEngine(
    private val randomFloat: () -> Float = { Random.Default.nextFloat() },
) {
    private var nextCarId = 0
    private var nextPickupId = 0
    /** Sub-slice scroll not yet converted into distance score. */
    private var scoreCarryPx = 0f

    var state: PixelRacerState by mutableStateOf(initialState())
        private set

    fun moveLeft() = steer(-1)

    fun moveRight() = steer(+1)

    private fun steer(delta: Int) {
        if (state.phase != PixelRacerPhase.Running) return
        state = state.copy(playerLane = (state.playerLane + delta).coerceIn(0, RACER_LANE_COUNT - 1))
    }

    fun pause() {
        if (state.phase == PixelRacerPhase.Running) state = state.copy(phase = PixelRacerPhase.Paused)
    }

    fun resume() {
        if (state.phase == PixelRacerPhase.Paused) state = state.copy(phase = PixelRacerPhase.Running)
    }

    fun reset() {
        nextCarId = 0
        nextPickupId = 0
        scoreCarryPx = 0f
        state = initialState()
    }

    fun isGameOver(): Boolean = state.phase == PixelRacerPhase.GameOver

    fun tick(deltaMs: Long) {
        val s = state
        if (s.phase != PixelRacerPhase.Running) return
        // Clamped so an app-switch pause can't replay seconds of traffic in
        // one frame.
        val clampedMs = deltaMs.coerceAtMost(MAX_DELTA_MS)
        val dt = clampedMs / 1000f

        val survivedMs = s.survivedMs + clampedMs
        val speedLevel = (1 + survivedMs / SPEED_LEVEL_EVERY_MS).toInt().coerceAtMost(MAX_SPEED_LEVEL)

        // Powerup clocks.
        val slowMoMs = (s.slowMoMs - clampedMs).coerceAtLeast(0)
        val turboMs = (s.turboMs - clampedMs).coerceAtLeast(0)
        val invulnMs = (s.invulnMs - clampedMs).coerceAtLeast(0)

        // World scroll: slow-mo drags the whole road, turbo floors it.
        val slowFactor = if (slowMoMs > 0) SLOWMO_FACTOR else 1f
        val turboFactor = if (turboMs > 0) TURBO_FACTOR else 1f
        val scroll = scrollSpeedForLevel(speedLevel) * slowFactor * turboFactor
        val scrolled = scroll * dt
        val distancePx = s.distancePx + scrolled

        // Distance score: 1 point per 10 units, doubled under turbo, with
        // the sub-slice remainder carried so no distance is lost to rounding.
        scoreCarryPx += scrolled
        var scoreGain = 0
        val distSlices = (scoreCarryPx / SCORE_PER_PX).toInt()
        if (distSlices > 0) {
            scoreCarryPx -= distSlices * SCORE_PER_PX
            scoreGain += distSlices * (if (turboMs > 0) TURBO_SCORE_MULT else 1)
        }

        // Fuel is the soft clock; the tank emptying ends the run outright.
        val fuel = (s.fuel - FUEL_DRAIN_PER_SEC * dt).coerceAtLeast(0f)
        var lowFuelWarnings = s.lowFuelWarnings
        if (s.fuel > LOW_FUEL_THRESHOLD && fuel <= LOW_FUEL_THRESHOLD) lowFuelWarnings++
        if (fuel <= 0f) {
            state = s.copy(
                survivedMs = survivedMs, distancePx = distancePx, fuel = 0f,
                score = s.score + scoreGain, lowFuelWarnings = lowFuelWarnings,
                phase = PixelRacerPhase.GameOver, gameOverCause = RacerGameOverCause.OutOfFuel,
            )
            return
        }

        // Player slides toward its target lane center.
        val targetX = racerLaneCenterX(s.playerLane)
        val maxStep = LANE_SLIDE_PER_SEC * dt
        val playerX = when {
            abs(targetX - s.playerX) <= maxStep -> targetX
            targetX > s.playerX -> s.playerX + maxStep
            else -> s.playerX - maxStep
        }

        // Traffic approaches; cars that clear the player's row arm the
        // near-miss check exactly once, adjacent lanes only.
        var combo = s.combo
        var comboMs = (s.comboMs - clampedMs).coerceAtLeast(0)
        if (comboMs == 0L) combo = 0
        var nearMisses = s.nearMisses
        var traffic = ArrayList<TrafficCar>(s.traffic.size + 3)
        for (car in s.traffic) {
            val y = car.y + scroll * car.kind.speedFactor * dt
            if (y - car.kind.height / 2f > RACER_FIELD_HEIGHT) continue
            var passed = car.passed
            if (!passed && y > RACER_PLAYER_Y) {
                passed = true
                if (abs(car.lane - s.playerLane) == 1 && invulnMs == 0L) {
                    combo = (combo + 1).coerceAtMost(COMBO_MAX)
                    comboMs = COMBO_WINDOW_MS
                    nearMisses++
                    scoreGain += NEAR_MISS_SCORE * combo
                }
            }
            traffic.add(car.copy(y = y, passed = passed))
        }

        // Pickups ride the road; the player collects by overlap.
        var newFuel = fuel
        var coinsCollected = s.coinsCollected
        var fuelCollected = s.fuelCollected
        var powerupsCollected = s.powerupsCollected
        var shieldActive = s.shieldActive
        var newSlowMo = slowMoMs
        var newTurbo = turboMs
        val pickups = ArrayList<RacerPickup>(s.pickups.size + 3)
        for (p in s.pickups) {
            val y = p.y + scroll * dt
            if (y - RACER_PICKUP_SIZE / 2f > RACER_FIELD_HEIGHT) continue
            val caught = overlaps(
                racerLaneCenterX(p.lane), y, RACER_PICKUP_SIZE, RACER_PICKUP_SIZE,
                playerX, RACER_PLAYER_Y, RACER_PLAYER_WIDTH, RACER_PLAYER_HEIGHT,
            )
            if (caught) {
                when (p.kind) {
                    RacerPickupKind.Coin -> {
                        scoreGain += COIN_SCORE * s.multiplier()
                        coinsCollected++
                    }
                    RacerPickupKind.Fuel -> {
                        newFuel = (newFuel + FUEL_PICKUP_AMOUNT).coerceAtMost(FUEL_MAX)
                        fuelCollected++
                    }
                    RacerPickupKind.Shield -> { shieldActive = true; powerupsCollected++ }
                    RacerPickupKind.SlowMo -> { newSlowMo = SLOWMO_DURATION_MS; powerupsCollected++ }
                    RacerPickupKind.Turbo -> { newTurbo = TURBO_DURATION_MS; powerupsCollected++ }
                }
            } else {
                pickups.add(p.copy(y = y))
            }
        }

        // Wave spawner. The open lane wanders at most one lane per wave, so
        // a survivable path always exists (unit-tested fairness invariant).
        var spawnTimer = s.spawnTimerMs - clampedMs
        var wavesSpawned = s.wavesSpawned
        var lastFreeLane = s.lastFreeLane
        if (spawnTimer <= 0) {
            spawnTimer = spawnIntervalForLevel(speedLevel)
            wavesSpawned++
            val freeLane = (lastFreeLane + ((randomFloat() * 3f).toInt() - 1))
                .coerceIn(0, RACER_LANE_COUNT - 1)
            lastFreeLane = freeLane

            val closedLanes = (0 until RACER_LANE_COUNT).filter { it != freeLane }.toMutableList()
            val carCount = (1 + (speedLevel - 1) / 3).coerceAtMost(closedLanes.size)
            repeat(carCount) {
                val lane = closedLanes.removeAt((randomFloat() * closedLanes.size).toInt().coerceAtMost(closedLanes.size - 1))
                val kind = trafficKindForLevel(speedLevel)
                traffic.add(TrafficCar(id = nextCarId++, lane = lane, y = -kind.height / 2f, kind = kind))
            }

            // One pickup line per wave, always in the guaranteed-open lane:
            // powerups rarest, fuel on a steady heartbeat, coins as the
            // risk/reward filler beside the traffic.
            when {
                wavesSpawned % POWERUP_WAVE_EVERY == 0 -> {
                    val kind = when ((wavesSpawned / POWERUP_WAVE_EVERY) % 3) {
                        0 -> RacerPickupKind.Shield
                        1 -> RacerPickupKind.SlowMo
                        else -> RacerPickupKind.Turbo
                    }
                    pickups.add(RacerPickup(nextPickupId++, freeLane, -RACER_PICKUP_SIZE, kind))
                }
                wavesSpawned % FUEL_WAVE_EVERY == 0 ->
                    pickups.add(RacerPickup(nextPickupId++, freeLane, -RACER_PICKUP_SIZE, RacerPickupKind.Fuel))
                randomFloat() < COIN_TRAIL_CHANCE -> repeat(COIN_TRAIL_LENGTH) { i ->
                    pickups.add(
                        RacerPickup(nextPickupId++, freeLane, -RACER_PICKUP_SIZE - i * COIN_TRAIL_GAP, RacerPickupKind.Coin)
                    )
                }
            }
        }

        // Collision, with a hitbox a touch smaller than the drawn cars.
        // A shield eats the hit; otherwise a life goes, with a mercy window.
        var lives = s.lives
        var crashes = s.crashes
        var newInvuln = invulnMs
        var phase = s.phase
        var cause = s.gameOverCause
        if (newInvuln == 0L) {
            val hit = traffic.firstOrNull { car ->
                overlaps(
                    racerLaneCenterX(car.lane), car.y, car.kind.width - HITBOX_MERCY, car.kind.height - HITBOX_MERCY,
                    playerX, RACER_PLAYER_Y, RACER_PLAYER_WIDTH - HITBOX_MERCY, RACER_PLAYER_HEIGHT - HITBOX_MERCY,
                )
            }
            if (hit != null) {
                traffic.remove(hit)
                if (shieldActive) {
                    shieldActive = false
                    newInvuln = INVULN_AFTER_SHIELD_MS
                } else {
                    lives--
                    crashes++
                    combo = 0
                    comboMs = 0
                    newInvuln = INVULN_AFTER_CRASH_MS
                    if (lives <= 0) {
                        phase = PixelRacerPhase.GameOver
                        cause = RacerGameOverCause.Wrecked
                    }
                }
            }
        }

        state = s.copy(
            score = s.score + scoreGain,
            distancePx = distancePx,
            survivedMs = survivedMs,
            speedLevel = speedLevel,
            lives = lives,
            fuel = newFuel,
            lowFuelWarnings = lowFuelWarnings,
            playerX = playerX,
            invulnMs = newInvuln,
            shieldActive = shieldActive,
            slowMoMs = newSlowMo,
            turboMs = newTurbo,
            combo = combo,
            comboMs = comboMs,
            traffic = traffic,
            pickups = pickups,
            spawnTimerMs = spawnTimer,
            wavesSpawned = wavesSpawned,
            lastFreeLane = lastFreeLane,
            coinsCollected = coinsCollected,
            fuelCollected = fuelCollected,
            powerupsCollected = powerupsCollected,
            nearMisses = nearMisses,
            crashes = crashes,
            phase = phase,
            gameOverCause = cause,
        )
    }

    /** Test-only escape hatch: pins exact situations (a car overlapping the
     *  player, a coin a hair above it, a near-empty tank) that honest play
     *  can't set up deterministically. */
    internal fun forceForTest(
        traffic: List<TrafficCar>? = null,
        pickups: List<RacerPickup>? = null,
        fuel: Float? = null,
        lives: Int? = null,
        shieldActive: Boolean? = null,
        combo: Int? = null,
        comboMs: Long? = null,
        invulnMs: Long? = null,
    ) {
        state = state.copy(
            traffic = traffic ?: state.traffic,
            pickups = pickups ?: state.pickups,
            fuel = fuel ?: state.fuel,
            lives = lives ?: state.lives,
            shieldActive = shieldActive ?: state.shieldActive,
            combo = combo ?: state.combo,
            comboMs = comboMs ?: state.comboMs,
            invulnMs = invulnMs ?: state.invulnMs,
        )
    }

    private fun trafficKindForLevel(level: Int): TrafficKind {
        val roll = randomFloat()
        return when {
            level >= SPORT_FROM_LEVEL && roll < SPORT_SHARE -> TrafficKind.Sport
            level >= TRUCK_FROM_LEVEL && roll < SPORT_SHARE + TRUCK_SHARE -> TrafficKind.Truck
            else -> TrafficKind.Sedan
        }
    }

    private fun scrollSpeedForLevel(level: Int): Float =
        SCROLL_BASE_PER_SEC + (level - 1) * SCROLL_PER_LEVEL

    private fun spawnIntervalForLevel(level: Int): Long =
        (BASE_SPAWN_INTERVAL_MS - (level - 1) * SPAWN_INTERVAL_STEP_MS).coerceAtLeast(MIN_SPAWN_INTERVAL_MS)

    /** Center-based AABB overlap. */
    private fun overlaps(
        ax: Float, ay: Float, aw: Float, ah: Float,
        bx: Float, by: Float, bw: Float, bh: Float,
    ): Boolean = abs(ax - bx) <= (aw + bw) / 2f && abs(ay - by) <= (ah + bh) / 2f

    private fun initialState(): PixelRacerState = PixelRacerState(
        score = 0,
        distancePx = 0f,
        survivedMs = 0L,
        speedLevel = 1,
        lives = STARTING_LIVES,
        fuel = FUEL_MAX,
        lowFuelWarnings = 0,
        playerLane = 1,
        playerX = racerLaneCenterX(1),
        invulnMs = 0L,
        shieldActive = false,
        slowMoMs = 0L,
        turboMs = 0L,
        combo = 0,
        comboMs = 0L,
        traffic = emptyList(),
        pickups = emptyList(),
        spawnTimerMs = FIRST_SPAWN_DELAY_MS,
        wavesSpawned = 0,
        lastFreeLane = 1,
        coinsCollected = 0,
        fuelCollected = 0,
        powerupsCollected = 0,
        nearMisses = 0,
        crashes = 0,
        phase = PixelRacerPhase.Running,
    )

    companion object {
        const val MAX_DELTA_MS = 100L
        const val STARTING_LIVES = 3
        /** Road scroll in field units per second, before level/powerup scaling. */
        const val SCROLL_BASE_PER_SEC = 45f
        const val SCROLL_PER_LEVEL = 6f
        const val SPEED_LEVEL_EVERY_MS = 20_000L
        const val MAX_SPEED_LEVEL = 10
        /** Field units of scroll worth one distance point. */
        const val SCORE_PER_PX = 10f
        const val FUEL_MAX = 100f
        /** Full tank lasts ~45s without pickups. */
        const val FUEL_DRAIN_PER_SEC = 2.2f
        const val FUEL_PICKUP_AMOUNT = 35f
        const val LOW_FUEL_THRESHOLD = 25f
        const val LANE_SLIDE_PER_SEC = 160f
        const val BASE_SPAWN_INTERVAL_MS = 1_500L
        const val SPAWN_INTERVAL_STEP_MS = 90L
        const val MIN_SPAWN_INTERVAL_MS = 700L
        /** Breather before the first wave; also the tutorial-by-design beat. */
        const val FIRST_SPAWN_DELAY_MS = 1_200L
        const val TRUCK_FROM_LEVEL = 2
        const val SPORT_FROM_LEVEL = 4
        const val TRUCK_SHARE = 0.30f
        const val SPORT_SHARE = 0.25f
        const val COIN_SCORE = 50
        const val NEAR_MISS_SCORE = 25
        const val COMBO_MAX = 5
        const val COMBO_WINDOW_MS = 5_000L
        const val COIN_TRAIL_CHANCE = 0.4f
        const val COIN_TRAIL_LENGTH = 3
        const val COIN_TRAIL_GAP = 10f
        const val FUEL_WAVE_EVERY = 8
        const val POWERUP_WAVE_EVERY = 10
        const val SHIELD_DURATION_MS = 15_000L // reserved: shield currently lasts until spent
        const val SLOWMO_DURATION_MS = 6_000L
        const val SLOWMO_FACTOR = 0.55f
        const val TURBO_DURATION_MS = 5_000L
        const val TURBO_FACTOR = 1.6f
        const val TURBO_SCORE_MULT = 2
        const val INVULN_AFTER_CRASH_MS = 1_500L
        const val INVULN_AFTER_SHIELD_MS = 800L
        /** Total size shaved off both boxes in a collision test — grazing
         *  pixels shouldn't wreck anyone. */
        const val HITBOX_MERCY = 3f
    }
}

/** Effective score multiplier: a live streak multiplies, no streak means x1. */
fun PixelRacerState.multiplier(): Int = maxOf(combo, 1)
