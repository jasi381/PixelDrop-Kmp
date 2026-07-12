package com.jasmeet.pixeldrop.pixelracer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelRacerEngineTest {

    private fun tickMany(engine: PixelRacerEngine, times: Int, deltaMs: Long = 16) {
        repeat(times) { engine.tick(deltaMs) }
    }

    /** Rolls of 0.1 keep spawns deterministic: the wave's free lane drifts
     *  by (0.1*3).toInt()-1 = -1 each wave, cars are Sedans, and no coin
     *  trail spawns (0.1 < 0.4 does spawn coins — see tests that rely on it). */
    private fun engine(roll: Float = 0.1f) = PixelRacerEngine(randomFloat = { roll })

    private fun sedanAt(lane: Int, y: Float, id: Int = 900) =
        TrafficCar(id = id, lane = lane, y = y, kind = TrafficKind.Sedan)

    @Test
    fun `initial state is a fresh full-tank three-lives run`() {
        val s = engine().state
        assertEquals(0, s.score)
        assertEquals(3, s.lives)
        assertEquals(PixelRacerEngine.FUEL_MAX, s.fuel, 0.01f)
        assertEquals(1, s.speedLevel)
        assertEquals(1, s.playerLane)
        assertEquals(racerLaneCenterX(1), s.playerX, 0.01f)
        assertEquals(PixelRacerPhase.Running, s.phase)
        assertNull(s.gameOverCause)
        assertTrue(s.traffic.isEmpty())
        assertTrue(s.pickups.isEmpty())
    }

    @Test
    fun `steering clamps to the four lanes`() {
        val e = engine()
        repeat(10) { e.moveLeft() }
        assertEquals(0, e.state.playerLane)
        repeat(10) { e.moveRight() }
        assertEquals(RACER_LANE_COUNT - 1, e.state.playerLane)
    }

    @Test
    fun `player slides to the target lane center over time`() {
        val e = engine()
        e.moveRight() // lane 1 -> 2
        tickMany(e, 20)
        assertEquals(racerLaneCenterX(2), e.state.playerX, 0.01f)
    }

    @Test
    fun `fuel drains over time and an empty tank ends the run as OUT OF FUEL`() {
        val e = engine()
        e.forceForTest(fuel = 1f)
        tickMany(e, 60) // ~1s at 2.2 fuel/s
        assertEquals(PixelRacerPhase.GameOver, e.state.phase)
        assertEquals(RacerGameOverCause.OutOfFuel, e.state.gameOverCause)
    }

    @Test
    fun `low fuel warns exactly once per threshold crossing`() {
        val e = engine()
        e.forceForTest(fuel = PixelRacerEngine.LOW_FUEL_THRESHOLD + 0.1f)
        tickMany(e, 20)
        assertEquals(1, e.state.lowFuelWarnings)
        tickMany(e, 20)
        assertEquals("no repeat while still low", 1, e.state.lowFuelWarnings)
    }

    @Test
    fun `fuel pickup refills capped at the tank size`() {
        val e = engine()
        e.forceForTest(
            fuel = 90f,
            pickups = listOf(RacerPickup(1, 1, RACER_PLAYER_Y - 1f, RacerPickupKind.Fuel)),
        )
        e.tick(16)
        assertEquals(PixelRacerEngine.FUEL_MAX, e.state.fuel, 0.5f)
        assertEquals(1, e.state.fuelCollected)
        assertTrue("pickup consumed", e.state.pickups.isEmpty())
    }

    @Test
    fun `collision costs a life, resets the combo and grants a mercy window`() {
        val e = engine()
        e.forceForTest(combo = 3, comboMs = 5_000L, traffic = listOf(sedanAt(1, RACER_PLAYER_Y)))
        e.tick(16)
        val s = e.state
        assertEquals(2, s.lives)
        assertEquals(1, s.crashes)
        assertEquals(0, s.combo)
        assertTrue("mercy window armed", s.invulnMs > 0)
        assertEquals(PixelRacerPhase.Running, s.phase)
    }

    @Test
    fun `mercy window ignores a second overlapping car`() {
        val e = engine()
        e.forceForTest(traffic = listOf(sedanAt(1, RACER_PLAYER_Y)))
        e.tick(16)
        assertEquals(2, e.state.lives)
        e.forceForTest(traffic = listOf(sedanAt(1, RACER_PLAYER_Y, id = 901)))
        e.tick(16)
        assertEquals("still two lives inside the window", 2, e.state.lives)
    }

    @Test
    fun `third crash wrecks the run`() {
        val e = engine()
        repeat(3) { i ->
            e.forceForTest(invulnMs = 0L, traffic = listOf(sedanAt(1, RACER_PLAYER_Y, id = 900 + i)))
            e.tick(16)
        }
        assertEquals(0, e.state.lives)
        assertEquals(PixelRacerPhase.GameOver, e.state.phase)
        assertEquals(RacerGameOverCause.Wrecked, e.state.gameOverCause)
    }

    @Test
    fun `shield absorbs exactly one hit`() {
        val e = engine()
        e.forceForTest(shieldActive = true, traffic = listOf(sedanAt(1, RACER_PLAYER_Y)))
        e.tick(16)
        assertEquals("no life lost", 3, e.state.lives)
        assertTrue("shield spent", !e.state.shieldActive)
        // Second hit after the brief shield-break window is a real crash.
        e.forceForTest(invulnMs = 0L, traffic = listOf(sedanAt(1, RACER_PLAYER_Y, id = 901)))
        e.tick(16)
        assertEquals(2, e.state.lives)
    }

    @Test
    fun `adjacent-lane pass scores a near miss and builds the combo`() {
        val e = engine()
        // Car one lane over, just above the player row; one tick carries it past.
        e.forceForTest(traffic = listOf(sedanAt(2, RACER_PLAYER_Y - 0.2f)))
        e.tick(16)
        val s = e.state
        assertEquals(1, s.nearMisses)
        assertEquals(1, s.combo)
        assertTrue("near-miss points banked", s.score >= PixelRacerEngine.NEAR_MISS_SCORE)
    }

    @Test
    fun `same-lane or distant passes score no near miss`() {
        val e = engine()
        // Two lanes away — a comfortable pass, not a near miss.
        e.forceForTest(traffic = listOf(sedanAt(3, RACER_PLAYER_Y - 0.2f)))
        e.tick(16)
        assertEquals(0, e.state.nearMisses)
    }

    @Test
    fun `combo decays after its window lapses`() {
        val e = engine()
        e.forceForTest(combo = 4, comboMs = 100L)
        tickMany(e, 10)
        assertEquals(0, e.state.combo)
        assertEquals("effective multiplier floors at x1", 1, e.state.multiplier())
    }

    @Test
    fun `coin is collected exactly once and pays the combo multiplier`() {
        val e = engine()
        e.forceForTest(
            combo = 3, comboMs = 5_000L,
            pickups = listOf(RacerPickup(1, 1, RACER_PLAYER_Y - 1f, RacerPickupKind.Coin)),
        )
        val before = e.state.score
        e.tick(16)
        assertEquals(1, e.state.coinsCollected)
        assertTrue(e.state.pickups.isEmpty())
        assertTrue(
            "coin paid 50 x combo",
            e.state.score - before >= PixelRacerEngine.COIN_SCORE * 3,
        )
    }

    @Test
    fun `slow-mo drags the road and expires`() {
        val e = engine()
        val plainStart = e.state.distancePx
        tickMany(e, 10)
        val plainGain = e.state.distancePx - plainStart

        val slow = engine()
        slow.forceForTest(pickups = listOf(RacerPickup(1, 1, RACER_PLAYER_Y - 1f, RacerPickupKind.SlowMo)))
        slow.tick(16) // collect
        val slowStart = slow.state.distancePx
        tickMany(slow, 10)
        val slowGain = slow.state.distancePx - slowStart

        assertTrue("slow-mo road moves slower", slowGain < plainGain * 0.7f)
        assertTrue(slow.state.slowMoMs > 0)
        tickMany(slow, 400) // ride out the 6s
        assertEquals(0L, slow.state.slowMoMs)
    }

    @Test
    fun `turbo speeds the road up and doubles distance points`() {
        val plain = engine()
        tickMany(plain, 60)
        val plainScore = plain.state.score

        val turbo = engine()
        turbo.forceForTest(pickups = listOf(RacerPickup(1, 1, RACER_PLAYER_Y - 1f, RacerPickupKind.Turbo)))
        turbo.tick(16)
        tickMany(turbo, 60)
        // Same wall time: turbo covers 1.6x road and pays 2x per unit.
        assertTrue("turbo outscores plain running", turbo.state.score > plainScore * 2)
    }

    @Test
    fun `difficulty steps every 20 seconds and speeds the scroll`() {
        val e = engine()
        // With rolls of 0.1 the wave spawner parks the free lane at 0 and
        // drops every car into lane 1 — steer to lane 0 first or the run is
        // wrecked long before the 20s level-up this test waits for.
        e.moveLeft()
        tickMany(e, 10)
        val d1Start = e.state.distancePx
        tickMany(e, 10)
        val d1 = e.state.distancePx - d1Start
        // Jump the clock: 20s of 100ms max-clamp ticks.
        repeat(200) { e.tick(100) }
        assertEquals(PixelRacerPhase.Running, e.state.phase)
        assertEquals(2, e.state.speedLevel)
        // The lane-0 pickup line means the player may have just grabbed a
        // powerup (wave 10 hands out slow-mo); ride it out and clear the
        // conveyor so the speed comparison measures the bare road.
        while (e.state.slowMoMs > 0 || e.state.turboMs > 0) e.tick(16)
        e.forceForTest(pickups = emptyList())
        val before = e.state.distancePx
        tickMany(e, 10)
        val d2 = e.state.distancePx - before
        assertTrue("level 2 road is faster", d2 > d1 - 0.01f)
    }

    @Test
    fun `delta clamp survives an app-switch style mega frame`() {
        val e = engine()
        e.tick(10_000) // one absurd frame
        assertTrue("clock only advanced by the clamp", e.state.survivedMs <= PixelRacerEngine.MAX_DELTA_MS)
        assertTrue("fuel only drained a sliver", e.state.fuel > PixelRacerEngine.FUEL_MAX - 1f)
    }

    @Test
    fun `waves always leave the free lane within one lane of the previous`() {
        // Random rolls at the extremes push the free-lane drift hardest.
        for (roll in listOf(0.0f, 0.45f, 0.99f)) {
            val e = engine(roll)
            var lastFree = e.state.lastFreeLane
            repeat(4_000) {
                e.tick(16)
                val s = e.state
                if (s.phase != PixelRacerPhase.Running) return@repeat
                val free = s.lastFreeLane
                assertTrue("free lane in range", free in 0 until RACER_LANE_COUNT)
                assertTrue("free lane drifts at most one lane", abs(free - lastFree) <= 1)
                lastFree = free
            }
        }
    }

    @Test
    fun `spawned waves never fill all four lanes`() {
        val e = engine(0.99f)
        repeat(2_000) {
            e.tick(16)
            val s = e.state
            // Look only at freshly spawned rows (top of the field): the
            // fairness invariant is about what a wave adds.
            val topLanes = s.traffic.filter { it.y < 10f }.map { it.lane }.toSet()
            assertTrue("at least one open lane at the top", topLanes.size < RACER_LANE_COUNT)
        }
    }

    @Test
    fun `pause freezes the world completely`() {
        val e = engine()
        tickMany(e, 100)
        e.pause()
        val frozen = e.state
        tickMany(e, 100)
        assertEquals("no tick advanced anything while paused", frozen, e.state)
        e.resume()
        tickMany(e, 10)
        assertTrue(e.state.survivedMs > frozen.survivedMs)
    }

    @Test
    fun `reset yields a pristine world after a messy run`() {
        val e = engine()
        tickMany(e, 500)
        e.forceForTest(traffic = listOf(sedanAt(1, RACER_PLAYER_Y)))
        e.tick(16) // crash
        e.reset()
        val fresh = PixelRacerEngine(randomFloat = { 0.1f }).state
        assertEquals("second run starts identical to a first run", fresh, e.state)
    }

    @Test
    fun `steering while paused or wrecked is ignored`() {
        val e = engine()
        e.pause()
        e.moveRight()
        assertEquals(1, e.state.playerLane)
        e.resume()
        e.forceForTest(fuel = 0.01f)
        e.tick(100)
        assertEquals(PixelRacerPhase.GameOver, e.state.phase)
        e.moveRight()
        assertEquals(1, e.state.playerLane)
    }
}
