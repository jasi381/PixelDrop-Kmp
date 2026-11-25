package com.jasmeet.pixeldrop.bombgrid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

/** Grid-based chain-reaction simulation for BOMB GRID. */
class BombGridEngine(
    private val randomInt: (Int) -> Int = { Random.Default.nextInt(it) },
) {
    private var nextBombId = 1
    private var nextHunterId = 1

    var state: BombGridState by mutableStateOf(initialState(1, score = 0, lives = BOMB_START_LIVES, bestChain = 0, bombsPlaced = 0, enemiesDestroyed = 0))
        private set

    fun move(dx: Int, dy: Int) {
        val s = state
        if (s.phase != BombGridPhase.Running) return
        val target = BombCell(s.player.x + dx, s.player.y + dy)
        if (isWalkable(target, s)) state = s.copy(player = target)
    }

    /** Returns true only for accepted placements so the countdown cue never false-triggers. */
    fun placeBomb(): Boolean {
        val s = state
        if (s.phase != BombGridPhase.Running || s.bombs.size >= s.bombCapacity || s.bombs.any { it.cell == s.player }) return false
        state = s.copy(
            bombs = s.bombs + GridBomb(nextBombId++, s.player, BOMB_FUSE_MS, s.blastRange),
            bombsPlaced = s.bombsPlaced + 1,
        )
        return true
    }

    fun tick(deltaMs: Long) {
        val s = state
        if (s.phase != BombGridPhase.Running) return
        val dt = deltaMs.coerceIn(0L, MAX_DELTA_MS)
        var next = s.copy(
            invulnerableMs = (s.invulnerableMs - dt).coerceAtLeast(0L),
            blasts = s.blasts.mapNotNull { blast ->
                val remaining = blast.remainingMs - dt
                if (remaining > 0L) blast.copy(remainingMs = remaining) else null
            },
            bombs = s.bombs.map { it.copy(fuseMs = it.fuseMs - dt) },
        )

        val queue = ArrayDeque(next.bombs.filter { it.fuseMs <= 0L })
        val exploded = mutableSetOf<Int>()
        while (queue.isNotEmpty()) {
            val bomb = queue.removeFirst()
            if (!exploded.add(bomb.id)) continue
            val result = explode(next, bomb, exploded.size)
            next = result.state
            for (other in result.triggered) if (other.id !in exploded) queue.addLast(other)
        }
        if (exploded.isNotEmpty()) next = next.copy(bombs = next.bombs.filterNot { it.id in exploded })

        next = moveHunters(next, dt)
        next = applyHazards(next)

        if (next.phase == BombGridPhase.Running && next.hunters.isEmpty()) {
            val nextGrid = next.grid + 1
            next = initialState(
                grid = nextGrid,
                score = next.score + GRID_CLEAR_SCORE * next.grid,
                lives = next.lives,
                bestChain = next.bestChain,
                bombsPlaced = next.bombsPlaced,
                enemiesDestroyed = next.enemiesDestroyed,
            )
        } else if (next.blasts.isEmpty()) {
            next = next.copy(currentChain = 0)
        }
        state = next
    }

    fun pause() { if (state.phase == BombGridPhase.Running) state = state.copy(phase = BombGridPhase.Paused) }
    fun resume() { if (state.phase == BombGridPhase.Paused) state = state.copy(phase = BombGridPhase.Running) }
    fun reset() { nextBombId = 1; nextHunterId = 1; state = initialState(1, 0, BOMB_START_LIVES, 0, 0, 0) }
    fun isGameOver(): Boolean = state.phase == BombGridPhase.GameOver

    internal fun forceForTest(
        player: BombCell? = null,
        lives: Int? = null,
        hunters: List<GridHunter>? = null,
        crates: Set<BombCell>? = null,
        bombs: List<GridBomb>? = null,
        blasts: List<GridBlast>? = null,
        invulnerableMs: Long? = null,
        bombCapacity: Int? = null,
    ) {
        state = state.copy(
            player = player ?: state.player,
            lives = lives ?: state.lives,
            hunters = hunters ?: state.hunters,
            crates = crates ?: state.crates,
            bombs = bombs ?: state.bombs,
            blasts = blasts ?: state.blasts,
            invulnerableMs = invulnerableMs ?: state.invulnerableMs,
            bombCapacity = bombCapacity ?: state.bombCapacity,
        )
    }

    private data class ExplosionResult(val state: BombGridState, val triggered: List<GridBomb>)

    private fun explode(source: BombGridState, bomb: GridBomb, chain: Int): ExplosionResult {
        var crates = source.crates
        val cells = ArrayList<BombCell>(1 + bomb.range * 4)
        cells += bomb.cell
        for ((dx,dy) in DIRECTIONS) {
            for (distance in 1..bomb.range) {
                val cell = BombCell(bomb.cell.x + dx * distance, bomb.cell.y + dy * distance)
                if (isHard(cell)) break
                cells += cell
                if (cell in crates) { crates = crates - cell; break }
            }
        }
        val triggered = source.bombs.filter { other -> other.id != bomb.id && cells.contains(other.cell) }
        val killed = source.hunters.filter { it.cell in cells }
        val currentChain = source.currentChain + 1
        val scoreGain = (source.crates.size - crates.size) * CRATE_SCORE + killed.size * HUNTER_SCORE * currentChain
        return ExplosionResult(
            source.copy(
                score = source.score + scoreGain,
                crates = crates,
                hunters = source.hunters - killed.toSet(),
                blasts = source.blasts + cells.map { GridBlast(it, BOMB_BLAST_MS, chain) },
                currentChain = currentChain,
                bestChain = maxOf(source.bestChain, currentChain),
                enemiesDestroyed = source.enemiesDestroyed + killed.size,
            ),
            triggered,
        )
    }

    private fun moveHunters(source: BombGridState, dt: Long): BombGridState {
        val moved = ArrayList<GridHunter>(source.hunters.size)
        for (hunter in source.hunters) {
            val cooldown = hunter.moveCooldownMs - dt
            if (cooldown > 0L) { moved += hunter.copy(moveCooldownMs = cooldown); continue }
            val towardX = if (source.player.x < hunter.cell.x) -1 else if (source.player.x > hunter.cell.x) 1 else 0
            val towardY = if (source.player.y < hunter.cell.y) -1 else if (source.player.y > hunter.cell.y) 1 else 0
            val candidates = if (abs(source.player.x - hunter.cell.x) >= abs(source.player.y - hunter.cell.y)) {
                listOf(towardX to 0, 0 to towardY, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
            } else listOf(0 to towardY, towardX to 0, 0 to -1, 0 to 1, -1 to 0, 1 to 0)
            var cell = hunter.cell
            val offset = randomInt(candidates.size)
            for (i in candidates.indices) {
                val d = candidates[(i + offset) % candidates.size]
                if (d.first == 0 && d.second == 0) continue
                val candidate = BombCell(cell.x + d.first, cell.y + d.second)
                if (isWalkable(candidate, source) && moved.none { it.cell == candidate }) { cell = candidate; break }
            }
            moved += hunter.copy(cell = cell, moveCooldownMs = hunterMoveInterval(source.grid))
        }
        return source.copy(hunters = moved)
    }

    private fun applyHazards(source: BombGridState): BombGridState {
        if (source.invulnerableMs > 0L) return source
        val hit = source.blasts.any { it.cell == source.player } || source.hunters.any { it.cell == source.player }
        if (!hit) return source
        if (source.shieldHits > 0) return source.copy(shieldHits = source.shieldHits - 1, invulnerableMs = HIT_GRACE_MS)
        val lives = source.lives - 1
        return source.copy(
            lives = lives,
            player = BombCell(1,1),
            invulnerableMs = HIT_GRACE_MS,
            currentChain = 0,
            phase = if (lives <= 0) BombGridPhase.GameOver else source.phase,
        )
    }

    private fun isWalkable(cell: BombCell, s: BombGridState): Boolean =
        cell.x in 1 until BOMB_GRID_COLS - 1 && cell.y in 1 until BOMB_GRID_ROWS - 1 &&
            !isHard(cell) && cell !in s.crates && s.bombs.none { it.cell == cell }

    private fun isHard(cell: BombCell): Boolean =
        cell.x <= 0 || cell.y <= 0 || cell.x >= BOMB_GRID_COLS - 1 || cell.y >= BOMB_GRID_ROWS - 1 ||
            (cell.x % 2 == 0 && cell.y % 2 == 0)

    private fun initialState(grid: Int, score: Int, lives: Int, bestChain: Int, bombsPlaced: Int, enemiesDestroyed: Int): BombGridState {
        val crates = buildSet {
            for (y in 1 until BOMB_GRID_ROWS - 1) for (x in 1 until BOMB_GRID_COLS - 1) {
                val cell = BombCell(x,y)
                val safe = (x <= 2 && y <= 2) || (x >= BOMB_GRID_COLS - 3 && y >= BOMB_GRID_ROWS - 3)
                if (!safe && !isHard(cell) && ((x * 7 + y * 11 + grid * 3) % 5 != 0)) add(cell)
            }
        }
        val spawnCells = listOf(BombCell(13,9), BombCell(13,1), BombCell(1,9), BombCell(7,5), BombCell(7,9), BombCell(13,5))
        val hunterCount = (2 + (grid - 1) / 2).coerceAtMost(6)
        val hunters = spawnCells.take(hunterCount).map { GridHunter(nextHunterId++, it, hunterMoveInterval(grid)) }
        return BombGridState(
            score, lives, grid, bestChain, currentChain = 0, player = BombCell(1,1), hunters = hunters,
            crates = crates - hunters.map { it.cell }.toSet(), bombs = emptyList(), blasts = emptyList(),
            bombCapacity = if (grid >= 4) 2 else 1, blastRange = if (grid >= 3) 3 else 2,
            shieldHits = if (grid % 4 == 0) 1 else 0, invulnerableMs = HIT_GRACE_MS, bombsPlaced = bombsPlaced,
            enemiesDestroyed = enemiesDestroyed, phase = BombGridPhase.Running,
        )
    }

    private fun hunterMoveInterval(grid: Int) = (560L - (grid - 1) * 45L).coerceAtLeast(260L)

    private companion object {
        val DIRECTIONS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        const val HIT_GRACE_MS = 1_600L
        const val MAX_DELTA_MS = 100L
        const val CRATE_SCORE = 25
        const val HUNTER_SCORE = 200
        const val GRID_CLEAR_SCORE = 1_000
    }
}
