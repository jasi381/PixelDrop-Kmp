package com.jasmeet.pixeldrop.game

enum class GamePhase { Running, Paused, GameOver }

data class GameState(
    val board: Array<IntArray>,
    val current: Piece,
    val next: PieceType,
    val score: Int = 0,
    val level: Int = 1,
    val lines: Int = 0,
    val phase: GamePhase = GamePhase.Running,
) {
    val tickMillis: Long
        get() = (700L - (level - 1) * 60L).coerceAtLeast(120L)
}
