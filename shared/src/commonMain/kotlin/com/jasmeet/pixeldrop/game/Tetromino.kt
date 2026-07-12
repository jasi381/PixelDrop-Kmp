package com.jasmeet.pixeldrop.game

/**
 * Generic block-drop piece geometry. Names are intentionally original;
 * shapes are plain grid geometry defined inside a square bounding box.
 */
enum class PieceType(val boxSize: Int, baseCells: List<Pair<Int, Int>>) {
    BAR(4, listOf(1 to 0, 1 to 1, 1 to 2, 1 to 3)),
    CUBE(2, listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)),
    TEE(3, listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2)),
    ESS(3, listOf(0 to 1, 0 to 2, 1 to 0, 1 to 1)),
    ZED(3, listOf(0 to 0, 0 to 1, 1 to 1, 1 to 2)),
    JAY(3, listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2)),
    ELL(3, listOf(0 to 2, 1 to 0, 1 to 1, 1 to 2));

    /** Four rotation states, each a list of (row, col) offsets inside the box. */
    val rotations: List<List<Pair<Int, Int>>> = buildList {
        var cells = baseCells
        repeat(4) {
            add(cells)
            cells = cells.map { (r, c) -> c to (boxSize - 1 - r) }
        }
    }
}

data class Piece(
    val type: PieceType,
    val rotation: Int = 0,
    val row: Int = 0,
    val col: Int = 0,
) {
    fun cells(): List<Pair<Int, Int>> =
        type.rotations[rotation].map { (r, c) -> (row + r) to (col + c) }

    fun moved(dr: Int, dc: Int): Piece = copy(row = row + dr, col = col + dc)

    fun rotated(direction: Int): Piece = copy(rotation = (rotation + direction + 4) % 4)
}
