package tim.core.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tim.core.game.Levels
import tim.core.game.Machine

/**
 * Monkey-tests the play screen: thousands of random taps, drags and button presses (including
 * pressing Play mid-drag and dragging while the machine runs) must never throw or leave the board
 * in an impossible state.
 */
class FuzzTest {
    private fun invariants(ps: PlayScreen, tag: String) {
        val parts = ps.board.playerParts
        for (i in parts.indices) {
            val a = parts[i]
            assertTrue(a.x >= 0 && a.y >= 0 && a.x + a.w <= Machine.WIDTH && a.y + a.h <= Machine.HEIGHT, "$tag: $a outside the field")
            assertTrue(a.x % Machine.GRID == 0.0 && a.y % Machine.GRID == 0.0, "$tag: $a off grid")
            for (f in ps.board.fixed) assertFalse(a.overlaps(f), "$tag: $a overlaps fixed $f")
            for (j in i + 1 until parts.size) assertFalse(a.overlaps(parts[j]), "$tag: $a overlaps ${parts[j]}")
        }
        for (item in ps.level.tray) assertTrue(ps.remaining(item.type) >= 0, "$tag: negative tray count for ${item.type}")
        val n = ps.board.all.size
        for (l in ps.board.playerLinks) assertTrue(l.from in 0 until n && l.to in 0 until n && l.via.all { it in 0 until n }, "$tag: dangling link $l with $n parts")
    }

    private fun fuzz(g: Game, seed: Long, events: Int, tag: String) {
        val rng = Rng(seed)
        val ps = g.screen as PlayScreen
        var down = false
        var x = 0.0; var y = 0.0
        repeat(events) { n ->
            val r = rng.next()
            when {
                !down && r < 0.5 -> { x = rng.range(0.0, g.width); y = rng.range(0.0, g.height); g.touch(TouchEvent(TouchAction.DOWN, x, y)); down = true }
                down && r < 0.75 -> { x = (x + rng.range(-160.0, 160.0)).coerceIn(-20.0, g.width + 20); y = (y + rng.range(-120.0, 120.0)).coerceIn(-20.0, g.height + 20); g.touch(TouchEvent(TouchAction.MOVE, x, y)) }
                down -> { g.touch(TouchEvent(if (rng.next() < 0.9) TouchAction.UP else TouchAction.CANCEL, x, y)); down = false }
                r < 0.55 -> { val b = ps.playButtonRect(); g.touch(TouchEvent(TouchAction.DOWN, b.center.x, b.center.y)); g.touch(TouchEvent(TouchAction.UP, b.center.x, b.center.y)) }
                r < 0.6 -> g.back()
                else -> { val tiles = ps.visibleTiles(); if (tiles.isNotEmpty()) { val t = tiles[(rng.next() * tiles.size).toInt()]; g.touch(TouchEvent(TouchAction.DOWN, t.rect.center.x, t.rect.center.y)); g.touch(TouchEvent(TouchAction.UP, t.rect.center.x, t.rect.center.y)) } }
            }
            g.update(rng.range(0.0, 0.08))
            if (g.screen === ps) invariants(ps, "$tag#$n") else return
        }
    }

    @TestFactory
    fun `random input never breaks a level`(): List<DynamicTest> = (Levels.all.indices step 3).map { i ->
        DynamicTest.dynamicTest("level ${i + 1}") {
            val g = Game(MemoryStorage()).also { it.resize(1280, 800); it.update(0.0) }
            g.startLevel(i); g.update(0.02)
            fuzz(g, 1000L + i, 1500, "level${i + 1}")
        }
    }

    @TestFactory
    fun `random input never breaks free play or a small screen`(): List<DynamicTest> = listOf(1280 to 800, 960 to 600, 2560 to 1600, 1024 to 768).map { (w, h) ->
        DynamicTest.dynamicTest("free play ${w}x$h") {
            val g = Game(MemoryStorage()).also { it.resize(w, h); it.update(0.0) }
            g.startFreeform(); g.update(0.02)
            fuzz(g, 77L + w, 2000, "free$w")
        }
    }
}
