package com.jasmeet.pixeldrop.ui.haptics


/**
 * Semantic haptic vocabulary. Call sites name the *moment*, never a duration
 * or amplitude; [HapticPolicy] owns the mapping and [RetroHaptics] the
 * hardware. [priority] decides who wins when two events land close together.
 */
enum class HapticEvent(val priority: Int, val minRepeatMs: Long = 0L) {
    /** Generic control press. */
    Tick(1),
    /** Menu move / pause-menu move. */
    Select(1),
    /** Landing after a full jump: barely there. */
    VeryLight(1),
    /** Small accepted action: lock, pickup, brick, jump. */
    Light(2),
    /** Notable action: menu confirm, power button, single blast. */
    Medium(3),
    /** Two chained blasts. */
    MediumChain2(3),
    /** Three (or more, capped) chained blasts. */
    MediumChain3(3),
    /** Near-miss / low resource: two short equal pulses. */
    Warning(3),
    Heavy(4),
    /** Reward: line clear, pickup power-up, level-up. */
    Success(4),
    /** Bigger reward: 3+ lines, line clear that also levels up. */
    SuccessBig(5),
    /** Console power on/off. */
    Power(4),
    /** Something hurt the player: hit, crash, life lost. */
    Impact(5),
    /** New best score: rising multi-pulse. */
    Celebrate(6),
    /** Run over: one long low thud. */
    GameOver(6),

    // ---- TOWER STACK vocabulary (see GAME_09_DESIGN.md section 12) ----
    /** Soft, low, very short thud: an accepted drop that trimmed almost nothing. */
    StackLand(1),
    /** Trim scaled by the trimmed fraction: light, medium, heavy. */
    StackTrim(2),
    StackTrimMedium(3),
    StackTrimHeavy(4),
    /** PERFECT ramp by streak: 1 tick, 2-3 double, 4-6 triple rising, 7+ rising chord. */
    StackPerfect1(2),
    StackPerfect2(3),
    StackPerfect3(4),
    StackPerfect4(4),
    /** Long resonant ring at streak milestones 3, 6, 9, 12. */
    StackMilestone(5),
    /** Star block power-up gained: bright double tick. */
    StackPowerUp(3),
    /** Power-up spent with B. */
    StackUse(3),
    /** 10th floor: SuccessBig-style. */
    StackLevelUp(5),
    /** Wind gust rumble: very gentle, repeats at most every 350 ms. */
    StackWind(1, minRepeatMs = 350L),
    /** Total miss: heavy short thud that hands off to [GameOver]. */
    StackMiss(5),
    /** Safety net catch: bounce-back double thump. */
    StackNet(4),

    // ---- RALLY DUEL vocabulary (see GAME_10_DESIGN.md): the heaviest set in the console ----
    /** Your return: five tiers by ball speed, 140 up to 255, a crisp 14-20 ms thump. */
    RallyHit(2),
    RallyHitMid(2),
    RallyHitFirm(3),
    RallyHitHard(3),
    RallyHitMax(3),
    /** The bot's return: a lighter tick. */
    RallyHitBot(1),
    /** Top / bottom wall: very light, and never more than ~9 a second. */
    RallyWall(1, minRepeatMs = 110L),
    /** Smash that connects: 255 crack, short gap, 200 decaying tail. */
    RallySmash(5),
    /** Curve that connects: three quick ramping pulses, a rolling swirl. */
    RallyCurve(3),
    RallyPointWin(4),
    /** Heavy 255 thud, decaying. */
    RallyPointLose(5),
    /** Rally 10 / 20: a strong decaying ring. */
    RallyMilestone(4),
    RallyPowerUp(3),
    /** Serve countdown: READY, READY, GO. */
    RallyServe(2),
    RallyServeMid(2),
    RallyServeGo(3),
    RallyMatchWin(6),
    /** Run over: the longest regular descending thud; the host plays it in place of the shared game-over pattern. */
    RallyRunOver(7),
    /** Second wind: double thump at full strength. */
    RallySecondWind(5),
    /** Boss intro: three 255 pulses over the siren. */
    BossWarning(6),
    /** Weak spot or core hit: crack plus shatter ring. */
    BossBreak(6),
    BossStun(3),
    /** Phase change: long rising rumble. */
    BossPhase(6),
    /** Boss defeated: the biggest pattern in the console. */
    BossDefeat(8),
    /** LADDER CLEARED: a rising multi-pulse build to full strength, then a ringing decay (over 1.2 s). */
    RallyVictory(9),
}

/** Tower Stack streak milestones (3/6/9/12); kept here so haptics need no engine import. */
private fun isStreakMilestone(streak: Int): Boolean = streak == 3 || streak == 6 || streak == 9 || streak == 12

/** Android-independent stand-ins for `HapticFeedbackConstants`. */
enum class ViewKind { VirtualKey, ClockTick }

/** Stand-ins for `VibrationEffect.EFFECT_*` (API 29+). */
enum class PredefinedKind { Tick, Click, HeavyClick }

/**
 * Off/on waveform in `VibrationEffect.createWaveform` layout: index 0 is the
 * initial delay, then alternating on/off durations in ms. [amplitudes] is
 * parallel (0 = off, 1..255 = strength). Lists, not arrays, so plans compare
 * by value.
 */
data class Wave(val timings: List<Long>, val amplitudes: List<Int>) {
    init {
        require(timings.size == amplitudes.size) { "timings and amplitudes must be parallel" }
    }

    val durationMs: Long get() = timings.sum()

    /**
     * Strict off/on/off/on pattern for `Vibrator.vibrate(long[])` and for
     * amplitude-less hardware: adjacent on-segments (or gaps) are merged, so a
     * stepped amplitude ramp degrades to one clean buzz instead of scrambling
     * which entries count as "on". Index 0 is the initial delay.
     */
    fun toLegacyPattern(): LongArray {
        val out = ArrayList<Long>(timings.size)
        out += timings[0]
        var lastOn = false
        for (i in 1 until timings.size) {
            val on = amplitudes[i] > 0
            if (on == lastOn && out.size > 1) {
                out[out.size - 1] = out[out.size - 1] + timings[i]
            } else if (!on && out.size == 1) {
                // A leading gap folds into the initial delay.
                out[0] = out[0] + timings[i]
            } else {
                out += timings[i]
                lastOn = on
            }
        }
        return out.toLongArray()
    }
}

/** What the hardware layer should do for one event. */
sealed interface HapticPlan {
    /** Time this plan occupies the motor; the policy blocks weaker events meanwhile. */
    val durationMs: Long

    /** Touch-style feedback via the View (honors the system touch-feedback setting). */
    data class ViewFeedback(val kind: ViewKind, val fallback: Wave) : HapticPlan {
        override val durationMs: Long get() = 0L
    }

    /** Built-in system effect (API 29+); [fallback] elsewhere. */
    data class Predefined(val kind: PredefinedKind, val fallback: Wave) : HapticPlan {
        override val durationMs: Long get() = fallback.durationMs
    }

    /** Custom pulse train. */
    data class Waveform(val wave: Wave) : HapticPlan {
        override val durationMs: Long get() = wave.durationMs
    }
}

private fun wave(vararg pairs: Pair<Long, Int>): Wave {
    // pairs are (durationMs, amplitude); off-gaps carry amplitude 0.
    val timings = ArrayList<Long>(pairs.size + 1)
    val amps = ArrayList<Int>(pairs.size + 1)
    timings += 0L; amps += 0
    for ((d, a) in pairs) { timings += d; amps += a }
    return Wave(timings, amps)
}

private const val GAP = 0 // readability marker for off segments

/** The event -> pattern table. Pure data; the single source of truth for feel. */
fun hapticPlanFor(event: HapticEvent): HapticPlan = when (event) {
    HapticEvent.Tick -> HapticPlan.ViewFeedback(ViewKind.VirtualKey, wave(10L to 60))
    HapticEvent.Select -> HapticPlan.ViewFeedback(ViewKind.ClockTick, wave(12L to 70))
    HapticEvent.VeryLight -> HapticPlan.Waveform(wave(8L to 40))
    HapticEvent.Light -> HapticPlan.Predefined(PredefinedKind.Tick, wave(14L to 80))
    HapticEvent.Medium -> HapticPlan.Predefined(PredefinedKind.Click, wave(22L to 140))
    HapticEvent.MediumChain2 -> HapticPlan.Waveform(wave(22L to 140, 50L to GAP, 22L to 150))
    HapticEvent.MediumChain3 -> HapticPlan.Waveform(
        wave(22L to 140, 50L to GAP, 22L to 150, 50L to GAP, 24L to 170),
    )
    HapticEvent.Warning -> HapticPlan.Waveform(wave(14L to 120, 55L to GAP, 14L to 120))
    HapticEvent.Heavy -> HapticPlan.Predefined(PredefinedKind.HeavyClick, wave(36L to 220))
    HapticEvent.Success -> HapticPlan.Waveform(wave(16L to 90, 45L to GAP, 26L to 190))
    HapticEvent.SuccessBig -> HapticPlan.Waveform(
        wave(16L to 90, 45L to GAP, 22L to 150, 45L to GAP, 34L to 230),
    )
    HapticEvent.Power -> HapticPlan.Waveform(wave(30L to 150, 60L to GAP, 50L to 235))
    HapticEvent.Impact -> HapticPlan.Waveform(wave(38L to 255, 30L to GAP, 22L to 110))
    HapticEvent.Celebrate -> HapticPlan.Waveform(
        wave(14L to 70, 60L to GAP, 18L to 120, 60L to GAP, 26L to 180, 60L to GAP, 46L to 255),
    )
    HapticEvent.GameOver -> HapticPlan.Waveform(wave(120L to 255, 20L to GAP, 160L to 120))

    // ---- TOWER STACK ----
    HapticEvent.StackLand -> HapticPlan.Waveform(wave(10L to 55))
    HapticEvent.StackTrim -> HapticPlan.Waveform(wave(12L to 90))
    HapticEvent.StackTrimMedium -> HapticPlan.Waveform(wave(14L to 140))
    HapticEvent.StackTrimHeavy -> HapticPlan.Waveform(wave(18L to 200, 16L to GAP, 12L to 110))
    // Perfect ramp: every step is stronger in total energy than the one before.
    HapticEvent.StackPerfect1 -> HapticPlan.Waveform(wave(10L to 120))
    HapticEvent.StackPerfect2 -> HapticPlan.Waveform(wave(10L to 120, 35L to GAP, 12L to 150))
    HapticEvent.StackPerfect3 -> HapticPlan.Waveform(
        wave(10L to 110, 30L to GAP, 12L to 160, 30L to GAP, 14L to 215),
    )
    // Rising "chord": contiguous amplitude steps, then a strong closing tap.
    HapticEvent.StackPerfect4 -> HapticPlan.Waveform(
        wave(8L to 70, 8L to 110, 8L to 150, 8L to 190, 10L to 230, 40L to GAP, 18L to 255),
    )
    // Resonant ring: swells to full strength and decays like a struck bell.
    HapticEvent.StackMilestone -> HapticPlan.Waveform(
        wave(
            12L to 100, 12L to 160, 14L to 220, 16L to 255, 20L to 200,
            22L to 150, 26L to 100, 30L to 60, 36L to 30,
        ),
    )
    HapticEvent.StackPowerUp -> HapticPlan.Waveform(wave(9L to 130, 40L to GAP, 9L to 170))
    HapticEvent.StackUse -> HapticPlan.Predefined(PredefinedKind.Click, wave(20L to 140))
    HapticEvent.StackLevelUp -> HapticPlan.Waveform(
        wave(16L to 100, 45L to GAP, 22L to 160, 45L to GAP, 30L to 220, 40L to GAP, 40L to 255),
    )
    // Gentle swell, well under the perceptible-nag level.
    HapticEvent.StackWind -> HapticPlan.Waveform(wave(16L to 30, 16L to 50, 16L to 70, 16L to 50, 16L to 30))
    HapticEvent.StackMiss -> HapticPlan.Waveform(wave(34L to 255, 18L to GAP, 14L to 130))
    HapticEvent.StackNet -> HapticPlan.Waveform(wave(26L to 200, 60L to GAP, 20L to 150))

    // ---- RALLY DUEL ----
    HapticEvent.RallyHit -> HapticPlan.Waveform(wave(14L to 140))
    HapticEvent.RallyHitMid -> HapticPlan.Waveform(wave(15L to 170))
    HapticEvent.RallyHitFirm -> HapticPlan.Waveform(wave(16L to 200))
    HapticEvent.RallyHitHard -> HapticPlan.Waveform(wave(18L to 230))
    HapticEvent.RallyHitMax -> HapticPlan.Waveform(wave(20L to 255))
    HapticEvent.RallyHitBot -> HapticPlan.Waveform(wave(10L to 75))
    HapticEvent.RallyWall -> HapticPlan.Waveform(wave(6L to 35))
    HapticEvent.RallySmash -> HapticPlan.Waveform(wave(22L to 255, 25L to GAP, 24L to 200, 18L to 150, 18L to 100))
    HapticEvent.RallyCurve -> HapticPlan.Waveform(wave(12L to 120, 18L to GAP, 14L to 170, 18L to GAP, 18L to 230))
    HapticEvent.RallyPointWin -> HapticPlan.Waveform(wave(20L to 150, 40L to GAP, 24L to 200, 40L to GAP, 36L to 255))
    HapticEvent.RallyPointLose -> HapticPlan.Waveform(wave(40L to 255, 15L to 200, 15L to 120))
    HapticEvent.RallyMilestone -> HapticPlan.Waveform(
        wave(16L to 255, 20L to 230, 24L to 200, 28L to 170, 30L to 130, 32L to 95, 34L to 60, 36L to 35),
    )
    HapticEvent.RallyPowerUp -> HapticPlan.Waveform(wave(14L to 200, 35L to GAP, 16L to 240))
    HapticEvent.RallyServe -> HapticPlan.Waveform(wave(12L to 110))
    HapticEvent.RallyServeMid -> HapticPlan.Waveform(wave(14L to 150))
    HapticEvent.RallyServeGo -> HapticPlan.Waveform(wave(26L to 220))
    HapticEvent.RallyMatchWin -> HapticPlan.Waveform(
        wave(
            20L to 140, 40L to GAP, 24L to 170, 40L to GAP, 28L to 200, 40L to GAP,
            34L to 230, 40L to GAP, 60L to 255, 20L to GAP, 40L to 180,
        ),
    )
    HapticEvent.RallyRunOver -> HapticPlan.Waveform(
        wave(
            90L to 255, 20L to GAP, 70L to 220, 30L to GAP, 60L to 170, 30L to GAP,
            50L to 120, 30L to GAP, 40L to 80, 30L to GAP, 40L to 50,
        ),
    )
    HapticEvent.RallySecondWind -> HapticPlan.Waveform(wave(28L to 255, 60L to GAP, 34L to 255))
    HapticEvent.BossWarning -> HapticPlan.Waveform(wave(130L to 255, 400L to GAP, 130L to 255, 400L to GAP, 130L to 255))
    HapticEvent.BossBreak -> HapticPlan.Waveform(
        wave(30L to 255, 12L to GAP, 20L to 210, 20L to 160, 20L to 115, 20L to 75, 20L to 40),
    )
    HapticEvent.BossStun -> HapticPlan.Waveform(wave(24L to 150, 30L to GAP, 18L to 120))
    HapticEvent.BossPhase -> HapticPlan.Waveform(
        wave(
            45L to 80, 45L to 100, 45L to 120, 45L to 140, 45L to 160, 45L to 180,
            45L to 200, 45L to 215, 45L to 230, 45L to 240, 45L to 250, 60L to 255,
        ),
    )
    HapticEvent.BossDefeat -> HapticPlan.Waveform(
        wave(
            60L to 255, 40L to GAP,
            30L to 140, 50L to GAP, 34L to 170, 50L to GAP, 38L to 200, 50L to GAP, 44L to 230, 50L to GAP,
            60L to 255, 40L to GAP, 80L to 255, 40L to GAP, 120L to 255, 40L to GAP, 160L to 255,
            60L to GAP, 200L to 255, 60L to GAP, 100L to 255,
        ),
    )
    HapticEvent.RallyVictory -> HapticPlan.Waveform(
        wave(
            // Build: eight pulses climbing to 255 over ~700 ms.
            40L to 90, 50L to GAP, 50L to 120, 50L to GAP, 60L to 150, 45L to GAP, 70L to 185, 45L to GAP,
            80L to 215, 40L to GAP, 90L to 240, 40L to GAP, 110L to 255, 40L to GAP,
            // Ringing decay: struck-bell pulses with growing gaps.
            90L to 255, 40L to GAP, 70L to 215, 45L to GAP, 55L to 170, 50L to GAP, 45L to 125, 50L to GAP,
            35L to 85, 55L to GAP, 28L to 55, 60L to GAP, 22L to 30,
        ),
    )
}

/** Your return: stronger the faster the ball (140 at serve speed up to 255 near the cap). */
fun rallyHitHaptic(speed: Float): HapticEvent = when {
    speed < 130f -> HapticEvent.RallyHit
    speed < 165f -> HapticEvent.RallyHitMid
    speed < 200f -> HapticEvent.RallyHitFirm
    speed < 235f -> HapticEvent.RallyHitHard
    else -> HapticEvent.RallyHitMax
}

/** Countdown tick for step 1 (READY), 2 (READY) or 3 (GO). */
fun rallyServeHaptic(step: Int): HapticEvent = when {
    step <= 1 -> HapticEvent.RallyServe
    step == 2 -> HapticEvent.RallyServeMid
    else -> HapticEvent.RallyServeGo
}

/** The shared game-over moment of a RALLY DUEL run: a new best celebrates, otherwise the long descending thud. */
fun rallyEndHaptic(isNewBest: Boolean): HapticEvent =
    if (isNewBest) HapticEvent.Celebrate else HapticEvent.RallyRunOver

/**
 * The game-over moment of a run that ended by finishing the LADDER CLEARED screen (B): the victory
 * pattern already played when that screen began, so a win never adds the defeat thud; only a NEW
 * BEST celebrates, and otherwise nothing fires.
 */
fun rallyWinEndHaptic(isNewBest: Boolean): HapticEvent? = if (isNewBest) HapticEvent.Celebrate else null

/** Perfect drop feedback rises with the streak: tick, double, triple, rising chord. */
fun stackPerfectHaptic(streak: Int): HapticEvent = when {
    streak <= 1 -> HapticEvent.StackPerfect1
    streak <= 3 -> HapticEvent.StackPerfect2
    streak <= 6 -> HapticEvent.StackPerfect3
    else -> HapticEvent.StackPerfect4
}

/** Streak 3, 6, 9 and 12 ring longer than the ramp step they land on. */
fun stackPerfectOrMilestoneHaptic(streak: Int): HapticEvent =
    if (isStreakMilestone(streak)) HapticEvent.StackMilestone else stackPerfectHaptic(streak)

/** Trim feedback scales with how much of the block was cut off (0..1); tiny trims are just the landing thud. */
fun stackTrimHaptic(fraction: Float): HapticEvent = when {
    fraction < 0.10f -> HapticEvent.StackLand
    fraction < 0.30f -> HapticEvent.StackTrim
    fraction < 0.55f -> HapticEvent.StackTrimMedium
    else -> HapticEvent.StackTrimHeavy
}

/** New-best runs celebrate; every other run just gets the thud. One haptic, never both. */
fun gameEndHaptic(isNewBest: Boolean): HapticEvent =
    if (isNewBest) HapticEvent.Celebrate else HapticEvent.GameOver

/** Blast pulses follow the chain length, capped at three so a long chain never becomes a buzz. */
fun chainHaptic(chainSteps: Int): HapticEvent = when {
    chainSteps <= 1 -> HapticEvent.Medium
    chainSteps == 2 -> HapticEvent.MediumChain2
    else -> HapticEvent.MediumChain3
}

/** Line clears scale with size; a clear that also levels up counts as the big one. */
fun lineClearHaptic(lines: Int, leveledUp: Boolean): HapticEvent =
    if (lines >= 3 || leveledUp) HapticEvent.SuccessBig else HapticEvent.Success

/**
 * Strongest of several events fired in the same frame, or null. Keeps "one
 * action, one haptic". Fixed-arity overloads (no vararg) so the per-frame
 * diff path allocates nothing; ties keep the earlier argument.
 */
fun strongestHaptic(a: HapticEvent?, b: HapticEvent?): HapticEvent? =
    if (a == null) b else if (b == null) a else if (b.priority > a.priority) b else a

fun strongestHaptic(a: HapticEvent?, b: HapticEvent?, c: HapticEvent?): HapticEvent? =
    strongestHaptic(strongestHaptic(a, b), c)

fun strongestHaptic(a: HapticEvent?, b: HapticEvent?, c: HapticEvent?, d: HapticEvent?): HapticEvent? =
    strongestHaptic(strongestHaptic(a, b, c), d)

fun strongestHaptic(a: HapticEvent?, b: HapticEvent?, c: HapticEvent?, d: HapticEvent?, e: HapticEvent?): HapticEvent? =
    strongestHaptic(strongestHaptic(a, b, c, d), e)

/**
 * Decides *whether* an event may fire and *what* it looks like. No Android
 * types, so the rules are unit-testable on the JVM.
 *
 * Rules:
 *  - disabled (user setting) or suspended (backgrounded / window focus lost) => silence
 *  - a new event inside [minIntervalMs] of the previous one, or while the
 *    previous pattern is still playing, is dropped unless it outranks it
 *
 * The clock is passed in, never read.
 */
class HapticPolicy(private val minIntervalMs: Long = MIN_INTERVAL_MS) {
    var enabled: Boolean = true
    var suspended: Boolean = false

    private var blockedUntilMs = Long.MIN_VALUE
    private var lastPriority = 0

    /** When each event last actually fired, for events with their own [HapticEvent.minRepeatMs]. */
    private val lastFiredMs = LongArray(HapticEvent.entries.size) { Long.MIN_VALUE }

    /** Returns the plan to play now, or null when the event must stay silent. */
    fun plan(event: HapticEvent, nowMs: Long): HapticPlan? {
        if (!enabled || suspended) return null
        // Per-event repeat limit (e.g. the wind rumble): never re-fires inside its own window.
        val last = lastFiredMs[event.ordinal]
        if (event.minRepeatMs > 0L && last != Long.MIN_VALUE && nowMs - last < event.minRepeatMs) return null
        if (nowMs < blockedUntilMs && event.priority <= lastPriority) return null
        val plan = hapticPlanFor(event)
        blockedUntilMs = nowMs + maxOf(minIntervalMs, plan.durationMs)
        lastPriority = event.priority
        lastFiredMs[event.ordinal] = nowMs
        return plan
    }

    /** Forget rate-limit history, e.g. after resuming from the background. */
    fun reset() {
        blockedUntilMs = Long.MIN_VALUE
        lastPriority = 0
        lastFiredMs.fill(Long.MIN_VALUE)
    }

    companion object {
        const val MIN_INTERVAL_MS = 40L
    }
}
