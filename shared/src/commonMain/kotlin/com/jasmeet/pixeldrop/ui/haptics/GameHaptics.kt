package com.jasmeet.pixeldrop.ui.haptics

import com.jasmeet.pixeldrop.brickpulse.BrickPulsePhase
import com.jasmeet.pixeldrop.brickpulse.BrickPulseState
import com.jasmeet.pixeldrop.game.GamePhase
import com.jasmeet.pixeldrop.game.GameState
import com.jasmeet.pixeldrop.meteordodge.MeteorDodgePhase
import com.jasmeet.pixeldrop.meteordodge.MeteorDodgeState
import com.jasmeet.pixeldrop.orbitblaster.OrbitBlasterPhase
import com.jasmeet.pixeldrop.orbitblaster.OrbitBlasterState
import com.jasmeet.pixeldrop.pixelracer.PixelRacerPhase
import com.jasmeet.pixeldrop.pixelracer.PixelRacerState
import com.jasmeet.pixeldrop.pixeltrail.PixelTrailPhase
import com.jasmeet.pixeldrop.pixeltrail.PixelTrailState
import kotlin.math.abs

/**
 * Pure "previous state -> current state => haptic" rules, one per game, in the
 * same spirit as the host's sound triggers (monotonic counters and life/level
 * diffs) so haptics and sound stay in sync without touching any engine.
 *
 * Every function returns at most ONE event per state change (the strongest),
 * and returns null unless both snapshots are in the Running phase. That
 * single guard means: nothing while paused, nothing on the game-over screen,
 * and nothing on the reset jump from a finished/paused run to a fresh one.
 * The final, run-ending hit is deliberately silent here: [gameEndHaptic]
 * covers it once.
 */
object GameHaptics {

    /** PIXEL DROP: rotate/move none, lock light, line clear scaled by lines, level-up bumps it. */
    fun pixelDrop(prev: GameState, cur: GameState): HapticEvent? {
        if (prev.phase != GamePhase.Running || cur.phase != GamePhase.Running) return null
        val lines = cur.lines - prev.lines
        val leveledUp = cur.level > prev.level
        if (lines > 0) return lineClearHaptic(lines, leveledUp)
        // Board is rebuilt only when a piece locks.
        if (cur.board !== prev.board) return HapticEvent.Light
        return null
    }

    /** PIXEL TRAIL: dot eaten light. Death is the run-ending hit, so game-over covers it. */
    fun pixelTrail(prev: PixelTrailState, cur: PixelTrailState): HapticEvent? {
        if (prev.phase != PixelTrailPhase.Running || cur.phase != PixelTrailPhase.Running) return null
        return if (cur.foodCollected > prev.foodCollected) HapticEvent.Light else null
    }

    /** BRICK PULSE: brick light, power-up / level clear success, life lost impact. */
    fun brickPulse(prev: BrickPulseState, cur: BrickPulseState): HapticEvent? {
        if (prev.phase != BrickPulsePhase.Running || cur.phase != BrickPulsePhase.Running) return null
        val lifeLost = cur.lives < prev.lives
        // A catch shows as a new/refreshed effect, an extra life, or an extra ball.
        val caught = cur.lives > prev.lives ||
            cur.balls.size > prev.balls.size ||
            cur.activeEffects.any { e ->
                val before = prev.activeEffects.firstOrNull { it.type == e.type }
                before == null || e.remainingMs > before.remainingMs
            }
        return strongestHaptic(
            if (lifeLost) HapticEvent.Impact else null,
            if (caught || cur.level > prev.level) HapticEvent.Success else null,
            if (cur.bricksBroken > prev.bricksBroken) HapticEvent.Light else null,
        )
    }

    /** ORBIT BLASTER: shots none, kill light, wave clear success, hit impact (shield block medium). */
    fun orbitBlaster(prev: OrbitBlasterState, cur: OrbitBlasterState): HapticEvent? {
        if (prev.phase != OrbitBlasterPhase.Running || cur.phase != OrbitBlasterPhase.Running) return null
        val hit = cur.lives < prev.lives
        // A shield block spends the shield and grants grace without costing a life.
        val blocked = !hit && prev.shieldMs > 0L && cur.shieldMs == 0L && cur.invulnerableMs > prev.invulnerableMs
        return strongestHaptic(
            if (hit) HapticEvent.Impact else null,
            if (blocked) HapticEvent.Medium else null,
            if (cur.wave > prev.wave) HapticEvent.Success else null,
            if (cur.enemiesKilled > prev.enemiesKilled) HapticEvent.Light else null,
        )
    }

    /** METEOR DODGE: near-miss warning, bonus success, shield-eaten hit impact. */
    fun meteorDodge(prev: MeteorDodgeState, cur: MeteorDodgeState): HapticEvent? {
        if (prev.phase != MeteorDodgePhase.Running || cur.phase != MeteorDodgePhase.Running) return null
        val shieldSpent = prev.shieldActive && !cur.shieldActive && cur.bonusesCollected == prev.bonusesCollected
        return strongestHaptic(
            if (shieldSpent) HapticEvent.Impact else null,
            if (cur.bonusesCollected > prev.bonusesCollected) HapticEvent.Success else null,
            if (meteorNearMiss(prev, cur)) HapticEvent.Warning else null,
        )
    }

    /** Half the ship's hitbox width; mirrors MeteorDodgeEngine.PLAYER_HITBOX_WIDTH / 2. */
    private const val METEOR_HITBOX_HALF_W = 2.5f
    /** A rock that clears the hitbox by no more than this (field units) is a near miss. */
    const val METEOR_NEAR_MISS_MARGIN = 3f

    /**
     * A rock counts once, on the frame its centre crosses the ship's row, when
     * it passed beside the hitbox without touching it. State-only: MeteorDodge
     * has no near-miss counter and its engine is left alone.
     */
    fun meteorNearMiss(prev: MeteorDodgeState, cur: MeteorDodgeState): Boolean {
        for (m in cur.meteors) {
            val before = prev.meteors.firstOrNull { it.id == m.id } ?: continue
            if (before.y < cur.playerY && m.y >= cur.playerY) {
                val gap = abs(m.x - cur.playerX) - m.radius - METEOR_HITBOX_HALF_W
                if (gap > 0f && gap <= METEOR_NEAR_MISS_MARGIN) return true
            }
        }
        return false
    }

    /** PIXEL RACER: near-miss / low fuel warning, coin+fuel light, power-up success, crash impact. */
    fun pixelRacer(prev: PixelRacerState, cur: PixelRacerState): HapticEvent? {
        if (prev.phase != PixelRacerPhase.Running || cur.phase != PixelRacerPhase.Running) return null
        return strongestHaptic(
            if (cur.crashes > prev.crashes) HapticEvent.Impact else null,
            if (cur.powerupsCollected > prev.powerupsCollected) HapticEvent.Success else null,
            if (cur.nearMisses > prev.nearMisses || cur.lowFuelWarnings > prev.lowFuelWarnings) HapticEvent.Warning else null,
            if (cur.coinsCollected > prev.coinsCollected || cur.fuelCollected > prev.fuelCollected) HapticEvent.Light else null,
        )
    }

}

/** The physical controls, for press feedback. */
enum class ConsoleButton { Up, Down, Left, Right, A, B, Start, Power }

/** What the LCD is doing, from the haptics point of view. */
enum class ShellMode {
    /** Games menu. */
    Menu,
    /** A round is actually running: game events, not presses, carry the feedback. */
    GameRunning,
    /** Pause menu or game-over screen. */
    GameIdle,
    /** Boot / coming-soon / powering off. */
    Other,
    /** Console is off. Waking it has its own haptic. */
    Off,
}

/**
 * Feedback for pressing a physical button, or null for silence. One press
 * yields at most one haptic, and input that does nothing yields none.
 * During a running round the D-pad / A / B stay silent: the game's own event
 * (a jump, a lock, a bomb) is the feedback, so an action never buzzes twice
 * and mashing the D-pad is never a constant hum.
 */
fun buttonHaptic(mode: ShellMode, button: ConsoleButton): HapticEvent? = when (mode) {
    ShellMode.Off -> null
    ShellMode.GameRunning -> when (button) {
        ConsoleButton.Start -> HapticEvent.Tick // pause
        ConsoleButton.Power -> HapticEvent.Medium
        else -> null
    }
    ShellMode.Menu -> when (button) {
        ConsoleButton.Up, ConsoleButton.Down -> HapticEvent.Select
        ConsoleButton.A, ConsoleButton.Start -> HapticEvent.Medium // confirm / launch
        ConsoleButton.Power -> HapticEvent.Medium
        ConsoleButton.Left, ConsoleButton.Right, ConsoleButton.B -> null // nothing happens
    }
    ShellMode.GameIdle -> when (button) {
        ConsoleButton.Up, ConsoleButton.Down -> HapticEvent.Select
        ConsoleButton.A, ConsoleButton.B, ConsoleButton.Start -> HapticEvent.Tick
        ConsoleButton.Power -> HapticEvent.Medium
        ConsoleButton.Left, ConsoleButton.Right -> null
    }
    ShellMode.Other -> when (button) {
        ConsoleButton.Power -> HapticEvent.Medium
        ConsoleButton.Start, ConsoleButton.A -> HapticEvent.Tick
        else -> null
    }
}
