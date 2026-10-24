package com.jasmeet.pixeldrop.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPolicyTest {

    @Test
    fun `every event has a plan and every waveform is well formed`() {
        for (event in HapticEvent.entries) {
            val plan = hapticPlanFor(event)
            val wave = when (plan) {
                is HapticPlan.ViewFeedback -> plan.fallback
                is HapticPlan.Predefined -> plan.fallback
                is HapticPlan.Waveform -> plan.wave
            }
            assertEquals("$event", wave.timings.size, wave.amplitudes.size)
            assertEquals("$event initial delay", 0L, wave.timings.first())
            assertTrue("$event has an on segment", wave.timings.drop(1).any { it > 0 })
            assertTrue("$event amplitudes in range", wave.amplitudes.all { it in 0..255 })
            // The RALLY DUEL set is the heaviest in the console and may run longer (run over, boss defeat).
            val limit = if (event.name.startsWith("Rally") || event.name.startsWith("Boss")) 1_500L else 400L
            assertTrue("$event stays short", wave.durationMs <= limit)
        }
    }

    @Test
    fun `touch events use view feedback and reward events use custom waveforms`() {
        assertTrue(hapticPlanFor(HapticEvent.Tick) is HapticPlan.ViewFeedback)
        assertTrue(hapticPlanFor(HapticEvent.Select) is HapticPlan.ViewFeedback)
        assertTrue(hapticPlanFor(HapticEvent.Light) is HapticPlan.Predefined)
        assertTrue(hapticPlanFor(HapticEvent.Success) is HapticPlan.Waveform)
        assertTrue(hapticPlanFor(HapticEvent.Impact) is HapticPlan.Waveform)
    }

    @Test
    fun `celebrate rises through three or more pulses and game over is one long thud`() {
        val celebrate = (hapticPlanFor(HapticEvent.Celebrate) as HapticPlan.Waveform).wave
        val pulses = celebrate.amplitudes.filter { it > 0 }
        assertTrue(pulses.size >= 3)
        assertEquals(pulses.sorted(), pulses) // strictly rising strength

        val thud = (hapticPlanFor(HapticEvent.GameOver) as HapticPlan.Waveform).wave
        val longestOn = thud.timings.filterIndexed { i, _ -> thud.amplitudes[i] > 0 }.max()
        assertTrue("game over thud should be the longest single pulse", longestOn >= 100)
        assertTrue(longestOn > (hapticPlanFor(HapticEvent.Impact) as HapticPlan.Waveform).wave.timings.max())
    }

    @Test
    fun `game end picks celebrate for a new best and the thud otherwise, never both`() {
        assertEquals(HapticEvent.Celebrate, gameEndHaptic(true))
        assertEquals(HapticEvent.GameOver, gameEndHaptic(false))
    }

    @Test
    fun `line clears scale with size and a level-up upgrades them`() {
        assertEquals(HapticEvent.Success, lineClearHaptic(1, false))
        assertEquals(HapticEvent.Success, lineClearHaptic(2, false))
        assertEquals(HapticEvent.SuccessBig, lineClearHaptic(3, false))
        assertEquals(HapticEvent.SuccessBig, lineClearHaptic(4, false))
        assertEquals(HapticEvent.SuccessBig, lineClearHaptic(1, true))
    }

    @Test
    fun `chain blasts are capped at three pulses`() {
        assertEquals(HapticEvent.Medium, chainHaptic(1))
        assertEquals(HapticEvent.MediumChain2, chainHaptic(2))
        assertEquals(HapticEvent.MediumChain3, chainHaptic(3))
        assertEquals(HapticEvent.MediumChain3, chainHaptic(9))
    }

    @Test
    fun `strongest event wins and ties keep the first`() {
        assertNull(strongestHaptic(null, null))
        assertEquals(HapticEvent.Light, strongestHaptic(null, HapticEvent.Light))
        assertEquals(HapticEvent.Impact, strongestHaptic(HapticEvent.Light, HapticEvent.Impact, HapticEvent.Success))
        assertEquals(HapticEvent.Warning, strongestHaptic(HapticEvent.Warning, HapticEvent.Medium))
    }

    @Test
    fun `disabled policy never fires`() {
        val policy = HapticPolicy().apply { enabled = false }
        for (event in HapticEvent.entries) assertNull(policy.plan(event, 1_000L))
    }

    @Test
    fun `suspended policy never fires and resumes cleanly`() {
        val policy = HapticPolicy().apply { suspended = true }
        assertNull(policy.plan(HapticEvent.Impact, 1_000L))
        policy.suspended = false
        assertNotNull(policy.plan(HapticEvent.Impact, 1_001L))
    }

    @Test
    fun `an event inside the minimum interval is dropped, then allowed after it`() {
        val policy = HapticPolicy(minIntervalMs = 40L)
        assertNotNull(policy.plan(HapticEvent.Light, 1_000L))
        assertNull(policy.plan(HapticEvent.Light, 1_039L))
        assertNull(policy.plan(HapticEvent.Tick, 1_010L)) // weaker never cuts in
        assertNotNull(policy.plan(HapticEvent.Light, 1_040L))
    }

    @Test
    fun `a stronger event may cut through the interval`() {
        val policy = HapticPolicy(minIntervalMs = 40L)
        assertNotNull(policy.plan(HapticEvent.Light, 1_000L))
        assertNotNull(policy.plan(HapticEvent.Impact, 1_005L))
    }

    @Test
    fun `a long pattern shields itself from equal or weaker events until it ends`() {
        val policy = HapticPolicy(minIntervalMs = 40L)
        val celebrate = hapticPlanFor(HapticEvent.Celebrate).durationMs
        assertNotNull(policy.plan(HapticEvent.Celebrate, 1_000L))
        assertNull(policy.plan(HapticEvent.Success, 1_000L + celebrate - 1))
        assertNull(policy.plan(HapticEvent.GameOver, 1_000L + celebrate - 1)) // equal priority
        assertNotNull(policy.plan(HapticEvent.Success, 1_000L + celebrate))
    }

    @Test
    fun `a burst of identical events cannot buzz`() {
        val policy = HapticPolicy(minIntervalMs = 40L)
        var fired = 0
        // A frame-rate flood: one event every 16 ms for one second.
        for (t in 0L until 1_000L step 16L) {
            if (policy.plan(HapticEvent.Light, t) != null) fired++
        }
        assertTrue("fired $fired times in 1s", fired <= 1_000 / 40 + 1)
        assertTrue(fired >= 10)
    }

    @Test
    fun `reset clears rate limit history`() {
        val policy = HapticPolicy()
        assertNotNull(policy.plan(HapticEvent.Impact, 1_000L))
        assertNull(policy.plan(HapticEvent.Impact, 1_010L))
        policy.reset()
        assertNotNull(policy.plan(HapticEvent.Impact, 1_011L))
    }

    private fun waveOf(event: HapticEvent): Wave = when (val p = hapticPlanFor(event)) {
        is HapticPlan.ViewFeedback -> p.fallback
        is HapticPlan.Predefined -> p.fallback
        is HapticPlan.Waveform -> p.wave
    }

    /** Total motor energy: sum of duration x amplitude over the segments. */
    private fun energy(event: HapticEvent): Long {
        val w = waveOf(event)
        var e = 0L
        for (i in w.timings.indices) e += w.timings[i] * w.amplitudes[i]
        return e
    }

    private fun peak(event: HapticEvent): Int = waveOf(event).amplitudes.max()

    @Test
    fun `stack perfect ramp grows in energy peak and priority with the streak`() {
        val ramp = listOf(
            HapticEvent.StackPerfect1, HapticEvent.StackPerfect2, HapticEvent.StackPerfect3,
            HapticEvent.StackPerfect4, HapticEvent.StackMilestone,
        )
        for (i in 1 until ramp.size) {
            assertTrue("${ramp[i]} energy", energy(ramp[i]) > energy(ramp[i - 1]))
            assertTrue("${ramp[i]} peak", peak(ramp[i]) >= peak(ramp[i - 1]))
            assertTrue("${ramp[i]} priority", ramp[i].priority >= ramp[i - 1].priority)
        }
        // Streak -> event is monotonic across the whole range.
        var last = stackPerfectHaptic(1)
        for (streak in 2..30) {
            val e = stackPerfectHaptic(streak)
            assertTrue(energy(e) >= energy(last))
            last = e
        }
        // Pulse counts: 1 tick, 2 pulses, 3 rising pulses.
        fun pulses(e: HapticEvent) = waveOf(e).amplitudes.filter { it > 0 }
        assertEquals(1, pulses(HapticEvent.StackPerfect1).size)
        assertEquals(2, pulses(HapticEvent.StackPerfect2).size)
        assertEquals(3, pulses(HapticEvent.StackPerfect3).size)
        assertEquals(pulses(HapticEvent.StackPerfect3).sorted(), pulses(HapticEvent.StackPerfect3))
        assertEquals(pulses(HapticEvent.StackPerfect4).take(5).sorted(), pulses(HapticEvent.StackPerfect4).take(5))
    }

    @Test
    fun `stack milestone rings longer than the chord and stays a single graceful pattern`() {
        assertTrue(waveOf(HapticEvent.StackMilestone).durationMs > waveOf(HapticEvent.StackPerfect4).durationMs)
        assertTrue(waveOf(HapticEvent.StackMilestone).durationMs <= 400)
        assertEquals(255, peak(HapticEvent.StackMilestone))
        for (s in listOf(3, 6, 9, 12)) assertEquals(HapticEvent.StackMilestone, stackPerfectOrMilestoneHaptic(s))
        for (s in listOf(1, 2, 4, 5, 7, 8, 10, 11, 13)) assertEquals(stackPerfectHaptic(s), stackPerfectOrMilestoneHaptic(s))
    }

    @Test
    fun `stack trims scale with the fraction`() {
        assertEquals(HapticEvent.StackLand, stackTrimHaptic(0f))
        assertEquals(HapticEvent.StackTrim, stackTrimHaptic(0.10f))
        assertEquals(HapticEvent.StackTrimMedium, stackTrimHaptic(0.30f))
        assertEquals(HapticEvent.StackTrimHeavy, stackTrimHaptic(0.55f))
        var last = 0L
        for (i in 0..100) {
            val e = energy(stackTrimHaptic(i / 100f))
            assertTrue(e >= last)
            last = e
        }
        assertTrue(peak(HapticEvent.StackTrimHeavy) >= 200)
        assertTrue(peak(HapticEvent.StackLand) <= 60)
    }

    @Test
    fun `stack land is the softest thud and wind the gentlest rumble`() {
        assertEquals(1, HapticEvent.StackLand.priority)
        assertTrue(waveOf(HapticEvent.StackLand).durationMs <= 12)
        assertTrue(peak(HapticEvent.StackWind) <= 80)
        assertEquals(1, HapticEvent.StackWind.priority)
        assertEquals(350L, HapticEvent.StackWind.minRepeatMs)
        // Miss is heavy and short, and game over outranks it so it can cut through.
        assertEquals(255, peak(HapticEvent.StackMiss))
        assertTrue(waveOf(HapticEvent.StackMiss).durationMs <= 80)
        assertTrue(HapticEvent.GameOver.priority > HapticEvent.StackMiss.priority)
        // Net and power-up are double pulses.
        assertEquals(2, waveOf(HapticEvent.StackNet).amplitudes.count { it > 0 })
        assertEquals(2, waveOf(HapticEvent.StackPowerUp).amplitudes.count { it > 0 })
        assertTrue(hapticPlanFor(HapticEvent.StackUse) is HapticPlan.Predefined)
    }

    @Test
    fun `wind rumble repeats at most every 350 ms and never cuts through anything stronger`() {
        val policy = HapticPolicy()
        assertNotNull(policy.plan(HapticEvent.StackWind, 1_000L))
        assertNull(policy.plan(HapticEvent.StackWind, 1_200L)) // repeat limit
        assertNull(policy.plan(HapticEvent.StackWind, 1_349L))
        assertNotNull(policy.plan(HapticEvent.StackWind, 1_350L))
        // A frame-rate flood of pulses fires at most once per 350 ms.
        val flood = HapticPolicy()
        var fired = 0
        for (t in 0L until 3_500L step 16L) if (flood.plan(HapticEvent.StackWind, t) != null) fired++
        assertTrue("fired $fired", fired <= 3_500 / 350 + 1)
        // A ringing milestone keeps the (weaker) wind quiet until it is over.
        val p2 = HapticPolicy()
        assertNotNull(p2.plan(HapticEvent.StackMilestone, 5_000L))
        assertNull(p2.plan(HapticEvent.StackWind, 5_010L))
    }

    @Test
    fun `game over cuts through the miss thud and stack events respect pause and the setting`() {
        val policy = HapticPolicy()
        assertNotNull(policy.plan(HapticEvent.StackMiss, 2_000L))
        assertNotNull(policy.plan(HapticEvent.GameOver, 2_020L))
        val off = HapticPolicy().apply { enabled = false }
        val paused = HapticPolicy().apply { suspended = true }
        for (e in HapticEvent.entries.filter { it.name.startsWith("Stack") }) {
            assertNull(off.plan(e, 1L)); assertNull(paused.plan(e, 1L))
        }
    }

    @Test
    fun `legacy patterns collapse stepped ramps into strict off-on pairs and keep the duration`() {
        // The rising chord degrades to one buzz, a gap, and the closing tap.
        assertEquals(listOf(0L, 42L, 40L, 18L), waveOf(HapticEvent.StackPerfect4).toLegacyPattern().toList())
        for (event in HapticEvent.entries) {
            val w = waveOf(event)
            val legacy = w.toLegacyPattern()
            assertEquals("$event duration", w.durationMs, legacy.sum())
            assertTrue("$event size", legacy.size >= 2)
            // Index 0 is the delay, odd indices are the on segments: all must be positive.
            for (i in 1 until legacy.size step 2) assertTrue("$event on[$i]", legacy[i] > 0)
        }
        // Existing alternating patterns are untouched.
        val impact = waveOf(HapticEvent.Impact)
        assertEquals(impact.timings, impact.toLegacyPattern().toList())
    }

    @Test
    fun `button presses give at most one event and silence during running play`() {
        // Running round: D-pad / A / B are silent, the game event carries feedback.
        for (b in listOf(ConsoleButton.Up, ConsoleButton.Down, ConsoleButton.Left, ConsoleButton.Right, ConsoleButton.A, ConsoleButton.B)) {
            assertNull("$b during play", buttonHaptic(ShellMode.GameRunning, b))
        }
        assertEquals(HapticEvent.Tick, buttonHaptic(ShellMode.GameRunning, ConsoleButton.Start))
        // Menu: navigation selects, confirm is stronger, dead buttons are silent.
        assertEquals(HapticEvent.Select, buttonHaptic(ShellMode.Menu, ConsoleButton.Down))
        assertEquals(HapticEvent.Select, buttonHaptic(ShellMode.Menu, ConsoleButton.Up))
        assertEquals(HapticEvent.Medium, buttonHaptic(ShellMode.Menu, ConsoleButton.A))
        assertNull(buttonHaptic(ShellMode.Menu, ConsoleButton.B))
        assertNull(buttonHaptic(ShellMode.Menu, ConsoleButton.Left))
        // Power is stronger than a generic tick everywhere it is live, and silent when off
        // (waking the console has its own haptic).
        assertEquals(HapticEvent.Medium, buttonHaptic(ShellMode.Menu, ConsoleButton.Power))
        assertEquals(HapticEvent.Medium, buttonHaptic(ShellMode.GameIdle, ConsoleButton.Power))
        for (b in ConsoleButton.entries) assertNull(buttonHaptic(ShellMode.Off, b))
    }
}
