package tim.core.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import tim.core.game.Levels
import tim.core.game.PartType
import tim.core.physics.Vec2
import tim.desktop.Java2DPainter
import tim.desktop.Snap
import kotlin.math.abs

/** Behaviour of the play screen's overlays and tray gestures. */
class UiFixesTest {
    private fun newGame(): Game = Game(MemoryStorage()).also { it.resize(1280, 800); it.update(0.0) }
    private fun press(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.DOWN, x, y)); g.update(0.02) }
    private fun move(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.MOVE, x, y)); g.update(0.02) }
    private fun release(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.UP, x, y)); g.update(0.02) }
    private fun tap(g: Game, x: Double, y: Double) { press(g, x, y); release(g, x, y) }
    private fun shot(g: Game, name: String) { val p = Java2DPainter.create(g.width.toInt(), g.height.toInt()); g.render(p); Snap.save(p, name) }

    /** Presses a tray tile and pulls it straight to the field along [path] (screen points). */
    private fun dragTile(g: Game, ps: PlayScreen, type: PartType, vararg path: Vec2) {
        val tile = ps.visibleTiles().first { it.type == type }
        press(g, tile.rect.center.x, tile.rect.center.y)
        for (pt in path) move(g, pt.x, pt.y)
        release(g, path.last().x, path.last().y)
    }

    @Test
    fun `clear dialog closes when tapped outside and keeps the parts`() {
        val g = newGame()
        g.startLevel(0); g.update(0.02)
        val ps = g.screen as PlayScreen
        val target = ps.layout.toScreen(Vec2(200.0, 200.0))
        dragTile(g, ps, ps.level.tray[0].type, Vec2(target.x - 200, target.y), target)
        assertEquals(1, ps.board.playerParts.size)
        // the broom button is the third one on the top bar
        val broom = ps.topBarButtonRect("clear")
        tap(g, broom.center.x, broom.center.y)
        assertTrue(ps.clearDialogShowing)
        shot(g, "screen-play-clear-dialog")
        // tapping the darkened field outside the panel just closes the dialog
        tap(g, 40.0, g.height - 40.0)
        assertFalse(ps.clearDialogShowing)
        assertEquals(1, ps.board.playerParts.size, "closing the dialog must not clear anything")
        // "No, keep" also keeps everything
        tap(g, broom.center.x, broom.center.y)
        val (yes, no) = ps.clearDialogButtons()
        tap(g, no.center.x, no.center.y)
        assertFalse(ps.clearDialogShowing)
        assertEquals(1, ps.board.playerParts.size)
        // and "Yes, clear" clears
        tap(g, broom.center.x, broom.center.y)
        tap(g, yes.center.x, yes.center.y)
        assertFalse(ps.clearDialogShowing)
        assertEquals(0, ps.board.playerParts.size)
    }

    @Test
    fun `a straight pull from a tray tile starts a drag`() {
        val g = newGame()
        g.startLevel(0); g.update(0.02)
        val ps = g.screen as PlayScreen
        val type = ps.level.tray[0].type
        val target = ps.layout.toScreen(Vec2(200.0, 200.0))
        val tile = ps.visibleTiles().first { it.type == type }
        // diagonal pull downwards and left, the way a finger naturally leaves the tray
        press(g, tile.rect.center.x, tile.rect.center.y)
        move(g, tile.rect.center.x - 12, tile.rect.center.y + 18)
        assertTrue(ps.dragging, "a small pull in any direction should pick the part up")
        move(g, target.x, target.y)
        release(g, target.x, target.y)
        assertEquals(1, ps.board.playerParts.size)
        assertEquals(type, ps.board.playerParts[0].type)
    }

    @Test
    fun `a part picked up from the board stays where it was grabbed and then glides up`() {
        val g = newGame()
        g.startLevel(0); g.update(0.02)
        val ps = g.screen as PlayScreen
        val type = ps.level.tray[0].type
        val target = ps.layout.toScreen(Vec2(300.0, 200.0))
        dragTile(g, ps, type, Vec2(target.x - 200, target.y), target)
        val placed = ps.board.playerParts[0]
        // grab it near its bottom-right corner
        val grabWorld = Vec2(placed.x + placed.w - 6, placed.y + placed.h - 6)
        val grab = ps.layout.toScreen(grabWorld)
        press(g, grab.x, grab.y)
        g.touch(TouchEvent(TouchAction.MOVE, grab.x + 3, grab.y + 3)); g.update(0.001)
        val start = ps.dragPosition() ?: error("should be dragging")
        assertTrue(abs(start.x - placed.x) <= 4.0 && abs(start.y - placed.y) <= 4.0, "no jump at pickup: $start vs ${placed.x},${placed.y}")
        // holding still, the part glides to the lifted position above the finger
        repeat(30) { g.update(0.02) }
        val later = ps.dragPosition()!!
        val liftedCentre = ps.layout.toWorld(grab.x + 3, grab.y + 3 - 48 * g.u)
        assertTrue(abs(later.x + placed.w / 2 - liftedCentre.x) <= 4.0 && abs(later.y + placed.h / 2 - liftedCentre.y) <= 4.0, "should end centred above the finger: $later")
        release(g, grab.x + 3, grab.y + 3)
    }

    @Test
    fun `tapping empty space around a part many times never deletes it`() {
        val g = newGame()
        g.startFreeform(fresh = true); g.update(0.02)
        val ps = g.screen as PlayScreen
        val type = ps.visibleTiles().first().type
        val target = ps.layout.toScreen(Vec2(300.0, 260.0))
        dragTile(g, ps, type, Vec2(target.x - 200, target.y), target)
        assertEquals(1, ps.board.playerParts.size)
        val part = ps.board.playerParts[0]
        // taps all around the part, on empty field, in quick succession (a child poking the screen)
        val spots = listOf(Vec2(part.x + part.w / 2, part.y - 6.0), Vec2(part.x - 6.0, part.y + part.h / 2), Vec2(part.x + part.w + 6.0, part.y - 20.0),
            Vec2(part.x + part.w / 2, part.y - 40.0), Vec2(part.x - 30.0, part.y - 30.0), Vec2(part.x + part.w + 30.0, part.y - 30.0))
        val painter = Java2DPainter.create(g.width.toInt(), g.height.toInt())
        repeat(6) { round ->
            for (wp in spots) {
                val sp = ps.layout.toScreen(wp)
                g.render(painter)   // like the real game: the selection buttons get placed by rendering
                press(g, sp.x, sp.y); g.render(painter); release(g, sp.x, sp.y)
                g.update(0.05); g.render(painter)
                assertEquals(1, ps.board.playerParts.size, "part vanished after tapping $wp in round $round")
            }
        }
    }

    @Test
    fun `in free play a vertical pull scrolls the tray and a sideways pull drags`() {
        val g = newGame()
        g.startFreeform(); g.update(0.02)
        val ps = g.screen as PlayScreen
        val tile = ps.visibleTiles().first()
        press(g, tile.rect.center.x, tile.rect.center.y)
        move(g, tile.rect.center.x + 2, tile.rect.center.y + 40)
        move(g, tile.rect.center.x + 2, tile.rect.center.y + 120)
        assertFalse(ps.dragging, "a vertical pull on a scrolling tray scrolls it")
        release(g, tile.rect.center.x + 2, tile.rect.center.y + 120)
        assertEquals(0, ps.board.playerParts.size)
        val tile2 = ps.visibleTiles().first()
        press(g, tile2.rect.center.x, tile2.rect.center.y)
        move(g, tile2.rect.center.x - 30, tile2.rect.center.y + 6)
        assertTrue(ps.dragging, "a sideways pull drags the part out")
        release(g, tile2.rect.center.x - 30, tile2.rect.center.y + 6)
    }

    @Test
    fun `tying instructions show in the banner and never cover the field`() {
        val g = newGame()
        val i = Levels.all.indexOfFirst { it.id == "l40" }
        g.startLevel(i); g.update(0.02)
        val ps = g.screen as PlayScreen
        val tile = ps.visibleTiles().first { it.type == PartType.ROPE }
        tap(g, tile.rect.center.x, tile.rect.center.y)
        assertTrue(ps.linking)
        shot(g, "screen-play-linking")
        // the pulleys sit at the very top of the field: they must be tappable while the message shows
        val pulley = ps.board.all.indexOfFirst { it.type == PartType.PULLEY }
        val seesaw = ps.board.all.indexOfFirst { it.type == PartType.SEESAW }
        val c1 = ps.layout.toScreen(ps.board.all[seesaw].aabb.center)
        tap(g, c1.x, c1.y)
        val c2 = ps.layout.toScreen(ps.board.all[pulley].aabb.center)
        tap(g, c2.x, c2.y)
        assertTrue(ps.linking, "still tying after the pulley")
        shot(g, "screen-play-linking-pulley")
    }

    @Test
    fun `the title screen offers Install only when the platform can install`() {
        val g = newGame()
        val title = g.screen as TitleScreen
        assertNull(title.installButtonRect(), "no install button without a platform hook")
        var installs = 0
        g.installAction = { installs++ }
        g.installAttention = true
        g.update(0.02)
        val r = title.installButtonRect() ?: error("install button should appear once the hook is set")
        shot(g, "screen-title-install")
        tap(g, r.center.x, r.center.y)
        assertEquals(1, installs, "tapping Install calls the platform")
        g.installAction = null
        g.update(0.02)
        assertNull(title.installButtonRect(), "button goes away once installed")
    }

    @Test
    fun `level list with every puzzle solved`() {
        val g = Game(MemoryStorage()).also { it.resize(1280, 2560); it.update(0.0) }
        for (l in Levels.all) g.progress.setStars(l.id, 3)
        g.toLevelSelect(); g.update(0.02)
        shot(g, "screen-levels-solved")
    }

    @Test
    fun `a screen resize while celebrating keeps the next button`() {
        val g = newGame()
        g.startLevel(0); g.update(0.02)
        val ps = g.screen as PlayScreen
        ps.board.playerParts.addAll(ps.level.solution)
        val play = ps.playButtonRect()
        tap(g, play.center.x, play.center.y)
        var t = 0.0
        while (!ps.won && t < 20) { g.update(1.0 / 60); t += 1.0 / 60 }
        assertTrue(ps.won)
        g.resize(1600, 1000); g.update(0.02)
        val (again, next) = ps.winButtons()
        tap(g, next.center.x, next.center.y)
        g.update(0.02)
        assertTrue(g.screen is PlayScreen && (g.screen as PlayScreen).levelIndex == 1, "Next should still work after a resize")
        assertTrue(again.width > 0)
    }

    @TestFactory
    fun `Next after winning goes to the following level`(): List<DynamicTest> = Levels.all.mapIndexed { i, level ->
        DynamicTest.dynamicTest("${level.id} ${level.title}") {
            val g = newGame()
            g.startLevel(i); g.update(0.02)
            val ps = g.screen as PlayScreen
            ps.board.playerParts.addAll(level.solution)
            ps.board.playerLinks.addAll(level.solutionLinks)
            val play = ps.playButtonRect()
            tap(g, play.center.x, play.center.y)
            assertTrue(ps.running)
            var t = 0.0
            while (!ps.won && t < level.timeLimit + 2) { g.update(1.0 / 60); t += 1.0 / 60 }
            assertTrue(ps.won, "${level.id} should be solved by its solution")
            assertTrue(g.progress.solved(level.id))
            val (_, next) = ps.winButtons()
            tap(g, next.center.x, next.center.y)
            g.update(0.02)
            if (i + 1 < Levels.all.size) {
                val s = g.screen
                assertTrue(s is PlayScreen && s.levelIndex == i + 1, "${level.id}: Next should open level ${i + 2}, got $s")
                // the new level starts fresh and is playable
                assertFalse((s as PlayScreen).running)
                assertEquals(0, s.board.playerParts.size)
                g.update(0.02)
                val p = Java2DPainter.create(g.width.toInt(), g.height.toInt()); g.render(p)
            } else {
                assertTrue(g.screen is LevelSelectScreen, "after the last level Next goes to the level list")
            }
        }
    }
}
