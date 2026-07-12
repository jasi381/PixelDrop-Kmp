package com.jasmeet.pixeldrop.galaxyminer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * GALAXY MINER design contract and deterministic simulation.
 *
 * Fantasy: drift through a quiet crystal belt and return ore to a refinery.
 * Core decision: bank a safe load now or carry more for a cargo multiplier.
 * Controls: D-pad adds thrust, A mines the nearest in-range crystal, B brakes.
 * Collision with a mine loses one of three hull points and all unbanked cargo;
 * a shield absorbs one impact. Every six banked crystals raises the level,
 * mine speed and mine count. Tractor Beam doubles mining range and draws
 * crystals inward; Speed Boost increases thrust/top speed; Shield blocks once.
 * HUD: score, hull, level, cargo multiplier and active powerups. All positions,
 * collision, mining, banking, powerup expiry and difficulty rules are JVM-testable.
 */
class GalaxyMinerEngine(
    private val randomFloat: () -> Float = { Random.Default.nextFloat() },
) {
    private var nextCrystalId = 0
    private var nextMineId = 0
    private var nextPowerId = 0

    var state: GalaxyMinerState by mutableStateOf(initialState())
        private set

    fun thrustLeft() = thrust(-1f, 0f)
    fun thrustRight() = thrust(1f, 0f)
    fun thrustUp() = thrust(0f, -1f)
    fun thrustDown() = thrust(0f, 1f)

    private fun thrust(dx: Float, dy: Float) {
        val s = state
        if (s.phase != GalaxyMinerPhase.Running) return
        val impulse = if (s.speedBoostMs > 0) BOOST_THRUST_IMPULSE else THRUST_IMPULSE
        val maxSpeed = if (s.speedBoostMs > 0) BOOST_MAX_SPEED else MAX_SPEED
        state = s.copy(
            velocityX = (s.velocityX + dx * impulse).coerceIn(-maxSpeed, maxSpeed),
            velocityY = (s.velocityY + dy * impulse).coerceIn(-maxSpeed, maxSpeed),
        )
    }

    fun brake() {
        if (state.phase != GalaxyMinerPhase.Running) return
        state = state.copy(velocityX = state.velocityX * BRAKE_FACTOR, velocityY = state.velocityY * BRAKE_FACTOR)
    }

    fun mine() {
        val s = state
        if (s.phase != GalaxyMinerPhase.Running || s.mineCooldownMs > 0 || s.cargo >= MAX_CARGO) return
        val range = if (s.tractorMs > 0) TRACTOR_MINING_RANGE else MINING_RANGE
        var nearestIndex = -1
        var nearestDistanceSquared = range * range
        for (index in s.crystals.indices) {
            val crystal = s.crystals[index]
            val dx = crystal.x - s.shipX
            val dy = crystal.y - s.shipY
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared
                nearestIndex = index
            }
        }
        if (nearestIndex < 0) return
        val crystal = s.crystals[nearestIndex]
        val crystals = s.crystals.toMutableList().also { it.removeAt(nearestIndex) }
        val mined = s.crystalsMined + 1
        val powerUps = s.powerUps.toMutableList()
        if (mined % POWERUP_EVERY == 0) {
            val type = when ((mined / POWERUP_EVERY) % 3) {
                0 -> MinerPowerType.TractorBeam
                1 -> MinerPowerType.Shield
                else -> MinerPowerType.SpeedBoost
            }
            powerUps += MinerPowerUp(nextPowerId++, type, crystal.x, crystal.y)
        }
        state = s.copy(
            cargo = s.cargo + 1,
            cargoValue = s.cargoValue + crystal.value,
            crystals = crystals,
            powerUps = powerUps,
            crystalsMined = mined,
            mineCooldownMs = MINE_COOLDOWN_MS,
            beamMs = BEAM_FLASH_MS,
            beamX = crystal.x,
            beamY = crystal.y,
        )
    }

    fun pause() { if (state.phase == GalaxyMinerPhase.Running) state = state.copy(phase = GalaxyMinerPhase.Paused) }
    fun resume() { if (state.phase == GalaxyMinerPhase.Paused) state = state.copy(phase = GalaxyMinerPhase.Running) }
    fun reset() { nextCrystalId = 0; nextMineId = 0; nextPowerId = 0; state = initialState() }

    fun tick(deltaMs: Long) {
        val s = state
        if (s.phase != GalaxyMinerPhase.Running) return
        val clampedMs = deltaMs.coerceIn(0L, MAX_DELTA_MS)
        val dt = clampedMs / 1000f
        var velocityX = s.velocityX * DRAG
        var velocityY = s.velocityY * DRAG
        var shipX = s.shipX + velocityX * dt
        var shipY = s.shipY + velocityY * dt
        if (shipX < MINER_SHIP_RADIUS) { shipX = MINER_SHIP_RADIUS; velocityX = abs(velocityX) * WALL_BOUNCE }
        if (shipX > MINER_FIELD_WIDTH - MINER_SHIP_RADIUS) { shipX = MINER_FIELD_WIDTH - MINER_SHIP_RADIUS; velocityX = -abs(velocityX) * WALL_BOUNCE }
        if (shipY < MINER_SHIP_RADIUS) { shipY = MINER_SHIP_RADIUS; velocityY = abs(velocityY) * WALL_BOUNCE }
        if (shipY > MINER_FIELD_HEIGHT - MINER_SHIP_RADIUS) { shipY = MINER_FIELD_HEIGHT - MINER_SHIP_RADIUS; velocityY = -abs(velocityY) * WALL_BOUNCE }

        var crystals = s.crystals.map { crystal ->
            var vx = crystal.velocityX
            var vy = crystal.velocityY
            var x = crystal.x + vx * dt
            var y = crystal.y + vy * dt
            if (s.tractorMs > 0) {
                val dx = shipX - x
                val dy = shipY - y
                val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                if (distance < TRACTOR_PULL_RANGE) { x += dx / distance * TRACTOR_PULL_SPEED * dt; y += dy / distance * TRACTOR_PULL_SPEED * dt }
            }
            if (x !in CRYSTAL_MARGIN..(MINER_FIELD_WIDTH - CRYSTAL_MARGIN)) { vx = -vx; x = x.coerceIn(CRYSTAL_MARGIN, MINER_FIELD_WIDTH - CRYSTAL_MARGIN) }
            if (y !in CRYSTAL_MARGIN..(MINER_FIELD_HEIGHT - CRYSTAL_MARGIN)) { vy = -vy; y = y.coerceIn(CRYSTAL_MARGIN, MINER_FIELD_HEIGHT - CRYSTAL_MARGIN) }
            crystal.copy(x = x, y = y, velocityX = vx, velocityY = vy)
        }.toMutableList()

        val level = (1 + s.bankedCrystals / CRYSTALS_PER_LEVEL).coerceAtMost(MAX_LEVEL)
        var mines = s.mines.map { mine -> moveMine(mine, dt, level) }.toMutableList()
        while (crystals.size < crystalCountForLevel(level)) crystals += spawnCrystal(level)
        while (mines.size < mineCountForLevel(level)) mines += spawnMine(level)

        var tractorMs = (s.tractorMs - clampedMs).coerceAtLeast(0)
        var speedBoostMs = (s.speedBoostMs - clampedMs).coerceAtLeast(0)
        var shield = s.shieldActive
        var collectedPowerUps = s.powerUpsCollected
        val remainingPowerUps = mutableListOf<MinerPowerUp>()
        for (powerUp in s.powerUps) {
            if (overlaps(shipX, shipY, MINER_SHIP_RADIUS, powerUp.x, powerUp.y, MINER_POWER_SIZE / 2f)) {
                when (powerUp.type) {
                    MinerPowerType.TractorBeam -> tractorMs = POWER_DURATION_MS
                    MinerPowerType.Shield -> shield = true
                    MinerPowerType.SpeedBoost -> speedBoostMs = POWER_DURATION_MS
                }
                collectedPowerUps++
            } else remainingPowerUps += powerUp
        }

        var hull = s.hull
        var cargo = s.cargo
        var cargoValue = s.cargoValue
        var hits = s.hits
        var invulnerableMs = (s.invulnerableMs - clampedMs).coerceAtLeast(0)
        var phase = s.phase
        if (invulnerableMs == 0L && mines.any { overlaps(shipX, shipY, MINER_SHIP_RADIUS, it.x, it.y, MINER_MINE_RADIUS) }) {
            if (shield) { shield = false; invulnerableMs = SHIELD_MERCY_MS }
            else {
                hull--; hits++; cargo = 0; cargoValue = 0
                shipX = MINER_STATION_X; shipY = MINER_STATION_Y; velocityX = 0f; velocityY = 0f
                invulnerableMs = HIT_MERCY_MS
                if (hull <= 0) phase = GalaxyMinerPhase.GameOver
            }
        }

        var score = s.score
        var banked = s.bankedCrystals
        var deposits = s.deposits
        if (cargo > 0 && overlaps(shipX, shipY, MINER_SHIP_RADIUS, MINER_STATION_X, MINER_STATION_Y, STATION_RADIUS)) {
            score += cargoValue * (1 + cargo / 2).coerceAtMost(5)
            banked += cargo
            cargo = 0; cargoValue = 0; deposits++
        }

        state = s.copy(
            score = score, hull = hull, level = (1 + banked / CRYSTALS_PER_LEVEL).coerceAtMost(MAX_LEVEL),
            shipX = shipX, shipY = shipY, velocityX = velocityX, velocityY = velocityY,
            cargo = cargo, cargoValue = cargoValue, bankedCrystals = banked,
            elapsedMs = s.elapsedMs + clampedMs, mineCooldownMs = (s.mineCooldownMs - clampedMs).coerceAtLeast(0),
            beamMs = (s.beamMs - clampedMs).coerceAtLeast(0), tractorMs = tractorMs, speedBoostMs = speedBoostMs,
            shieldActive = shield, invulnerableMs = invulnerableMs,
            crystals = crystals, mines = mines, powerUps = remainingPowerUps,
            deposits = deposits, hits = hits, powerUpsCollected = collectedPowerUps, phase = phase,
        )
    }

    private fun moveMine(mine: SpaceMine, dt: Float, level: Int): SpaceMine {
        val factor = 1f + (level - 1) * MINE_SPEED_STEP
        var vx = mine.velocityX * factor
        var vy = mine.velocityY * factor
        var x = mine.x + vx * dt
        var y = mine.y + vy * dt
        if (x !in MINE_MARGIN..(MINER_FIELD_WIDTH - MINE_MARGIN)) { vx = -vx; x = x.coerceIn(MINE_MARGIN, MINER_FIELD_WIDTH - MINE_MARGIN) }
        if (y !in MINE_MARGIN..(MINER_FIELD_HEIGHT - MINE_MARGIN)) { vy = -vy; y = y.coerceIn(MINE_MARGIN, MINER_FIELD_HEIGHT - MINE_MARGIN) }
        return mine.copy(x = x, y = y, velocityX = vx / factor, velocityY = vy / factor)
    }

    private fun spawnCrystal(level: Int): SpaceCrystal {
        val angleX = randomFloat() * 2f - 1f
        val angleY = randomFloat() * 2f - 1f
        return SpaceCrystal(nextCrystalId++, randomX(), randomY(), angleX * CRYSTAL_SPEED, angleY * CRYSTAL_SPEED, BASE_CRYSTAL_VALUE + level * 5)
    }

    private fun spawnMine(level: Int): SpaceMine = SpaceMine(
        nextMineId++, randomX(), randomY(topOnly = true),
        (randomFloat() * 2f - 1f) * BASE_MINE_SPEED,
        (0.45f + randomFloat() * 0.55f) * BASE_MINE_SPEED,
    )

    private fun randomX(): Float = SPAWN_MARGIN + randomFloat() * (MINER_FIELD_WIDTH - SPAWN_MARGIN * 2f)
    private fun randomY(topOnly: Boolean = false): Float = SPAWN_MARGIN + randomFloat() * (if (topOnly) 65f else MINER_FIELD_HEIGHT - SPAWN_MARGIN * 2f)
    private fun crystalCountForLevel(level: Int) = (BASE_CRYSTALS + level / 2).coerceAtMost(MAX_CRYSTALS)
    private fun mineCountForLevel(level: Int) = (BASE_MINES + (level - 1) / 2).coerceAtMost(MAX_MINES)
    private fun overlaps(ax: Float, ay: Float, ar: Float, bx: Float, by: Float, br: Float): Boolean {
        val dx = ax - bx; val dy = ay - by; val radius = ar + br
        return dx * dx + dy * dy < radius * radius
    }

    private fun initialState(): GalaxyMinerState {
        val crystals = MutableList(BASE_CRYSTALS) { spawnCrystal(1) }
        val mines = MutableList(BASE_MINES) { spawnMine(1) }
        return GalaxyMinerState(
            score = 0, hull = STARTING_HULL, level = 1, shipX = MINER_STATION_X, shipY = MINER_STATION_Y,
            velocityX = 0f, velocityY = 0f, cargo = 0, cargoValue = 0, bankedCrystals = 0,
            elapsedMs = 0, mineCooldownMs = 0, beamMs = 0, beamX = 0f, beamY = 0f,
            tractorMs = 0, speedBoostMs = 0, shieldActive = false, invulnerableMs = START_MERCY_MS,
            crystals = crystals, mines = mines, powerUps = emptyList(), crystalsMined = 0, deposits = 0,
            hits = 0, powerUpsCollected = 0,
        )
    }

    internal fun forceForTest(state: GalaxyMinerState) { this.state = state }

    companion object {
        const val STARTING_HULL = 3; const val MAX_LEVEL = 8; const val MAX_CARGO = 10
        const val CRYSTALS_PER_LEVEL = 6; const val BASE_CRYSTAL_VALUE = 20; const val POWERUP_EVERY = 4
        const val BASE_CRYSTALS = 6; const val MAX_CRYSTALS = 10; const val BASE_MINES = 2; const val MAX_MINES = 6
        const val THRUST_IMPULSE = 5.5f; const val BOOST_THRUST_IMPULSE = 8f; const val MAX_SPEED = 24f; const val BOOST_MAX_SPEED = 34f
        const val BRAKE_FACTOR = 0.35f; const val DRAG = 0.992f; const val WALL_BOUNCE = 0.65f
        const val MINING_RANGE = 15f; const val TRACTOR_MINING_RANGE = 29f; const val TRACTOR_PULL_RANGE = 35f; const val TRACTOR_PULL_SPEED = 8f
        const val CRYSTAL_SPEED = 1.8f; const val BASE_MINE_SPEED = 5f; const val MINE_SPEED_STEP = 0.08f
        const val MINE_COOLDOWN_MS = 260L; const val BEAM_FLASH_MS = 140L; const val POWER_DURATION_MS = 8_000L
        const val START_MERCY_MS = 1_000L; const val HIT_MERCY_MS = 1_500L; const val SHIELD_MERCY_MS = 500L; const val MAX_DELTA_MS = 100L
        const val STATION_RADIUS = 6f; const val CRYSTAL_MARGIN = 3f; const val MINE_MARGIN = 4f; const val SPAWN_MARGIN = 8f
    }
}
