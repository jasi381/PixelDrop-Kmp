package com.jasmeet.pixeldrop.ui.settings

import com.jasmeet.pixeldrop.game.GameId

/** Per-game preference key: `control_captions_seen_<GameId.name>`. */
fun controlCaptionsSeenKey(game: GameId): String = "control_captions_seen_" + game.name
