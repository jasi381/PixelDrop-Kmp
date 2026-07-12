package com.jasmeet.pixeldrop.game

/** Every playable slot in the CANVAS CLASSICS menu; all six launch a
 *  real game. */
enum class GameId { PixelDrop, PixelTrail, BrickPulse, OrbitBlaster, MeteorDodge, PixelRacer, GalaxyMiner }

data class GameInfo(val id: GameId, val title: String)

/** Menu list order, top to bottom. */
val GAMES: List<GameInfo> = listOf(
    GameInfo(GameId.PixelDrop, "PIXEL DROP"),
    GameInfo(GameId.PixelTrail, "PIXEL TRAIL"),
    GameInfo(GameId.BrickPulse, "BRICK PULSE"),
    GameInfo(GameId.OrbitBlaster, "ORBIT BLASTER"),
    GameInfo(GameId.MeteorDodge, "METEOR DODGE"),
    GameInfo(GameId.PixelRacer, "PIXEL RACER"),
    GameInfo(GameId.GalaxyMiner, "GALAXY MINER"),
)

fun GameId.next(): GameId {
    val ids = GAMES.map { it.id }
    return ids[(ids.indexOf(this) + 1) % ids.size]
}

fun GameId.previous(): GameId {
    val ids = GAMES.map { it.id }
    return ids[(ids.indexOf(this) - 1 + ids.size) % ids.size]
}
