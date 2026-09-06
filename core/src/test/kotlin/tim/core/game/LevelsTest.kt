package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tim.core.physics.World
import tim.desktop.Snap

/** Every level's stored solution must solve it, and the untouched board must not. */
class LevelsTest {
    companion object {
        /** Runs the solution and returns the time to solve, or -1. Dumps frames on failure for debugging. */
        fun solveTime(level: Level, dumpOnFail: Boolean = true): Double {
            val m = Machine(level.solvedBoard(), gravity = level.gravity, airPressure = level.airPressure)
            val steps = (level.timeLimit / World.STEP).toInt()
            for (i in 0 until steps) {
                if (level.goal.check(m)) return m.time
                m.step()
            }
            if (dumpOnFail) { dumpFrames(level, level.solvedBoard(), "fail-${level.id}"); println("TRACE ${level.id}\n" + LevelLab.trace(level)) }
            return -1.0
        }

        fun dumpFrames(level: Level, board: Board, name: String, every: Double = 0.5, seconds: Double = 8.0) {
            val m = Machine(board, gravity = level.gravity, airPressure = level.airPressure)
            var next = 0.0
            var frame = 0
            while (m.time <= seconds) {
                if (m.time >= next) { Snap.save(Snap.paintMachine(m, 1.5, m.time), "$name-${frame.toString().padStart(2, '0')}"); next += every; frame++ }
                m.step()
            }
        }
    }

    @TestFactory
    fun `stored solutions solve their levels`(): List<DynamicTest> = Levels.all.map { lvl ->
        DynamicTest.dynamicTest("${lvl.id} ${lvl.title}") {
            val t = solveTime(lvl)
            assertTrue(t >= 0, "solution for ${lvl.id} did not reach the goal within ${lvl.timeLimit}s (frames dumped)")
            println("${lvl.id} solved in %.2fs".format(t))
        }
    }

    @TestFactory
    fun `empty boards do not solve themselves`(): List<DynamicTest> = Levels.all.map { lvl ->
        DynamicTest.dynamicTest("${lvl.id} ${lvl.title}") {
            val m = Machine(lvl.newBoard(), gravity = lvl.gravity, airPressure = lvl.airPressure)
            repeat((lvl.timeLimit / World.STEP).toInt()) { assertFalse(lvl.goal.check(m), "${lvl.id} solved itself at ${m.time}"); m.step() }
        }
    }

    @TestFactory
    fun `solutions only use tray parts and fit the board`(): List<DynamicTest> = Levels.all.map { lvl ->
        DynamicTest.dynamicTest("${lvl.id} ${lvl.title}") {
            val counts = lvl.tray.groupBy { it.type }.mapValues { e -> e.value.sumOf { it.count } }
            val used = lvl.solution.groupingBy { it.type }.eachCount()
            for ((t, n) in used) assertTrue((counts[t] ?: 0) >= n, "${lvl.id}: solution uses $n x $t but tray has ${counts[t] ?: 0}")
            // fixed parts may overlap each other (a cage over a mouse); player parts may not overlap anything
            val all = lvl.fixed + lvl.solution
            for (i in lvl.fixed.size until all.size) for (j in all.indices) if (i != j) assertFalse(all[i].overlaps(all[j]), "${lvl.id}: ${all[i]} overlaps ${all[j]}")
            for (p in lvl.solution) assertTrue(p.x >= 0 && p.y >= 0 && p.x + p.w <= Machine.WIDTH && p.y + p.h <= Machine.HEIGHT, "${lvl.id}: $p outside the field")
            for (p in all) assertTrue(p.x % Machine.GRID == 0.0 && p.y % Machine.GRID == 0.0, "${lvl.id}: $p not on the grid")
            val tools = lvl.solutionLinks.groupingBy { tim.core.game.LinkRules.toolFor(it.kind) }.eachCount()
            for ((t, n) in tools) assertTrue((counts[t] ?: 0) >= n, "${lvl.id}: solution ties $n x $t but tray has ${counts[t] ?: 0}")
            for (l in lvl.solutionLinks) {
                assertTrue(l.from in all.indices && l.to in all.indices && l.via.all { it in all.indices }, "${lvl.id}: link $l points outside the part list")
                assertNotNull(tim.core.game.LinkRules.connect(l.kind, l.from, all[l.from].type, l.to, all[l.to].type, l.via), "${lvl.id}: link $l joins incompatible parts")
            }
        }
    }
}
