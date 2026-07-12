package com.jasmeet.pixeldrop.brickpulse

enum class BrickPattern {
    ClassicWall, CenterGap, Pyramid, SideTowers, CheckerGrid, StrongLine, Fortress, PulseMix
}

data class BrickPulseLevel(
    val number: Int,
    val pattern: BrickPattern,
    val ballSpeedMultiplier: Float,
    val paddleWidthMultiplier: Float,
    val powerUpDropChance: Float,
    val hasStrongBricks: Boolean,
)

/** The 8 hand-designed layouts below give every level a distinct shape and
 *  feel; past level 8 they loop with an extra speed bump per lap so the
 *  game never runs dry on content without needing unbounded new patterns. */
object BrickPulseLevels {
    const val GRID_COLS = 8

    private val BASE_LEVELS = listOf(
        BrickPulseLevel(1, BrickPattern.ClassicWall, 1.00f, 1.00f, 0.08f, false),
        BrickPulseLevel(2, BrickPattern.CenterGap, 1.05f, 1.00f, 0.10f, false),
        BrickPulseLevel(3, BrickPattern.Pyramid, 1.10f, 1.00f, 0.10f, false),
        BrickPulseLevel(4, BrickPattern.SideTowers, 1.15f, 1.00f, 0.10f, false),
        BrickPulseLevel(5, BrickPattern.CheckerGrid, 1.20f, 0.95f, 0.12f, false),
        BrickPulseLevel(6, BrickPattern.StrongLine, 1.25f, 0.95f, 0.12f, true),
        BrickPulseLevel(7, BrickPattern.Fortress, 1.30f, 0.90f, 0.14f, true),
        BrickPulseLevel(8, BrickPattern.PulseMix, 1.35f, 0.90f, 0.15f, true),
    )

    fun forLevel(number: Int): BrickPulseLevel {
        val base = BASE_LEVELS[(number - 1).mod(BASE_LEVELS.size)]
        val lap = (number - 1) / BASE_LEVELS.size
        return base.copy(number = number, ballSpeedMultiplier = base.ballSpeedMultiplier + lap * 0.15f)
    }

    /** (row, col, isStrong) triples for every brick in [level]'s pattern. */
    fun bricks(level: BrickPulseLevel): List<Triple<Int, Int, Boolean>> = when (level.pattern) {
        BrickPattern.ClassicWall -> classicWall()
        BrickPattern.CenterGap -> centerGap()
        BrickPattern.Pyramid -> pyramid()
        BrickPattern.SideTowers -> sideTowers()
        BrickPattern.CheckerGrid -> checkerGrid()
        BrickPattern.StrongLine -> strongLine(level.hasStrongBricks)
        BrickPattern.Fortress -> fortress(level.hasStrongBricks)
        BrickPattern.PulseMix -> pulseMix(level.hasStrongBricks)
    }

    private fun classicWall(): List<Triple<Int, Int, Boolean>> =
        (0 until 4).flatMap { row -> (0 until GRID_COLS).map { col -> Triple(row, col, false) } }

    private fun centerGap(): List<Triple<Int, Int, Boolean>> =
        (0 until 4).flatMap { row ->
            (0 until GRID_COLS).mapNotNull { col ->
                if (col == 3 || col == 4) null else Triple(row, col, false)
            }
        }

    private fun pyramid(): List<Triple<Int, Int, Boolean>> =
        (0 until 5).flatMap { row ->
            val width = ((row + 1) * 2).coerceAtMost(GRID_COLS)
            val start = (GRID_COLS - width) / 2
            (start until start + width).map { col -> Triple(row, col, false) }
        }

    private fun sideTowers(): List<Triple<Int, Int, Boolean>> =
        (0 until 6).flatMap { row ->
            listOf(0, 1, GRID_COLS - 2, GRID_COLS - 1).map { col -> Triple(row, col, false) }
        }

    private fun checkerGrid(): List<Triple<Int, Int, Boolean>> =
        (0 until 5).flatMap { row ->
            (0 until GRID_COLS).mapNotNull { col ->
                if ((row + col) % 2 == 0) Triple(row, col, false) else null
            }
        }

    private fun strongLine(hasStrongBricks: Boolean): List<Triple<Int, Int, Boolean>> =
        (0 until 4).flatMap { row ->
            (0 until GRID_COLS).map { col -> Triple(row, col, hasStrongBricks && row == 2) }
        }

    private fun fortress(hasStrongBricks: Boolean): List<Triple<Int, Int, Boolean>> =
        (0 until 6).flatMap { row ->
            (0 until GRID_COLS).mapNotNull { col ->
                val onBorder = row == 0 || row == 5 || col == 0 || col == GRID_COLS - 1
                if (onBorder) Triple(row, col, hasStrongBricks && row == 0) else null
            }
        }

    private fun pulseMix(hasStrongBricks: Boolean): List<Triple<Int, Int, Boolean>> =
        (0 until 5).flatMap { row ->
            (0 until GRID_COLS).mapNotNull { col ->
                if ((row + col) % 2 != 0) return@mapNotNull null
                Triple(row, col, hasStrongBricks && (row + col) % 4 == 0)
            }
        }
}
