package com.jasmeet.pixeldrop.galaxyminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyMinerEngineTest {
    private fun engine() = GalaxyMinerEngine(randomFloat = { 0.25f })

    @Test fun `fresh run begins docked with empty cargo and three hull`() {
        val state = engine().state
        assertEquals(MINER_STATION_X, state.shipX, 0.01f)
        assertEquals(MINER_STATION_Y, state.shipY, 0.01f)
        assertEquals(0, state.cargo)
        assertEquals(3, state.hull)
    }

    @Test fun `thrust accelerates and brake removes most drift`() {
        val engine = engine()
        engine.thrustRight()
        assertTrue(engine.state.velocityX > 0f)
        val speed = engine.state.velocityX
        engine.brake()
        assertTrue(engine.state.velocityX < speed / 2f)
    }

    @Test fun `mining collects nearest crystal once and arms cooldown`() {
        val engine = engine()
        engine.forceForTest(engine.state.copy(
            shipX = 40f, shipY = 40f, mineCooldownMs = 0,
            crystals = listOf(SpaceCrystal(99, 45f, 40f, 0f, 0f, 30)), mines = emptyList(),
        ))
        engine.mine()
        assertEquals(1, engine.state.cargo)
        assertEquals(30, engine.state.cargoValue)
        assertEquals(1, engine.state.crystalsMined)
        assertTrue(engine.state.crystals.isEmpty())
        val cargo = engine.state.cargo
        engine.mine()
        assertEquals(cargo, engine.state.cargo)
    }

    @Test fun `out of range crystal cannot be mined without tractor`() {
        val engine = engine()
        engine.forceForTest(engine.state.copy(
            shipX = 10f, shipY = 10f,
            crystals = listOf(SpaceCrystal(1, 35f, 10f, 0f, 0f, 20)), mines = emptyList(),
        ))
        engine.mine()
        assertEquals(0, engine.state.cargo)
        engine.forceForTest(engine.state.copy(tractorMs = 1_000L))
        engine.mine()
        assertEquals(1, engine.state.cargo)
    }

    @Test fun `docking banks cargo with greed multiplier`() {
        val engine = engine()
        engine.forceForTest(engine.state.copy(
            shipX = MINER_STATION_X, shipY = MINER_STATION_Y,
            cargo = 4, cargoValue = 100, mines = emptyList(), invulnerableMs = 1_000L,
        ))
        engine.tick(16)
        assertEquals(0, engine.state.cargo)
        assertEquals(300, engine.state.score)
        assertEquals(4, engine.state.bankedCrystals)
        assertEquals(1, engine.state.deposits)
    }

    @Test fun `mine impact loses hull and all risky cargo`() {
        val engine = engine()
        engine.forceForTest(engine.state.copy(
            shipX = 30f, shipY = 30f, cargo = 5, cargoValue = 150, invulnerableMs = 0,
            mines = listOf(SpaceMine(1, 30f, 30f, 0f, 0f)),
        ))
        engine.tick(16)
        assertEquals(2, engine.state.hull)
        assertEquals(0, engine.state.cargo)
        assertEquals(1, engine.state.hits)
        assertTrue(engine.state.invulnerableMs > 0)
    }

    @Test fun `shield absorbs exactly one mine impact`() {
        val engine = engine()
        engine.forceForTest(engine.state.copy(
            shipX = 30f, shipY = 30f, shieldActive = true, invulnerableMs = 0,
            mines = listOf(SpaceMine(1, 30f, 30f, 0f, 0f)),
        ))
        engine.tick(16)
        assertEquals(3, engine.state.hull)
        assertFalse(engine.state.shieldActive)
    }

    @Test fun `powerups activate and expire on simulation time`() {
        val engine = engine()
        engine.forceForTest(engine.state.copy(
            shipX = 30f, shipY = 30f, mines = emptyList(),
            powerUps = listOf(MinerPowerUp(1, MinerPowerType.SpeedBoost, 30f, 30f)),
        ))
        engine.tick(16)
        assertTrue(engine.state.speedBoostMs > 0)
        repeat(81) { engine.tick(100) }
        assertEquals(0L, engine.state.speedBoostMs)
    }

    @Test fun `difficulty level rises every six banked crystals`() {
        val engine = engine()
        engine.forceForTest(engine.state.copy(
            shipX = MINER_STATION_X, shipY = MINER_STATION_Y, cargo = 1, cargoValue = 20,
            bankedCrystals = 5, mines = emptyList(), invulnerableMs = 1_000L,
        ))
        engine.tick(16)
        assertEquals(2, engine.state.level)
        assertEquals(6, engine.state.bankedCrystals)
    }

    @Test fun `pause freezes and reset creates pristine run`() {
        val engine = engine()
        engine.thrustLeft()
        engine.pause()
        val paused = engine.state
        engine.tick(100)
        assertEquals(paused, engine.state)
        engine.reset()
        assertEquals(engine().state, engine.state)
    }
}
