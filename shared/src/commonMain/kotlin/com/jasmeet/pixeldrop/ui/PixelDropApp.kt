package com.jasmeet.pixeldrop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jasmeet.pixeldrop.brickpulse.BrickPulseEngine
import com.jasmeet.pixeldrop.brickpulse.BrickPulsePhase
import com.jasmeet.pixeldrop.game.GameEngine
import com.jasmeet.pixeldrop.game.GameId
import com.jasmeet.pixeldrop.game.GamePhase
import com.jasmeet.pixeldrop.game.next
import com.jasmeet.pixeldrop.game.previous
import com.jasmeet.pixeldrop.meteordodge.MeteorDodgeEngine
import com.jasmeet.pixeldrop.meteordodge.MeteorDodgePhase
import com.jasmeet.pixeldrop.motion.BootTimeline
import com.jasmeet.pixeldrop.motion.PowerOffTimeline
import com.jasmeet.pixeldrop.orbitblaster.OrbitBlasterEngine
import com.jasmeet.pixeldrop.orbitblaster.OrbitBlasterPhase
import com.jasmeet.pixeldrop.pixelracer.PixelRacerEngine
import com.jasmeet.pixeldrop.pixelracer.PixelRacerPhase
import com.jasmeet.pixeldrop.pixeltrail.PixelTrailEngine
import com.jasmeet.pixeldrop.pixeltrail.PixelTrailPhase
import com.jasmeet.pixeldrop.pixeltrail.TrailDirection
import com.jasmeet.pixeldrop.ui.audio.rememberRetroAudio
import com.jasmeet.pixeldrop.ui.components.ConsoleInput
import com.jasmeet.pixeldrop.ui.components.ConsoleCustomizer
import com.jasmeet.pixeldrop.ui.components.ConsoleCustomizerButton
import com.jasmeet.pixeldrop.ui.components.PrivacyPolicyButton
import com.jasmeet.pixeldrop.ui.components.PrivacyPolicyScreen
import com.jasmeet.pixeldrop.ui.components.RetroConsoleShell
import com.jasmeet.pixeldrop.ui.screens.BootScreen
import com.jasmeet.pixeldrop.ui.screens.BrickPulseGameOverScreen
import com.jasmeet.pixeldrop.ui.screens.BrickPulseScreen
import com.jasmeet.pixeldrop.ui.screens.ComingSoonScreen
import com.jasmeet.pixeldrop.ui.screens.GameMenuScreen
import com.jasmeet.pixeldrop.ui.screens.GameOverScreen
import com.jasmeet.pixeldrop.ui.screens.GameplayScreen
import com.jasmeet.pixeldrop.ui.screens.MeteorDodgeGameOverScreen
import com.jasmeet.pixeldrop.ui.screens.MeteorDodgeScreen
import com.jasmeet.pixeldrop.ui.screens.OrbitBlasterGameOverScreen
import com.jasmeet.pixeldrop.ui.screens.OrbitBlasterScreen
import com.jasmeet.pixeldrop.ui.screens.PAUSE_MENU_ITEMS
import com.jasmeet.pixeldrop.ui.screens.PauseScreen
import com.jasmeet.pixeldrop.ui.screens.PixelRacerGameOverScreen
import com.jasmeet.pixeldrop.ui.screens.PixelRacerScreen
import com.jasmeet.pixeldrop.ui.screens.PixelTrailGameOverScreen
import com.jasmeet.pixeldrop.ui.screens.PixelTrailScreen
import com.jasmeet.pixeldrop.ui.platform.rememberConsolePrefs
import com.jasmeet.pixeldrop.ui.screens.PowerOffScreen
import com.jasmeet.pixeldrop.ui.theme.PixelDropColors
import com.jasmeet.pixeldrop.ui.theme.consoleThemeFor
import kotlinx.coroutines.delay

private enum class AppScreen { Boot, Menu, Game, ComingSoon, PoweringOff, PoweredOff }

private const val CONSOLE_THEME_KEY = "console_theme"

/** True while whichever game is currently selected is actually mid-round —
 *  used to decide whether foregrounding the app should resume the shared
 *  gameplay music track. */
private fun isActiveGameRunning(
    screen: AppScreen,
    selectedGameId: GameId,
    engine: GameEngine,
    trailEngine: PixelTrailEngine,
    brickEngine: BrickPulseEngine,
    orbitEngine: OrbitBlasterEngine,
    meteorEngine: MeteorDodgeEngine,
    racerEngine: PixelRacerEngine,
): Boolean {
    if (screen != AppScreen.Game) return false
    return when (selectedGameId) {
        GameId.PixelDrop -> engine.state.phase == GamePhase.Running
        GameId.PixelTrail -> trailEngine.state.phase == PixelTrailPhase.Running
        GameId.BrickPulse -> brickEngine.state.phase == BrickPulsePhase.Running
        GameId.OrbitBlaster -> orbitEngine.state.phase == OrbitBlasterPhase.Running
        GameId.MeteorDodge -> meteorEngine.state.phase == MeteorDodgePhase.Running
        GameId.PixelRacer -> racerEngine.state.phase == PixelRacerPhase.Running
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PixelDropApp(windowFocused: Boolean = true) {
    val soundPlayer = rememberRetroAudio()
    val customizationPrefs = rememberConsolePrefs()
    var selectedThemeId by rememberSaveable {
        mutableStateOf(consoleThemeFor(customizationPrefs.getString(CONSOLE_THEME_KEY)).id)
    }
    var customizationOpen by rememberSaveable { mutableStateOf(false) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { soundPlayer.release() }
    }

    val engine = remember { GameEngine() }
    val trailEngine = remember { PixelTrailEngine() }
    val brickEngine = remember { BrickPulseEngine() }
    val orbitEngine = remember { OrbitBlasterEngine() }
    val meteorEngine = remember { MeteorDodgeEngine() }
    val racerEngine = remember { PixelRacerEngine() }
    var screen by remember { mutableStateOf(AppScreen.Boot) }

    // Cursor in the CANVAS CLASSICS menu; also which game ComingSoon names.
    var selectedGameId by remember { mutableStateOf(GameId.PixelDrop) }

    // App lifecycle: pause music in background, resume only mid-round.
    val lifecycleOwner = LocalLifecycleOwner.current
    var bootElapsed by remember { mutableIntStateOf(0) }
    var shutdownElapsed by remember { mutableIntStateOf(0) }
    var pauseIndex by remember { mutableIntStateOf(0) }

    // What the LCD was showing when the power-off combo completed.
    var frozenScreen by remember { mutableStateOf(AppScreen.Menu) }
    var frozenPhase by remember { mutableStateOf(GamePhase.Running) }
    var frozenTrailPhase by remember { mutableStateOf(PixelTrailPhase.Running) }
    var frozenBrickPhase by remember { mutableStateOf(BrickPulsePhase.Running) }
    var frozenOrbitPhase by remember { mutableStateOf(OrbitBlasterPhase.Running) }
    var frozenMeteorPhase by remember { mutableStateOf(MeteorDodgePhase.Running) }
    var frozenRacerPhase by remember { mutableStateOf(PixelRacerPhase.Running) }

    // Raw pressed state of the POWER pill, for the hold-to-power-off timer.
    var powerHeld by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    soundPlayer.pauseGameplayMusic()
                    soundPlayer.pauseSfx()
                }
                Lifecycle.Event.ON_RESUME -> {
                    soundPlayer.resumeSfx()
                    if (isActiveGameRunning(screen, selectedGameId, engine, trailEngine, brickEngine, orbitEngine, meteorEngine, racerEngine)) {
                        soundPlayer.startGameplayMusic()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Window focus (not just lifecycle): Recents/Overview peek keeps the
    // Activity RESUMED for its live thumbnail and never fires onPause, so
    // audio would otherwise keep playing behind the app switcher.
    LaunchedEffect(windowFocused) {
        if (!windowFocused) {
            soundPlayer.pauseGameplayMusic()
            soundPlayer.pauseSfx()
        } else {
            soundPlayer.resumeSfx()
            if (isActiveGameRunning(screen, selectedGameId, engine, trailEngine, brickEngine, orbitEngine, meteorEngine, racerEngine)) {
                soundPlayer.startGameplayMusic()
            }
        }
    }

    // Boot clock: frame-driven, plays the boot chime once, advances to Menu.
    LaunchedEffect(screen) {
        if (screen == AppScreen.Boot) {
            bootElapsed = 0
            soundPlayer.playBoot()
            val start = withFrameMillis { it }
            while (bootElapsed < BootTimeline.TOTAL_MS) {
                withFrameMillis { now ->
                    bootElapsed = (now - start).toInt().coerceAtMost(BootTimeline.TOTAL_MS)
                }
            }
            screen = AppScreen.Menu
        }
    }

    // Shutdown clock: drives the power-off animation, then rests powered off.
    LaunchedEffect(screen) {
        if (screen == AppScreen.PoweringOff) {
            shutdownElapsed = 0
            val start = withFrameMillis { it }
            while (shutdownElapsed < PowerOffTimeline.TOTAL_MS) {
                withFrameMillis { now ->
                    shutdownElapsed = (now - start).toInt().coerceAtMost(PowerOffTimeline.TOTAL_MS)
                }
            }
            screen = AppScreen.PoweredOff
        }
    }

    // Gravity tick; cancelled automatically on pause/game over/power-off.
    val phase = engine.state.phase
    LaunchedEffect(screen, phase) {
        if (screen == AppScreen.Game && phase == GamePhase.Running) {
            while (true) {
                delay(engine.state.tickMillis)
                engine.tick()
            }
        }
    }

    // PIXEL TRAIL's own movement tick; only runs while it's the active game.
    val trailPhase = trailEngine.state.phase
    LaunchedEffect(screen, selectedGameId, trailPhase) {
        if (screen == AppScreen.Game && selectedGameId == GameId.PixelTrail && trailPhase == PixelTrailPhase.Running) {
            while (true) {
                delay(trailEngine.state.tickMillis)
                trailEngine.tick()
            }
        }
    }

    // BRICK PULSE's physics tick — frame-driven with a real delta (like the
    // boot/shutdown clocks above), not a fixed delay, so the ball moves
    // smoothly instead of in discrete grid steps.
    val brickPhase = brickEngine.state.phase
    LaunchedEffect(screen, selectedGameId, brickPhase) {
        if (screen == AppScreen.Game && selectedGameId == GameId.BrickPulse && brickPhase == BrickPulsePhase.Running) {
            var lastFrame = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val delta = now - lastFrame
                    lastFrame = now
                    brickEngine.tick(delta)
                }
            }
        }
    }

    // ORBIT BLASTER's physics tick — frame-driven like BRICK PULSE's so
    // ships and bullets glide instead of stepping.
    val orbitPhase = orbitEngine.state.phase
    LaunchedEffect(screen, selectedGameId, orbitPhase) {
        if (screen == AppScreen.Game && selectedGameId == GameId.OrbitBlaster && orbitPhase == OrbitBlasterPhase.Running) {
            var lastFrame = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val delta = now - lastFrame
                    lastFrame = now
                    orbitEngine.tick(delta)
                }
            }
        }
    }

    // METEOR DODGE's physics tick — frame-driven like the two above.
    val meteorPhase = meteorEngine.state.phase
    LaunchedEffect(screen, selectedGameId, meteorPhase) {
        if (screen == AppScreen.Game && selectedGameId == GameId.MeteorDodge && meteorPhase == MeteorDodgePhase.Running) {
            var lastFrame = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val delta = now - lastFrame
                    lastFrame = now
                    meteorEngine.tick(delta)
                }
            }
        }
    }

    // PIXEL RACER's physics tick — frame-driven like the three above.
    val racerPhase = racerEngine.state.phase
    LaunchedEffect(screen, selectedGameId, racerPhase) {
        if (screen == AppScreen.Game && selectedGameId == GameId.PixelRacer && racerPhase == PixelRacerPhase.Running) {
            var lastFrame = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val delta = now - lastFrame
                    lastFrame = now
                    racerEngine.tick(delta)
                }
            }
        }
    }

    // Line-clear chime: fires whenever the cleared-lines counter grows.
    val clearedLines = engine.state.lines
    var previousLines by remember { mutableIntStateOf(0) }
    LaunchedEffect(clearedLines) {
        if (clearedLines > previousLines && screen == AppScreen.Game) {
            soundPlayer.playLineClear()
        }
        previousLines = clearedLines
    }

    // Reuses the same chime for PIXEL TRAIL's food pickups — no dedicated
    // SFX asset exists for it yet, and this is the closest existing "scored"
    // cue to reuse cleanly.
    val trailFoodCollected = trailEngine.state.foodCollected
    var previousTrailFood by remember { mutableIntStateOf(0) }
    LaunchedEffect(trailFoodCollected) {
        if (trailFoodCollected > previousTrailFood && screen == AppScreen.Game) {
            soundPlayer.playLineClear()
        }
        previousTrailFood = trailFoodCollected
    }

    // Same reuse for BRICK PULSE's brick breaks — no dedicated
    // pixel_brick_break asset exists yet (TODO once one lands).
    val bricksBroken = brickEngine.state.bricksBroken
    var previousBricksBroken by remember { mutableIntStateOf(0) }
    LaunchedEffect(bricksBroken) {
        if (bricksBroken > previousBricksBroken && screen == AppScreen.Game) {
            soundPlayer.playLineClear()
        }
        previousBricksBroken = bricksBroken
    }

    // Same reuse for ORBIT BLASTER kills — no dedicated explosion SFX asset
    // exists yet (TODO once one lands).
    val orbitKills = orbitEngine.state.enemiesKilled
    var previousOrbitKills by remember { mutableIntStateOf(0) }
    LaunchedEffect(orbitKills) {
        if (orbitKills > previousOrbitKills && screen == AppScreen.Game) {
            soundPlayer.playLineClear()
        }
        previousOrbitKills = orbitKills
    }

    // Same reuse for METEOR DODGE bonus pickups.
    val meteorBonuses = meteorEngine.state.bonusesCollected
    var previousMeteorBonuses by remember { mutableIntStateOf(0) }
    LaunchedEffect(meteorBonuses) {
        if (meteorBonuses > previousMeteorBonuses && screen == AppScreen.Game) {
            soundPlayer.playLineClear()
        }
        previousMeteorBonuses = meteorBonuses
    }

    // Same reuse for PIXEL RACER pickups (coins, fuel and powerup boxes).
    val racerPickups = racerEngine.state.coinsCollected +
        racerEngine.state.fuelCollected + racerEngine.state.powerupsCollected
    var previousRacerPickups by remember { mutableIntStateOf(0) }
    LaunchedEffect(racerPickups) {
        if (racerPickups > previousRacerPickups && screen == AppScreen.Game) {
            soundPlayer.playLineClear()
        }
        previousRacerPickups = racerPickups
    }

    // PIXEL RACER near-miss blip — reuses the menu blip as the closest short
    // cue (TODO once a dedicated near-miss asset lands).
    val racerNearMisses = racerEngine.state.nearMisses
    var previousRacerNearMisses by remember { mutableIntStateOf(0) }
    LaunchedEffect(racerNearMisses) {
        if (racerNearMisses > previousRacerNearMisses && screen == AppScreen.Game) {
            soundPlayer.playMenuSelect()
        }
        previousRacerNearMisses = racerNearMisses
    }

    // PIXEL RACER crash thud — reuses the rotate blip (TODO once a dedicated
    // crash asset lands). Full wrecks are covered by the shared game-over
    // sting below, so this only marks lives lost mid-run.
    val racerCrashes = racerEngine.state.crashes
    var previousRacerCrashes by remember { mutableIntStateOf(0) }
    LaunchedEffect(racerCrashes) {
        if (racerCrashes > previousRacerCrashes && screen == AppScreen.Game &&
            racerEngine.state.phase == PixelRacerPhase.Running
        ) {
            soundPlayer.playRotate()
        }
        previousRacerCrashes = racerCrashes
    }

    // PIXEL RACER low-fuel beep — once per threshold crossing, counter-keyed.
    val racerFuelWarnings = racerEngine.state.lowFuelWarnings
    var previousRacerFuelWarnings by remember { mutableIntStateOf(0) }
    LaunchedEffect(racerFuelWarnings) {
        if (racerFuelWarnings > previousRacerFuelWarnings && screen == AppScreen.Game) {
            soundPlayer.playMove()
        }
        previousRacerFuelWarnings = racerFuelWarnings
    }

    // Shared game-over chime: one shot on the transition into any game's
    // GameOver phase. Keyed on the boolean, so recompositions and sitting on
    // the game-over screen never replay it; retry flips it false and re-arms.
    val anyGameOver = screen == AppScreen.Game && when (selectedGameId) {
        GameId.PixelDrop -> phase == GamePhase.GameOver
        GameId.PixelTrail -> trailPhase == PixelTrailPhase.GameOver
        GameId.BrickPulse -> brickPhase == BrickPulsePhase.GameOver
        GameId.OrbitBlaster -> orbitPhase == OrbitBlasterPhase.GameOver
        GameId.MeteorDodge -> meteorPhase == MeteorDodgePhase.GameOver
        GameId.PixelRacer -> racerPhase == PixelRacerPhase.GameOver
    }
    LaunchedEffect(anyGameOver) {
        if (anyGameOver) soundPlayer.playGameOver()
    }

    // Gameplay music follows whichever game is active: playing while a round
    // runs, paused with the pause menu, stopped (and rewound) everywhere else.
    LaunchedEffect(screen, selectedGameId, phase, trailPhase, brickPhase, orbitPhase, meteorPhase, racerPhase) {
        when {
            screen != AppScreen.Game -> soundPlayer.stopGameplayMusic()
            selectedGameId == GameId.PixelDrop && phase == GamePhase.Running ->
                soundPlayer.startGameplayMusic()
            selectedGameId == GameId.PixelDrop && phase == GamePhase.Paused ->
                soundPlayer.pauseGameplayMusic()
            selectedGameId == GameId.PixelTrail && trailPhase == PixelTrailPhase.Running ->
                soundPlayer.startGameplayMusic()
            selectedGameId == GameId.PixelTrail && trailPhase == PixelTrailPhase.Paused ->
                soundPlayer.pauseGameplayMusic()
            selectedGameId == GameId.BrickPulse && brickPhase == BrickPulsePhase.Running ->
                soundPlayer.startGameplayMusic()
            selectedGameId == GameId.BrickPulse && brickPhase == BrickPulsePhase.Paused ->
                soundPlayer.pauseGameplayMusic()
            selectedGameId == GameId.OrbitBlaster && orbitPhase == OrbitBlasterPhase.Running ->
                soundPlayer.startGameplayMusic()
            selectedGameId == GameId.OrbitBlaster && orbitPhase == OrbitBlasterPhase.Paused ->
                soundPlayer.pauseGameplayMusic()
            selectedGameId == GameId.MeteorDodge && meteorPhase == MeteorDodgePhase.Running ->
                soundPlayer.startGameplayMusic()
            selectedGameId == GameId.MeteorDodge && meteorPhase == MeteorDodgePhase.Paused ->
                soundPlayer.pauseGameplayMusic()
            selectedGameId == GameId.PixelRacer && racerPhase == PixelRacerPhase.Running ->
                soundPlayer.startGameplayMusic()
            selectedGameId == GameId.PixelRacer && racerPhase == PixelRacerPhase.Paused ->
                soundPlayer.pauseGameplayMusic()
            else -> soundPlayer.stopGameplayMusic()
        }
    }

    // Hold POWER ~1s from Menu, Game or ComingSoon to switch the console off.
    // Releasing the pill cancels the LaunchedEffect before it fires;
    // a quick tap never powers off.
    val powerArmed = powerHeld &&
        (screen == AppScreen.Menu || screen == AppScreen.Game || screen == AppScreen.ComingSoon)
    LaunchedEffect(powerArmed) {
        if (powerArmed) {
            delay(PowerOffTimeline.COMBO_HOLD_MS)
            frozenScreen = screen
            frozenPhase = engine.state.phase
            frozenTrailPhase = trailEngine.state.phase
            frozenBrickPhase = brickEngine.state.phase
            frozenOrbitPhase = orbitEngine.state.phase
            frozenMeteorPhase = meteorEngine.state.phase
            frozenRacerPhase = racerEngine.state.phase
            soundPlayer.playShutdown()
            screen = AppScreen.PoweringOff
        }
    }

    fun startGame() {
        engine.reset()
        pauseIndex = 0
        // A fresh round always restarts the music from the top, even when
        // coming from the pause menu's RESTART (music was merely paused).
        soundPlayer.stopGameplayMusic()
        screen = AppScreen.Game
    }

    fun startPixelTrail() {
        trailEngine.reset()
        pauseIndex = 0
        soundPlayer.stopGameplayMusic()
        screen = AppScreen.Game
    }

    fun startBrickPulse() {
        brickEngine.reset()
        pauseIndex = 0
        soundPlayer.stopGameplayMusic()
        screen = AppScreen.Game
    }

    fun startOrbitBlaster() {
        orbitEngine.reset()
        pauseIndex = 0
        soundPlayer.stopGameplayMusic()
        screen = AppScreen.Game
    }

    fun startMeteorDodge() {
        meteorEngine.reset()
        pauseIndex = 0
        soundPlayer.stopGameplayMusic()
        screen = AppScreen.Game
    }

    fun startPixelRacer() {
        racerEngine.reset()
        pauseIndex = 0
        soundPlayer.stopGameplayMusic()
        screen = AppScreen.Game
    }

    // All six CANVAS CLASSICS are implemented and launch directly.
    fun launchSelected() {
        when (selectedGameId) {
            GameId.PixelDrop -> startGame()
            GameId.PixelTrail -> startPixelTrail()
            GameId.BrickPulse -> startBrickPulse()
            GameId.OrbitBlaster -> startOrbitBlaster()
            GameId.MeteorDodge -> startMeteorDodge()
            GameId.PixelRacer -> startPixelRacer()
        }
    }

    fun powerOn() {
        bootElapsed = 0
        screen = AppScreen.Boot
    }

    val input = remember {
        ConsoleInput(
            onLeft = {
                if (screen == AppScreen.Game) {
                    when (selectedGameId) {
                        GameId.PixelDrop -> if (engine.state.phase == GamePhase.Running) {
                            engine.moveLeft()
                            soundPlayer.playMove()
                        }
                        GameId.PixelTrail -> if (trailEngine.state.phase == PixelTrailPhase.Running) {
                            trailEngine.changeDirection(TrailDirection.Left)
                        }
                        GameId.BrickPulse -> if (brickEngine.state.phase == BrickPulsePhase.Running) {
                            brickEngine.movePaddleLeft()
                        }
                        GameId.OrbitBlaster -> if (orbitEngine.state.phase == OrbitBlasterPhase.Running) {
                            orbitEngine.moveLeft()
                        }
                        GameId.MeteorDodge -> if (meteorEngine.state.phase == MeteorDodgePhase.Running) {
                            meteorEngine.moveLeft()
                        }
                        GameId.PixelRacer -> if (racerEngine.state.phase == PixelRacerPhase.Running) {
                            racerEngine.moveLeft()
                            soundPlayer.playMove()
                        }
                    }
                }
            },
            onRight = {
                if (screen == AppScreen.Game) {
                    when (selectedGameId) {
                        GameId.PixelDrop -> if (engine.state.phase == GamePhase.Running) {
                            engine.moveRight()
                            soundPlayer.playMove()
                        }
                        GameId.PixelTrail -> if (trailEngine.state.phase == PixelTrailPhase.Running) {
                            trailEngine.changeDirection(TrailDirection.Right)
                        }
                        GameId.BrickPulse -> if (brickEngine.state.phase == BrickPulsePhase.Running) {
                            brickEngine.movePaddleRight()
                        }
                        GameId.OrbitBlaster -> if (orbitEngine.state.phase == OrbitBlasterPhase.Running) {
                            orbitEngine.moveRight()
                        }
                        GameId.MeteorDodge -> if (meteorEngine.state.phase == MeteorDodgePhase.Running) {
                            meteorEngine.moveRight()
                        }
                        GameId.PixelRacer -> if (racerEngine.state.phase == PixelRacerPhase.Running) {
                            racerEngine.moveRight()
                            soundPlayer.playMove()
                        }
                    }
                }
            },
            onDown = {
                when {
                    screen == AppScreen.Menu -> {
                        selectedGameId = selectedGameId.next()
                        soundPlayer.playMenuSelect()
                    }
                    screen == AppScreen.Game && selectedGameId == GameId.PixelDrop -> when (engine.state.phase) {
                        GamePhase.Running -> engine.softDrop()
                        GamePhase.Paused -> pauseIndex = (pauseIndex + 1) % PAUSE_MENU_ITEMS.size
                        else -> Unit
                    }
                    screen == AppScreen.Game && selectedGameId == GameId.PixelTrail -> when (trailEngine.state.phase) {
                        PixelTrailPhase.Running -> trailEngine.changeDirection(TrailDirection.Down)
                        PixelTrailPhase.Paused -> pauseIndex = (pauseIndex + 1) % PAUSE_MENU_ITEMS.size
                        else -> Unit
                    }
                    screen == AppScreen.Game && selectedGameId == GameId.BrickPulse &&
                        brickEngine.state.phase == BrickPulsePhase.Paused ->
                        pauseIndex = (pauseIndex + 1) % PAUSE_MENU_ITEMS.size
                    screen == AppScreen.Game && selectedGameId == GameId.OrbitBlaster &&
                        orbitEngine.state.phase == OrbitBlasterPhase.Paused ->
                        pauseIndex = (pauseIndex + 1) % PAUSE_MENU_ITEMS.size
                    screen == AppScreen.Game && selectedGameId == GameId.MeteorDodge -> when (meteorEngine.state.phase) {
                        MeteorDodgePhase.Running -> meteorEngine.moveDown()
                        MeteorDodgePhase.Paused -> pauseIndex = (pauseIndex + 1) % PAUSE_MENU_ITEMS.size
                        else -> Unit
                    }
                    screen == AppScreen.Game && selectedGameId == GameId.PixelRacer &&
                        racerEngine.state.phase == PixelRacerPhase.Paused ->
                        pauseIndex = (pauseIndex + 1) % PAUSE_MENU_ITEMS.size
                }
            },
            onUp = {
                when {
                    screen == AppScreen.Menu -> {
                        selectedGameId = selectedGameId.previous()
                        soundPlayer.playMenuSelect()
                    }
                    screen == AppScreen.Game && selectedGameId == GameId.PixelDrop -> {
                        if (engine.state.phase == GamePhase.Paused) {
                            pauseIndex = (pauseIndex + PAUSE_MENU_ITEMS.size - 1) % PAUSE_MENU_ITEMS.size
                        }
                    }
                    screen == AppScreen.Game && selectedGameId == GameId.PixelTrail -> when (trailEngine.state.phase) {
                        PixelTrailPhase.Running -> trailEngine.changeDirection(TrailDirection.Up)
                        PixelTrailPhase.Paused ->
                            pauseIndex = (pauseIndex + PAUSE_MENU_ITEMS.size - 1) % PAUSE_MENU_ITEMS.size
                        else -> Unit
                    }
                    screen == AppScreen.Game && selectedGameId == GameId.BrickPulse &&
                        brickEngine.state.phase == BrickPulsePhase.Paused ->
                        pauseIndex = (pauseIndex + PAUSE_MENU_ITEMS.size - 1) % PAUSE_MENU_ITEMS.size
                    screen == AppScreen.Game && selectedGameId == GameId.OrbitBlaster &&
                        orbitEngine.state.phase == OrbitBlasterPhase.Paused ->
                        pauseIndex = (pauseIndex + PAUSE_MENU_ITEMS.size - 1) % PAUSE_MENU_ITEMS.size
                    screen == AppScreen.Game && selectedGameId == GameId.MeteorDodge -> when (meteorEngine.state.phase) {
                        MeteorDodgePhase.Running -> meteorEngine.moveUp()
                        MeteorDodgePhase.Paused ->
                            pauseIndex = (pauseIndex + PAUSE_MENU_ITEMS.size - 1) % PAUSE_MENU_ITEMS.size
                        else -> Unit
                    }
                    screen == AppScreen.Game && selectedGameId == GameId.PixelRacer &&
                        racerEngine.state.phase == PixelRacerPhase.Paused ->
                        pauseIndex = (pauseIndex + PAUSE_MENU_ITEMS.size - 1) % PAUSE_MENU_ITEMS.size
                }
            },
            onA = {
                when (screen) {
                    AppScreen.Boot, AppScreen.PoweringOff, AppScreen.ComingSoon -> Unit
                    AppScreen.PoweredOff -> powerOn()
                    AppScreen.Menu -> launchSelected()
                    AppScreen.Game -> when (selectedGameId) {
                        GameId.PixelDrop -> when (engine.state.phase) {
                            GamePhase.Running -> {
                                engine.rotateCw()
                                soundPlayer.playRotate()
                            }
                            GamePhase.Paused -> when (pauseIndex) {
                                0 -> engine.resume()
                                1 -> startGame()
                                else -> screen = AppScreen.Menu
                            }
                            GamePhase.GameOver -> startGame()
                        }
                        GameId.PixelTrail -> when (trailEngine.state.phase) {
                            PixelTrailPhase.Running -> Unit
                            PixelTrailPhase.Paused -> when (pauseIndex) {
                                0 -> trailEngine.resume()
                                1 -> startPixelTrail()
                                else -> screen = AppScreen.Menu
                            }
                            PixelTrailPhase.GameOver -> startPixelTrail()
                        }
                        GameId.BrickPulse -> when (brickEngine.state.phase) {
                            BrickPulsePhase.Running -> brickEngine.launchBall()
                            BrickPulsePhase.Paused -> when (pauseIndex) {
                                0 -> brickEngine.resume()
                                1 -> startBrickPulse()
                                else -> screen = AppScreen.Menu
                            }
                            BrickPulsePhase.GameOver -> startBrickPulse()
                        }
                        GameId.OrbitBlaster -> when (orbitEngine.state.phase) {
                            OrbitBlasterPhase.Running -> orbitEngine.shoot()
                            OrbitBlasterPhase.Paused -> when (pauseIndex) {
                                0 -> orbitEngine.resume()
                                1 -> startOrbitBlaster()
                                else -> screen = AppScreen.Menu
                            }
                            OrbitBlasterPhase.GameOver -> startOrbitBlaster()
                        }
                        GameId.MeteorDodge -> when (meteorEngine.state.phase) {
                            MeteorDodgePhase.Running -> meteorEngine.dash()
                            MeteorDodgePhase.Paused -> when (pauseIndex) {
                                0 -> meteorEngine.resume()
                                1 -> startMeteorDodge()
                                else -> screen = AppScreen.Menu
                            }
                            MeteorDodgePhase.GameOver -> startMeteorDodge()
                        }
                        GameId.PixelRacer -> when (racerEngine.state.phase) {
                            // Steering is the whole game; A stays free.
                            PixelRacerPhase.Running -> Unit
                            PixelRacerPhase.Paused -> when (pauseIndex) {
                                0 -> racerEngine.resume()
                                1 -> startPixelRacer()
                                else -> screen = AppScreen.Menu
                            }
                            PixelRacerPhase.GameOver -> startPixelRacer()
                        }
                    }
                }
            },
            onB = {
                when (screen) {
                    AppScreen.ComingSoon -> screen = AppScreen.Menu
                    AppScreen.Game -> when (selectedGameId) {
                        GameId.PixelDrop -> when (engine.state.phase) {
                            GamePhase.Running -> {
                                engine.rotateCcw()
                                soundPlayer.playRotate()
                            }
                            // B on the game-over screen returns to the menu.
                            GamePhase.GameOver -> screen = AppScreen.Menu
                            else -> Unit
                        }
                        GameId.PixelTrail -> if (trailEngine.state.phase == PixelTrailPhase.GameOver) {
                            screen = AppScreen.Menu
                        }
                        GameId.BrickPulse -> if (brickEngine.state.phase == BrickPulsePhase.GameOver) {
                            screen = AppScreen.Menu
                        }
                        GameId.OrbitBlaster -> when (orbitEngine.state.phase) {
                            // B is the dash trigger; a safe no-op until the
                            // Dash powerup arms it.
                            OrbitBlasterPhase.Running -> orbitEngine.dash()
                            OrbitBlasterPhase.GameOver -> screen = AppScreen.Menu
                            else -> Unit
                        }
                        GameId.MeteorDodge -> if (meteorEngine.state.phase == MeteorDodgePhase.GameOver) {
                            screen = AppScreen.Menu
                        }
                        GameId.PixelRacer -> if (racerEngine.state.phase == PixelRacerPhase.GameOver) {
                            screen = AppScreen.Menu
                        }
                    }
                    else -> Unit
                }
            },
            onStart = {
                when (screen) {
                    AppScreen.Boot -> {
                        bootElapsed = BootTimeline.TOTAL_MS
                        soundPlayer.stopAll()
                        screen = AppScreen.Menu
                    }
                    AppScreen.Menu -> launchSelected()
                    AppScreen.Game -> when (selectedGameId) {
                        GameId.PixelDrop -> when (engine.state.phase) {
                            GamePhase.Running -> {
                                pauseIndex = 0
                                engine.pause()
                            }
                            GamePhase.Paused -> engine.resume()
                            GamePhase.GameOver -> startGame()
                        }
                        GameId.PixelTrail -> when (trailEngine.state.phase) {
                            PixelTrailPhase.Running -> {
                                pauseIndex = 0
                                trailEngine.pause()
                            }
                            PixelTrailPhase.Paused -> trailEngine.resume()
                            PixelTrailPhase.GameOver -> startPixelTrail()
                        }
                        GameId.BrickPulse -> when (brickEngine.state.phase) {
                            BrickPulsePhase.Running -> {
                                pauseIndex = 0
                                brickEngine.pause()
                            }
                            BrickPulsePhase.Paused -> brickEngine.resume()
                            BrickPulsePhase.GameOver -> startBrickPulse()
                        }
                        GameId.OrbitBlaster -> when (orbitEngine.state.phase) {
                            OrbitBlasterPhase.Running -> {
                                pauseIndex = 0
                                orbitEngine.pause()
                            }
                            OrbitBlasterPhase.Paused -> orbitEngine.resume()
                            OrbitBlasterPhase.GameOver -> startOrbitBlaster()
                        }
                        GameId.MeteorDodge -> when (meteorEngine.state.phase) {
                            MeteorDodgePhase.Running -> {
                                pauseIndex = 0
                                meteorEngine.pause()
                            }
                            MeteorDodgePhase.Paused -> meteorEngine.resume()
                            MeteorDodgePhase.GameOver -> startMeteorDodge()
                        }
                        GameId.PixelRacer -> when (racerEngine.state.phase) {
                            PixelRacerPhase.Running -> {
                                pauseIndex = 0
                                racerEngine.pause()
                            }
                            PixelRacerPhase.Paused -> racerEngine.resume()
                            PixelRacerPhase.GameOver -> startPixelRacer()
                        }
                    }
                    AppScreen.PoweringOff, AppScreen.ComingSoon -> Unit
                    AppScreen.PoweredOff -> powerOn()
                }
            },
            onPower = {
                // Tap turns the console back on; powering off requires a hold.
                if (screen == AppScreen.PoweredOff) powerOn()
            },
            onPowerHeldChange = { powerHeld = it },
        )
    }

    val lcdBrightness = when (screen) {
        AppScreen.Boot -> BootTimeline.lcdBrightness(bootElapsed)
        AppScreen.PoweringOff -> PowerOffTimeline.lcdBrightness(shutdownElapsed)
        AppScreen.PoweredOff -> 0.02f
        else -> if (powerArmed) 0.85f else 1f // subtle dim hints the power hold
    }
    val ledBrightness = when (screen) {
        AppScreen.Boot -> BootTimeline.ledBrightness(bootElapsed)
        AppScreen.PoweringOff -> PowerOffTimeline.ledBrightness(shutdownElapsed)
        AppScreen.PoweredOff -> 0f
        else -> 1f
    }

    BackHandler(enabled = customizationOpen) { customizationOpen = false }
    BackHandler(enabled = privacyOpen) { privacyOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(PixelDropColors.RetroBackground)
            // Exposes the console power mode to accessibility + UI tests.
            .semantics { contentDescription = "mode:${screen.name}" }
    ) {
        SynthwaveBackdrop()
        RetroConsoleShell(
            input = input,
            lcdBrightness = lcdBrightness,
            ledBrightness = ledBrightness,
            theme = consoleThemeFor(selectedThemeId),
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(12.dp),
        ) {
            when (screen) {
                AppScreen.Boot -> BootScreen(bootElapsed)
                AppScreen.Menu -> GameMenuScreen(selected = selectedGameId)
                AppScreen.ComingSoon -> ComingSoonScreen(selectedGameId)
                AppScreen.Game -> when (selectedGameId) {
                    GameId.PixelDrop -> when (engine.state.phase) {
                        GamePhase.Running -> GameplayScreen(engine.state)
                        GamePhase.Paused -> PauseScreen(pauseIndex)
                        GamePhase.GameOver -> GameOverScreen(engine.state)
                    }
                    GameId.PixelTrail -> when (trailEngine.state.phase) {
                        PixelTrailPhase.Running -> PixelTrailScreen(trailEngine.state)
                        PixelTrailPhase.Paused -> PauseScreen(pauseIndex)
                        PixelTrailPhase.GameOver -> PixelTrailGameOverScreen(trailEngine.state)
                    }
                    GameId.BrickPulse -> when (brickEngine.state.phase) {
                        BrickPulsePhase.Running -> BrickPulseScreen(brickEngine.state)
                        BrickPulsePhase.Paused -> PauseScreen(pauseIndex)
                        BrickPulsePhase.GameOver -> BrickPulseGameOverScreen(brickEngine.state)
                    }
                    GameId.OrbitBlaster -> when (orbitEngine.state.phase) {
                        OrbitBlasterPhase.Running -> OrbitBlasterScreen(orbitEngine.state)
                        OrbitBlasterPhase.Paused -> PauseScreen(pauseIndex)
                        OrbitBlasterPhase.GameOver -> OrbitBlasterGameOverScreen(orbitEngine.state)
                    }
                    GameId.MeteorDodge -> when (meteorEngine.state.phase) {
                        MeteorDodgePhase.Running -> MeteorDodgeScreen(meteorEngine.state)
                        MeteorDodgePhase.Paused -> PauseScreen(pauseIndex)
                        MeteorDodgePhase.GameOver -> MeteorDodgeGameOverScreen(meteorEngine.state)
                    }
                    GameId.PixelRacer -> when (racerEngine.state.phase) {
                        PixelRacerPhase.Running -> PixelRacerScreen(racerEngine.state)
                        PixelRacerPhase.Paused -> PauseScreen(pauseIndex)
                        PixelRacerPhase.GameOver -> PixelRacerGameOverScreen(racerEngine.state)
                    }
                }
                AppScreen.PoweringOff -> {
                    // Frozen content stays visible until the collapse curtain
                    // has fully closed over it, then only the overlay draws.
                    if (shutdownElapsed < PowerOffTimeline.COLLAPSE_END) {
                        when (frozenScreen) {
                            AppScreen.Menu -> GameMenuScreen(selected = selectedGameId)
                            AppScreen.ComingSoon -> ComingSoonScreen(selectedGameId)
                            AppScreen.Game -> when (selectedGameId) {
                                GameId.PixelDrop -> when (frozenPhase) {
                                    GamePhase.Running -> GameplayScreen(engine.state)
                                    GamePhase.Paused -> PauseScreen(pauseIndex)
                                    GamePhase.GameOver -> GameOverScreen(engine.state)
                                }
                                GameId.PixelTrail -> when (frozenTrailPhase) {
                                    PixelTrailPhase.Running -> PixelTrailScreen(trailEngine.state)
                                    PixelTrailPhase.Paused -> PauseScreen(pauseIndex)
                                    PixelTrailPhase.GameOver -> PixelTrailGameOverScreen(trailEngine.state)
                                }
                                GameId.BrickPulse -> when (frozenBrickPhase) {
                                    BrickPulsePhase.Running -> BrickPulseScreen(brickEngine.state)
                                    BrickPulsePhase.Paused -> PauseScreen(pauseIndex)
                                    BrickPulsePhase.GameOver -> BrickPulseGameOverScreen(brickEngine.state)
                                }
                                GameId.OrbitBlaster -> when (frozenOrbitPhase) {
                                    OrbitBlasterPhase.Running -> OrbitBlasterScreen(orbitEngine.state)
                                    OrbitBlasterPhase.Paused -> PauseScreen(pauseIndex)
                                    OrbitBlasterPhase.GameOver -> OrbitBlasterGameOverScreen(orbitEngine.state)
                                }
                                GameId.MeteorDodge -> when (frozenMeteorPhase) {
                                    MeteorDodgePhase.Running -> MeteorDodgeScreen(meteorEngine.state)
                                    MeteorDodgePhase.Paused -> PauseScreen(pauseIndex)
                                    MeteorDodgePhase.GameOver -> MeteorDodgeGameOverScreen(meteorEngine.state)
                                }
                                GameId.PixelRacer -> when (frozenRacerPhase) {
                                    PixelRacerPhase.Running -> PixelRacerScreen(racerEngine.state)
                                    PixelRacerPhase.Paused -> PauseScreen(pauseIndex)
                                    PixelRacerPhase.GameOver -> PixelRacerGameOverScreen(racerEngine.state)
                                }
                            }
                            else -> Unit
                        }
                    }
                    PowerOffScreen(shutdownElapsed)
                }
                AppScreen.PoweredOff -> PowerOffScreen(PowerOffTimeline.TOTAL_MS)
            }
        }

        AnimatedVisibility(
            visible = screen == AppScreen.Menu && !customizationOpen && !privacyOpen,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp)
                .zIndex(2f),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            ConsoleCustomizerButton(
                expanded = customizationOpen,
                onClick = {
                    soundPlayer.playMenuSelect()
                    customizationOpen = true
                },
            )
        }

        AnimatedVisibility(
            visible = screen == AppScreen.Menu && !customizationOpen && !privacyOpen,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopStart)
                .safeDrawingPadding()
                .padding(16.dp)
                .zIndex(2f),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            PrivacyPolicyButton(
                onClick = {
                    soundPlayer.playMenuSelect()
                    privacyOpen = true
                },
            )
        }

        ConsoleCustomizer(
            visible = customizationOpen,
            selectedThemeId = selectedThemeId,
            onThemeSelected = { theme ->
                selectedThemeId = theme.id
                customizationPrefs.putString(CONSOLE_THEME_KEY, theme.id)
                soundPlayer.playMenuSelect()
            },
            onDismiss = { customizationOpen = false },
            modifier = Modifier.zIndex(3f),
        )

        AnimatedVisibility(
            visible = privacyOpen,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(4f),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PrivacyPolicyScreen(onClose = { privacyOpen = false })
        }
    }
}

/** 90's outrun backdrop: sunset gradient, a striped retro sun, star field,
 *  and a perspective grid floor converging behind the console. */
@Composable
private fun SynthwaveBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.60f

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(PixelDropColors.SynthSkyTop, PixelDropColors.SynthSkyMid, PixelDropColors.SynthSkyBottom),
                startY = 0f,
                endY = horizonY,
            ),
            size = Size(w, horizonY),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(PixelDropColors.SynthSkyBottom, PixelDropColors.RetroBackground),
                startY = horizonY,
                endY = h,
            ),
            topLeft = Offset(0f, horizonY),
            size = Size(w, h - horizonY),
        )

        // Retro sun with a few "missing" horizontal stripes near its base.
        val sunRadius = w * 0.30f
        val sunCenter = Offset(w * 0.5f, horizonY - sunRadius * 0.35f)
        clipRect(0f, 0f, w, horizonY) {
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(PixelDropColors.SynthSun, PixelDropColors.SynthSunCore),
                    startY = sunCenter.y - sunRadius,
                    endY = sunCenter.y + sunRadius,
                ),
                radius = sunRadius,
                center = sunCenter,
            )
            for (i in 0 until 5) {
                val stripeY = sunCenter.y + sunRadius * (0.15f + i * 0.16f)
                val stripeHeight = sunRadius * (0.03f + i * 0.012f)
                drawRect(
                    color = PixelDropColors.RetroBackground,
                    topLeft = Offset(sunCenter.x - sunRadius, stripeY),
                    size = Size(sunRadius * 2, stripeHeight),
                )
            }
        }

        // Star field above the horizon.
        val starStep = 34.dp.toPx()
        var sy = starStep / 2f
        var row = 0
        while (sy < horizonY * 0.85f) {
            var sx = if (row % 2 == 0) starStep / 2f else starStep
            while (sx < w) {
                drawCircle(PixelDropColors.SynthStar.copy(alpha = 0.55f), radius = 1.2.dp.toPx(), center = Offset(sx, sy))
                sx += starStep
            }
            sy += starStep
            row++
        }

        // Perspective grid floor, converging toward the horizon center.
        val vanishX = w * 0.5f
        val horizLines = 7
        for (i in 1..horizLines) {
            val t = i / horizLines.toFloat()
            val y = horizonY + (h - horizonY) * (t * t)
            drawLine(
                color = PixelDropColors.SynthGrid.copy(alpha = 0.35f * (1f - t) + 0.1f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        val vertLines = 9
        for (i in -vertLines / 2..vertLines / 2) {
            val spread = i / (vertLines / 2f)
            drawLine(
                color = PixelDropColors.SynthGrid.copy(alpha = 0.3f),
                start = Offset(vanishX, horizonY),
                end = Offset(vanishX + spread * w * 0.9f, h),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Soft vignette to keep focus on the console.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = center,
                radius = size.maxDimension * 0.75f,
            )
        )
    }
}

