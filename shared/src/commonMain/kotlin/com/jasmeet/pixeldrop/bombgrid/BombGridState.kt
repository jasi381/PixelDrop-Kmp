package com.jasmeet.pixeldrop.bombgrid

enum class BombGridPhase { Running, Paused, GameOver }

data class BombCell(val x: Int, val y: Int)

data class GridBomb(val id: Int, val cell: BombCell, val fuseMs: Long, val range: Int)

data class GridBlast(val cell: BombCell, val remainingMs: Long, val chain: Int)

data class GridHunter(val id: Int, val cell: BombCell, val moveCooldownMs: Long)

data class BombGridState(
    val score: Int,
    val lives: Int,
    val grid: Int,
    val bestChain: Int,
    val currentChain: Int,
    val player: BombCell,
    val hunters: List<GridHunter>,
    val crates: Set<BombCell>,
    val bombs: List<GridBomb>,
    val blasts: List<GridBlast>,
    val bombCapacity: Int,
    val blastRange: Int,
    val shieldHits: Int,
    val invulnerableMs: Long,
    val bombsPlaced: Int,
    val enemiesDestroyed: Int,
    val phase: BombGridPhase = BombGridPhase.Running,
)

const val BOMB_GRID_COLS = 15
const val BOMB_GRID_ROWS = 11
const val BOMB_FUSE_MS = 1_670L
const val BOMB_BLAST_MS = 300L
const val BOMB_START_LIVES = 3
