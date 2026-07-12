package com.jasmeet.pixeldrop.pixeltrail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * Deterministic grid-survival engine, independent of any UI drawing.
 * Pass a custom [random] for tests.
 */
class PixelTrailEngine(
    private val gridWidth: Int = 18,
    private val gridHeight: Int = 18,
    private val random: Random = Random.Default,
) {
    var state: PixelTrailState by mutableStateOf(newGame())
        private set

    fun reset() {
        state = newGame()
    }

    /** Direction change is buffered into [PixelTrailState.pendingDirection] and
     *  applied on the next [tick] — an instant reversal into the neck is ignored. */
    fun changeDirection(direction: TrailDirection) {
        if (state.phase != PixelTrailPhase.Running) return
        if (isOpposite(direction, state.direction)) return
        state = state.copy(pendingDirection = direction)
    }

    fun pause() {
        if (state.phase == PixelTrailPhase.Running) state = state.copy(phase = PixelTrailPhase.Paused)
    }

    fun resume() {
        if (state.phase == PixelTrailPhase.Paused) state = state.copy(phase = PixelTrailPhase.Running)
    }

    fun tick() {
        if (state.phase != PixelTrailPhase.Running) return
        val dir = state.pendingDirection
        val newHead = state.trail.first().moved(dir)

        if (isWallCollision(newHead)) {
            state = state.copy(phase = PixelTrailPhase.GameOver)
            return
        }
        val ateFood = newHead == state.food
        // The tail cell vacates this tick unless the trail is growing, in
        // which case it stays put and counts toward a self-collision.
        val bodyAfterMove = if (ateFood) state.trail else state.trail.dropLast(1)
        if (bodyAfterMove.contains(newHead)) {
            state = state.copy(phase = PixelTrailPhase.GameOver)
            return
        }

        val newTrail = listOf(newHead) + bodyAfterMove
        if (ateFood) {
            val newFoodCollected = state.foodCollected + 1
            state = state.copy(
                trail = newTrail,
                direction = dir,
                food = spawnFood(newTrail),
                score = state.score + 10 + if (newFoodCollected % 5 == 0) 50 else 0,
                speed = newFoodCollected / 5 + 1,
                foodCollected = newFoodCollected,
            )
        } else {
            state = state.copy(trail = newTrail, direction = dir)
        }
    }

    /** True if [point] is out of bounds or already occupied by the body
     *  (ignoring the tail cell, which vacates on the next move). */
    fun isCollision(point: GridPoint): Boolean =
        isWallCollision(point) || state.trail.dropLast(1).contains(point)

    private fun isWallCollision(point: GridPoint): Boolean =
        point.x < 0 || point.x >= gridWidth || point.y < 0 || point.y >= gridHeight

    private fun spawnFood(occupied: List<GridPoint>): GridPoint {
        val occupiedSet = occupied.toSet()
        val empty = (0 until gridWidth).flatMap { x ->
            (0 until gridHeight).mapNotNull { y ->
                GridPoint(x, y).takeIf { it !in occupiedSet }
            }
        }
        return if (empty.isEmpty()) occupied.first() else empty[random.nextInt(empty.size)]
    }

    private fun newGame(): PixelTrailState {
        val startX = gridWidth / 2
        val startY = gridHeight / 2
        val trail = listOf(
            GridPoint(startX, startY),
            GridPoint(startX - 1, startY),
            GridPoint(startX - 2, startY),
            GridPoint(startX - 3, startY),
        )
        return PixelTrailState(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            trail = trail,
            direction = TrailDirection.Right,
            pendingDirection = TrailDirection.Right,
            food = spawnFood(trail),
            score = 0,
            speed = 1,
            foodCollected = 0,
            phase = PixelTrailPhase.Running,
        )
    }
}

private fun GridPoint.moved(direction: TrailDirection): GridPoint = when (direction) {
    TrailDirection.Up -> copy(y = y - 1)
    TrailDirection.Down -> copy(y = y + 1)
    TrailDirection.Left -> copy(x = x - 1)
    TrailDirection.Right -> copy(x = x + 1)
}

private fun isOpposite(a: TrailDirection, b: TrailDirection): Boolean = when (a) {
    TrailDirection.Up -> b == TrailDirection.Down
    TrailDirection.Down -> b == TrailDirection.Up
    TrailDirection.Left -> b == TrailDirection.Right
    TrailDirection.Right -> b == TrailDirection.Left
}
