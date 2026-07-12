package com.jasmeet.pixeldrop.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private fun engineOf(vararg types: PieceType): GameEngine {
        var i = 0
        return GameEngine(pieceSource = { types[i++ % types.size] })
    }

    @Test
    fun `piece stops at left wall`() {
        val engine = engineOf(PieceType.CUBE)
        repeat(20) { engine.moveLeft() }
        val minCol = engine.state.current.cells().minOf { it.second }
        assertEquals(0, minCol)
    }

    @Test
    fun `rotation with kicks keeps piece inside bounds`() {
        val engine = engineOf(PieceType.BAR)
        engine.rotateCw() // vertical
        repeat(20) { engine.moveLeft() }
        engine.rotateCw() // back to horizontal at the wall, needs a kick
        engine.state.current.cells().forEach { (r, c) ->
            assertTrue("row $r out of bounds", r in 0 until Board.HEIGHT)
            assertTrue("col $c out of bounds", c in 0 until Board.WIDTH)
        }
    }

    @Test
    fun `five cubes clear two lines and score double-line points`() {
        val engine = engineOf(PieceType.CUBE)
        // Cube spawns at col 4; park pairs at columns 0,2,4,6,8.
        for (target in intArrayOf(0, 2, 4, 6, 8)) {
            val delta = target - 4
            repeat(kotlin.math.abs(delta)) {
                if (delta < 0) engine.moveLeft() else engine.moveRight()
            }
            engine.hardDrop()
        }
        assertEquals(2, engine.state.lines)
        assertEquals(300, engine.state.score) // 2 lines at level 1
        assertTrue(engine.state.board.all { row -> row.all { it == 0 } })
    }

    @Test
    fun `bars clear a single line for 100 points`() {
        val engine = engineOf(PieceType.BAR)
        // Two horizontal bars cover columns 0..7 of the bottom row.
        repeat(3) { engine.moveLeft() }
        engine.hardDrop()
        engine.moveRight()
        engine.hardDrop()
        // Two vertical bars fill columns 8 and 9.
        engine.rotateCw()
        repeat(3) { engine.moveRight() }
        engine.hardDrop()
        engine.rotateCw()
        repeat(4) { engine.moveRight() }
        engine.hardDrop()
        assertEquals(1, engine.state.lines)
        assertEquals(100, engine.state.score)
    }

    @Test
    fun `soft drop scores one point per cell`() {
        val engine = engineOf(PieceType.CUBE)
        engine.softDrop()
        assertEquals(1, engine.state.score)
    }

    @Test
    fun `game over when spawn area is blocked`() {
        val engine = engineOf(PieceType.CUBE)
        repeat(12) {
            if (engine.state.phase == GamePhase.Running) engine.hardDrop()
        }
        assertEquals(GamePhase.GameOver, engine.state.phase)
    }

    @Test
    fun `level rises every ten lines and speeds up tick`() {
        val state = GameState(
            board = Board.empty(),
            current = Piece(PieceType.CUBE),
            next = PieceType.CUBE,
            level = 3,
        )
        assertTrue(state.tickMillis < 700L)
        assertEquals(700L - 2 * 60L, state.tickMillis)
    }

    @Test
    fun `clearFullLines compacts board downward`() {
        val board = Board.empty()
        board[Board.HEIGHT - 1] = IntArray(Board.WIDTH) { 1 } // full bottom row
        board[Board.HEIGHT - 2][0] = 1 // one block above it
        val (cleared, count) = Board.clearFullLines(board)
        assertEquals(1, count)
        assertEquals(1, cleared[Board.HEIGHT - 1][0]) // block fell one row
        assertTrue(cleared[Board.HEIGHT - 1].drop(1).all { it == 0 })
    }
}
