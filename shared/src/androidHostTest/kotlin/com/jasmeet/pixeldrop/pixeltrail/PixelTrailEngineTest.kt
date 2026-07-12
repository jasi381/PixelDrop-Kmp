package com.jasmeet.pixeldrop.pixeltrail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PixelTrailEngineTest {

    /** Always resolves to the first empty cell in raster order — for an
     *  18x18 grid with a center-ish trail that's (0,0), safely out of the
     *  way of the short movement paths these tests drive. Keeps
     *  wall/self-collision assertions independent of RNG. */
    private class FirstCellRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(until: Int): Int = 0
    }

    @Test
    fun `initial state has trail, food and zero score`() {
        val engine = PixelTrailEngine(random = FirstCellRandom())
        assertEquals(4, engine.state.trail.size)
        assertEquals(0, engine.state.score)
        assertEquals(PixelTrailPhase.Running, engine.state.phase)
        assertTrue(engine.state.food !in engine.state.trail)
    }

    @Test
    fun `direction cannot reverse directly`() {
        val engine = PixelTrailEngine(random = FirstCellRandom()) // starts moving Right
        engine.changeDirection(TrailDirection.Left)
        assertEquals(TrailDirection.Right, engine.state.pendingDirection)
    }

    @Test
    fun `tick moves head by one cell`() {
        val engine = PixelTrailEngine(random = FirstCellRandom())
        val head = engine.state.trail.first()
        engine.tick()
        assertEquals(GridPoint(head.x + 1, head.y), engine.state.trail.first())
    }

    @Test
    fun `food collection increases score and length`() {
        // Default RNG here — the walk below reaches wherever food actually
        // landed, so the test doesn't need to control its position.
        val engine = PixelTrailEngine(gridWidth = 18, gridHeight = 18)
        val lengthBefore = engine.state.trail.size
        val target = engine.state.food

        walkTo(engine, target)

        assertEquals(10, engine.state.score)
        assertEquals(lengthBefore + 1, engine.state.trail.size)
        assertEquals(1, engine.state.foodCollected)
    }

    @Test
    fun `wall collision causes game over`() {
        val engine = PixelTrailEngine(gridWidth = 18, gridHeight = 18, random = FirstCellRandom())
        // Head starts at col 9 moving Right; 9 ticks crosses the right wall (col 18).
        repeat(9) { engine.tick() }
        assertEquals(PixelTrailPhase.GameOver, engine.state.phase)
    }

    @Test
    fun `self collision causes game over`() {
        val engine = PixelTrailEngine(gridWidth = 18, gridHeight = 18)
        // A snake exactly as long as a 1x1 loop's perimeter (4) can chase its
        // own tail around that loop forever without colliding, so grow past
        // that first, then trace a tight clockwise square back into the body.
        walkTo(engine, engine.state.food)
        assertEquals(5, engine.state.trail.size)

        repeat(4) {
            engine.changeDirection(clockwise(engine.state.direction))
            engine.tick()
        }
        assertEquals(PixelTrailPhase.GameOver, engine.state.phase)
    }

    @Test
    fun `food spawns outside the trail`() {
        val engine = PixelTrailEngine(random = FirstCellRandom())
        repeat(20) {
            assertTrue(engine.state.food !in engine.state.trail)
            if (engine.state.phase == PixelTrailPhase.Running) engine.tick() else engine.reset()
        }
    }

    private fun opposite(direction: TrailDirection): TrailDirection = when (direction) {
        TrailDirection.Up -> TrailDirection.Down
        TrailDirection.Down -> TrailDirection.Up
        TrailDirection.Left -> TrailDirection.Right
        TrailDirection.Right -> TrailDirection.Left
    }

    private fun clockwise(direction: TrailDirection): TrailDirection = when (direction) {
        TrailDirection.Right -> TrailDirection.Down
        TrailDirection.Down -> TrailDirection.Left
        TrailDirection.Left -> TrailDirection.Up
        TrailDirection.Up -> TrailDirection.Right
    }

    private fun perpendicular(direction: TrailDirection): TrailDirection = when (direction) {
        TrailDirection.Right, TrailDirection.Left -> TrailDirection.Down
        TrailDirection.Up, TrailDirection.Down -> TrailDirection.Right
    }

    /** Ticks [engine] toward [target], one cell per call, until the head
     *  arrives. Prefers whichever axis isn't an instant reversal of the
     *  current heading; if both are (target directly behind, same row/col),
     *  takes a perpendicular detour for one tick to break the deadlock. */
    private fun walkTo(engine: PixelTrailEngine, target: GridPoint) {
        var guard = 0
        while (engine.state.trail.first() != target) {
            val head = engine.state.trail.first()
            val cur = engine.state.direction
            val wantX = when {
                head.x < target.x -> TrailDirection.Right
                head.x > target.x -> TrailDirection.Left
                else -> null
            }
            val wantY = when {
                head.y < target.y -> TrailDirection.Down
                head.y > target.y -> TrailDirection.Up
                else -> null
            }
            val choice = listOfNotNull(wantX, wantY).firstOrNull { it != opposite(cur) }
                ?: perpendicular(cur)
            engine.changeDirection(choice)
            engine.tick()

            guard++
            check(guard < 200) { "did not reach $target within 200 ticks" }
            check(engine.state.phase == PixelTrailPhase.Running) { "unexpected game over while walking to $target" }
        }
    }
}
