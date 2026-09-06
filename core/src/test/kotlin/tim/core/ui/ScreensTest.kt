package tim.core.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.Levels
import tim.core.physics.Vec2
import tim.desktop.Java2DPainter
import tim.desktop.Snap

/** Drives the whole game through its platform-independent API and screenshots each screen. */
class ScreensTest {
    private fun newGame(): Game = Game(MemoryStorage()).also { it.resize(1280, 800); it.update(0.0) }

    private fun shot(g: Game, name: String): Java2DPainter {
        val p = Java2DPainter.create(g.width.toInt(), g.height.toInt())
        g.render(p)
        Snap.save(p, name)
        return p
    }

    private fun tap(g: Game, x: Double, y: Double) {
        g.touch(TouchEvent(TouchAction.DOWN, x, y)); g.update(0.016)
        g.touch(TouchEvent(TouchAction.UP, x, y)); g.update(0.016)
    }

    @Test
    fun `title screen renders and leads to level select and play`() {
        val g = newGame()
        assertTrue(g.screen is TitleScreen)
        g.update(0.5)
        shot(g, "screen-title")
        tap(g, g.width / 2, g.height * 0.56 + 55)
        assertTrue(g.screen is LevelSelectScreen, "expected level select, got ${g.screen}")
        shot(g, "screen-levels")
        tap(g, 200.0, 190.0)
        assertTrue(g.screen is PlayScreen, "expected play screen, got ${g.screen}")
        val ps = g.screen as PlayScreen
        assertEquals(Levels.all[0].id, ps.level.id)
        shot(g, "screen-play-empty")
    }

    @Test
    fun `dragging a part from the tray places it and play runs the machine`() {
        val g = newGame()
        g.startLevel(0); g.update(0.016)
        val ps = g.screen as PlayScreen
        val tray = ps.layout.tray
        val tileY = tray.minY + 60
        val tileX = tray.center.x
        val target = ps.layout.toScreen(Vec2(72.0, 184.0))
        g.touch(TouchEvent(TouchAction.DOWN, tileX, tileY)); g.update(0.016)
        g.touch(TouchEvent(TouchAction.MOVE, tileX - 60, tileY)); g.update(0.016)
        g.touch(TouchEvent(TouchAction.MOVE, target.x, target.y + 48)); g.update(0.016)
        shot(g, "screen-play-dragging")
        g.touch(TouchEvent(TouchAction.UP, target.x, target.y + 48)); g.update(0.016)
        assertEquals(1, ps.board.playerParts.size, "part should have been placed")
        val placed = ps.board.playerParts[0]
        println("placed at ${placed.x},${placed.y}")
        shot(g, "screen-play-placed")
        tap(g, tray.center.x, g.height - 70.0)
        assertTrue(ps.running, "machine should be running")
        repeat(30) { g.update(0.05) }
        shot(g, "screen-play-running")
    }

    @Test
    fun `solving a level shows the celebration`() {
        val g = newGame()
        g.startLevel(0); g.update(0.016)
        val ps = g.screen as PlayScreen
        for (pl in Levels.all[0].solution) ps.board.playerParts.add(pl)
        ps.startRun()
        var frames = 0
        while (!ps.won && frames < 600) { g.update(1.0 / 60); frames++ }
        assertTrue(ps.won, "level 1 should be won with its solution")
        repeat(20) { g.update(1.0 / 30) }
        shot(g, "screen-play-won")
        assertEquals(3, g.progress.stars(Levels.all[0].id))
    }
}
