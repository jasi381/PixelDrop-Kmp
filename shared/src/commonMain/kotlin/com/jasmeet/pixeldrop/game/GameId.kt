package com.jasmeet.pixeldrop.game

/** Every slot in the CANVAS CLASSICS menu. The first six launch a real game;
 *  the rest are teasers that open the COMING SOON screen (see [isPlayable]). */
enum class GameId {
    PixelDrop, PixelTrail, BrickPulse, OrbitBlaster, MeteorDodge, PixelRacer,
    Soon1, Soon2, Soon3, Soon4,
}

data class GameInfo(val id: GameId, val title: String, val comingSoon: Boolean = false)

/** Menu list order, top to bottom. */
val GAMES: List<GameInfo> = listOf(
    GameInfo(GameId.PixelDrop, "PIXEL DROP"),
    GameInfo(GameId.PixelTrail, "PIXEL TRAIL"),
    GameInfo(GameId.BrickPulse, "BRICK PULSE"),
    GameInfo(GameId.OrbitBlaster, "ORBIT BLASTER"),
    GameInfo(GameId.MeteorDodge, "METEOR DODGE"),
    GameInfo(GameId.PixelRacer, "PIXEL RACER"),
    GameInfo(GameId.Soon1, "BOMB GRID", comingSoon = true),
    GameInfo(GameId.Soon2, "RIDGE RUNNER", comingSoon = true),
    GameInfo(GameId.Soon3, "TOWER STACK", comingSoon = true),
    GameInfo(GameId.Soon4, "RALLY DUEL", comingSoon = true),
)

val GameId.isPlayable: Boolean get() = GAMES.none { it.id == this && it.comingSoon }

fun GameId.next(): GameId {
    val ids = GAMES.map { it.id }
    return ids[(ids.indexOf(this) + 1) % ids.size]
}

fun GameId.previous(): GameId {
    val ids = GAMES.map { it.id }
    return ids[(ids.indexOf(this) - 1 + ids.size) % ids.size]
}
