package com.jasmeet.pixeldrop.ui.screens

import com.jasmeet.pixeldrop.game.GAMES
import com.jasmeet.pixeldrop.game.GameId
import com.jasmeet.pixeldrop.game.isPlayable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuDemoFramesTest {
    private class Recorder : DemoSink {
        val rects = ArrayList<String>()
        var outOfBounds = 0
        var badTone = 0
        override fun rect(x: Float, y: Float, w: Float, h: Float, tone: Int) {
            if (x < 0f || y < 0f || w <= 0f || h <= 0f || x + w > DEMO_COLS + 1e-4f || y + h > DEMO_ROWS + 1e-4f) outOfBounds++
            if (tone < TONE_FULL || tone > TONE_PAPER) badTone++
            rects += "$x,$y,$w,$h,$tone"
        }
    }

    private fun frame(game: GameId, t: Float): Set<String> {
        val r = Recorder()
        renderDemo(game, t, r)
        return r.rects.toSet()
    }

    private fun diff(a: Set<String>, b: Set<String>) = (a - b).size + (b - a).size

    private val samples = 72

    /** Teaser slots draw a text sprite, not a cell demo. */
    private val playable = GameId.entries.filter { it.isPlayable }

    @Test fun `every game in the menu has a demo that draws something`() {
        assertEquals(GameId.entries.toSet(), GAMES.map { it.id }.toSet())
        for (game in playable) {
            val r = Recorder()
            renderDemo(game, DEMO_STILL_T, r)
            assertTrue("$game drew nothing", r.rects.isNotEmpty())
        }
    }

    @Test fun `demos stay inside the preview field with valid tones`() {
        for (game in playable) for (i in 0 until samples) {
            val r = Recorder()
            renderDemo(game, i / samples.toFloat(), r)
            assertEquals("$game frame $i out of bounds", 0, r.outOfBounds)
            assertEquals("$game frame $i bad tone", 0, r.badTone)
        }
    }

    @Test fun `demos are deterministic`() {
        for (game in playable) for (i in 0 until samples step 7) {
            val t = i / samples.toFloat()
            assertEquals(frame(game, t), frame(game, t))
        }
    }

    @Test fun `every demo actually animates across the loop`() {
        for (game in playable) {
            val distinct = (0 until samples).map { frame(game, it / samples.toFloat()) }.toSet()
            assertTrue("$game has only ${distinct.size} distinct frames", distinct.size >= 12)
        }
    }

    /** Seam may exceed the worst in-loop step by one dropped tail cell plus a respawned dot (PixelTrail). */
    private val seamSlack = 2

    @Test fun `loop seam is no more abrupt than any step inside the loop`() {
        for (game in playable) {
            val frames = (0 until samples).map { frame(game, it / samples.toFloat()) }
            var worstInside = 0
            for (i in 0 until samples - 1) worstInside = maxOf(worstInside, diff(frames[i], frames[i + 1]))
            val seam = diff(frames[samples - 1], frames[0])
            assertTrue("$game seam $seam vs worst in-loop step $worstInside", seam <= worstInside + seamSlack)
        }
    }

    @Test fun `sub-loop start and end frames are close for the stateful demos`() {
        // The demos that rebuild their scene must land back on (nearly) the opening frame.
        val stateful = listOf(GameId.PixelDrop, GameId.BrickPulse, GameId.OrbitBlaster)
        for (game in stateful) {
            val end = frame(game, 0.999f)
            val start = frame(game, 0f)
            assertTrue("$game seam too visible: ${diff(end, start)}", diff(end, start) <= 24)
        }
    }
}
