package com.jasmeet.pixeldrop.pixeltrail

data class GridPoint(val x: Int, val y: Int)

enum class TrailDirection { Up, Down, Left, Right }

enum class PixelTrailPhase { Running, Paused, GameOver }

data class PixelTrailState(
    val gridWidth: Int,
    val gridHeight: Int,
    /** Head first, tail last. */
    val trail: List<GridPoint>,
    val direction: TrailDirection,
    val pendingDirection: TrailDirection,
    val food: GridPoint,
    val score: Int,
    val speed: Int,
    val foodCollected: Int,
    val phase: PixelTrailPhase = PixelTrailPhase.Running,
) {
    val tickMillis: Long
        get() = (220L - (speed - 1) * 15L).coerceAtLeast(90L)
}
