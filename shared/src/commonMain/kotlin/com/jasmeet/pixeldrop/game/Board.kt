package com.jasmeet.pixeldrop.game

/** Pure board helpers so collision / merge / clear logic stays testable. */
object Board {
    const val WIDTH = 10
    const val HEIGHT = 20

    fun empty(): Array<IntArray> = Array(HEIGHT) { IntArray(WIDTH) }

    fun collides(board: Array<IntArray>, piece: Piece): Boolean =
        piece.cells().any { (r, c) ->
            c < 0 || c >= WIDTH || r >= HEIGHT || (r >= 0 && board[r][c] != 0)
        }

    fun merge(board: Array<IntArray>, piece: Piece): Array<IntArray> {
        val next = Array(HEIGHT) { board[it].copyOf() }
        piece.cells().forEach { (r, c) ->
            if (r in 0 until HEIGHT && c in 0 until WIDTH) next[r][c] = 1
        }
        return next
    }

    /** Returns the compacted board plus how many full lines were removed. */
    fun clearFullLines(board: Array<IntArray>): Pair<Array<IntArray>, Int> {
        val kept = board.filter { row -> row.any { it == 0 } }
        val cleared = HEIGHT - kept.size
        if (cleared == 0) return board to 0
        val next = Array(HEIGHT) { IntArray(WIDTH) }
        kept.forEachIndexed { i, row -> next[cleared + i] = row.copyOf() }
        return next to cleared
    }
}
