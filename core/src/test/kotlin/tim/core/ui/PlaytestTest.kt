package tim.core.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tim.core.game.Levels
import tim.core.game.Placement
import tim.core.physics.Vec2
import tim.desktop.Java2DPainter
import tim.desktop.Snap

/**
 * Plays every level the way a child would: drag each solution part out of the tray to its spot,
 * flip or rotate it with the on-screen buttons, press Play, and wait for the celebration.
 */
class PlaytestTest {
    private fun newGame(): Game = Game(MemoryStorage()).also { it.resize(1280, 800); it.update(0.0) }

    private fun press(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.DOWN, x, y)); g.update(0.02) }
    private fun move(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.MOVE, x, y)); g.update(0.02) }
    private fun release(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.UP, x, y)); g.update(0.02) }
    private fun tap(g: Game, x: Double, y: Double) { press(g, x, y); release(g, x, y) }

    private fun shot(g: Game, name: String) {
        val p = Java2DPainter.create(g.width.toInt(), g.height.toInt())
        g.render(p)
        Snap.save(p, name)
    }

    /** Drag a part of the right type from the tray so that it lands on [target]. */
    private fun dragFromTray(g: Game, ps: PlayScreen, target: Placement) {
        val tile = ps.visibleTiles().firstOrNull { it.type == target.type } ?: error("no tray tile for ${target.type}")
        val lift = 48.0 * g.u
        val centre = ps.layout.toScreen(Vec2(target.x + target.w / 2, target.y + target.h / 2))
        press(g, tile.rect.center.x, tile.rect.center.y)
        move(g, tile.rect.center.x - 40, tile.rect.center.y)
        move(g, tile.rect.minX - 80, tile.rect.center.y)
        move(g, centre.x, centre.y + lift)
        release(g, centre.x, centre.y + lift)
    }

    @TestFactory
    fun `every level can be solved through the touch interface`(): List<DynamicTest> = Levels.all.mapIndexed { i, level ->
        DynamicTest.dynamicTest("${level.id} ${level.title}") {
            val g = newGame()
            g.startLevel(i); g.update(0.02)
            val ps = g.screen as PlayScreen
            for (sol in level.solution) {
                dragFromTray(g, ps, sol)
                val idx = ps.board.playerParts.size - 1
                assertTrue(idx >= 0, "${level.id}: ${sol.type} was not placed")
                var placed = ps.board.playerParts[idx]
                assertEquals(sol.type, placed.type)
                // flip / rotate with the floating buttons next to the selected part
                repeat(sol.rotation) {
                    g.update(0.02)
                    val b = ps.actionButton("rotate") ?: error("${level.id}: no rotate button for ${sol.type}")
                    tap(g, b.center.x, b.center.y)
                }
                if (sol.flipped) {
                    g.update(0.02)
                    val b = ps.actionButton("flip") ?: error("${level.id}: no flip button for ${sol.type}")
                    tap(g, b.center.x, b.center.y)
                }
                placed = ps.board.playerParts[idx]
                assertEquals(sol.x, placed.x, 0.0, "${level.id}: ${sol.type} x")
                assertEquals(sol.y, placed.y, 0.0, "${level.id}: ${sol.type} y")
                assertEquals(sol.flipped, placed.flipped, "${level.id}: ${sol.type} flipped")
                assertEquals(sol.rotation, placed.rotation, "${level.id}: ${sol.type} rotation")
            }
            shot(g, "playtest-${level.id}-built")
            // press the big play button
            val play = ps.playButtonRect()
            tap(g, play.center.x, play.center.y)
            assertTrue(ps.running, "${level.id}: machine should run")
            var t = 0.0
            while (!ps.won && t < level.timeLimit + 2) { g.update(1.0 / 60); t += 1.0 / 60 }
            assertTrue(ps.won, "${level.id}: not solved through the UI within ${level.timeLimit}s")
            shot(g, "playtest-${level.id}-won")
            assertTrue(g.progress.solved(level.id))
        }
    }
}
