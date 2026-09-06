package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.parts.Cheese
import tim.core.game.parts.Mouse
import tim.core.game.PartType as T

/** Placement overlap rules and goal checks that the puzzles rely on. */
class PlacementRulesTest {
    @Test
    fun `a ball may sit in the empty half of a ramp's box but not inside the wood`() {
        val ramp = Placement(T.INCLINE, 0.0, 0.0)          // solid triangle: bottom-left half
        assertFalse(Fit.overlap(ramp, Placement(T.BASEBALL, 44.0, 0.0)), "small ball in the empty top-right corner")
        assertFalse(Fit.overlap(ramp, Placement(T.BASKETBALL, 36.0, -20.0)), "big ball resting on the slope")
        assertTrue(Fit.overlap(ramp, Placement(T.BASEBALL, 0.0, 12.0)), "ball inside the wood")
        assertTrue(Fit.overlap(ramp, Placement(T.BASKETBALL, 16.0, 0.0)), "big ball across the slope")
        val flipped = Placement(T.INCLINE, 0.0, 0.0, flipped = true) // solid: bottom-right half
        assertFalse(Fit.overlap(flipped, Placement(T.BASEBALL, 4.0, 0.0)), "small ball in the empty top-left corner")
        assertTrue(Fit.overlap(flipped, Placement(T.BASEBALL, 48.0, 12.0)))
    }

    @Test
    fun `only the named ball reaching the star solves level 11`() {
        val level = Levels.all.first { it.id == "l11" }
        val star = level.fixed.indexOfFirst { it.type == T.STAR }
        // a baseball dropped straight onto the star touches it, but the goal wants the tennis ball
        val cheat = Board(level.fixed, level.fixedLinks, arrayListOf(Placement(T.BASEBALL, 600.0, 300.0)))
        val m = Machine(cheat)
        m.run(4.0)
        val starPart = m.parts[star] as Toucher
        assertTrue(starPart.touchedBy.any { it.type == T.BASEBALL }, "the baseball did land on the star")
        assertFalse(level.goal.check(m), "the wrong ball must not count")
        // the real solution does
        val real = Machine(level.solvedBoard())
        assertTrue(real.runUntil(level.timeLimit) { level.goal.check(it) })
    }

    @Test
    fun `Mort runs to cheese he can see without stopping to sniff`() {
        val m = machine {
            for (i in 0 until 7) part(T.BRICK_WALL, i * 96.0, 384.0)
            part(T.MOUSE, 40.0, 368.0)
            part(T.CHEESE, 420.0, 368.0)
        }
        val mouse = m.part<Mouse>(7)
        val cheese = m.part<Cheese>(8)
        var started = false
        var stops = 0
        var t = 0.0
        while (!cheese.eaten && t < 12.0) {
            m.step(); t += 1.0 / 60
            if (mouse.walking) started = true
            if (started && !mouse.walking && !mouse.eating) stops++
        }
        assertTrue(cheese.eaten, "Mort should reach the cheese")
        assertEquals(0, stops, "Mort paused on his way to cheese he could see")
    }
}
