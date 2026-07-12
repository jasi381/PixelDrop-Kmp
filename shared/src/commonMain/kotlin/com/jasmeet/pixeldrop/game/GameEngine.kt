package com.jasmeet.pixeldrop.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Deterministic block-drop engine, independent of any UI drawing.
 * Pass a custom [pieceSource] for tests; defaults to a uniform random bag.
 */
class GameEngine(
    private val pieceSource: () -> PieceType = { PieceType.entries.random() },
) {
    var state: GameState by mutableStateOf(newGame())
        private set

    fun reset() {
        state = newGame()
    }

    fun moveLeft() {
        tryMove(0, -1)
    }

    fun moveRight() {
        tryMove(0, 1)
    }

    fun softDrop() {
        if (state.phase != GamePhase.Running) return
        if (tryMove(1, 0)) {
            state = state.copy(score = state.score + 1)
        } else {
            lockPiece()
        }
    }

    fun hardDrop() {
        if (state.phase != GamePhase.Running) return
        while (tryMove(1, 0)) Unit
        lockPiece()
    }

    fun rotateCw() = rotate(1)

    fun rotateCcw() = rotate(-1)

    fun tick() {
        if (state.phase != GamePhase.Running) return
        if (!tryMove(1, 0)) lockPiece()
    }

    fun pause() {
        if (state.phase == GamePhase.Running) state = state.copy(phase = GamePhase.Paused)
    }

    fun resume() {
        if (state.phase == GamePhase.Paused) state = state.copy(phase = GamePhase.Running)
    }

    private fun rotate(direction: Int) {
        if (state.phase != GamePhase.Running) return
        val rotated = state.current.rotated(direction)
        // Simple wall kicks: try in place, then nudge sideways.
        for (kick in intArrayOf(0, -1, 1, -2, 2)) {
            val candidate = rotated.moved(0, kick)
            if (!Board.collides(state.board, candidate)) {
                state = state.copy(current = candidate)
                return
            }
        }
    }

    private fun tryMove(dr: Int, dc: Int): Boolean {
        if (state.phase != GamePhase.Running) return false
        val moved = state.current.moved(dr, dc)
        if (Board.collides(state.board, moved)) return false
        state = state.copy(current = moved)
        return true
    }

    private fun lockPiece() {
        val merged = Board.merge(state.board, state.current)
        val (cleared, count) = Board.clearFullLines(merged)
        val lineScore = intArrayOf(0, 100, 300, 500, 800)[count] * state.level
        val totalLines = state.lines + count
        val spawned = spawn(state.next)
        val blocked = Board.collides(cleared, spawned)
        state = state.copy(
            board = cleared,
            current = spawned,
            next = pieceSource(),
            score = state.score + lineScore,
            lines = totalLines,
            level = totalLines / 10 + 1,
            phase = if (blocked) GamePhase.GameOver else GamePhase.Running,
        )
    }

    private fun spawn(type: PieceType): Piece =
        Piece(type = type, rotation = 0, row = 0, col = (Board.WIDTH - type.boxSize) / 2)

    private fun newGame(): GameState =
        GameState(board = Board.empty(), current = spawn(pieceSource()), next = pieceSource())
}
