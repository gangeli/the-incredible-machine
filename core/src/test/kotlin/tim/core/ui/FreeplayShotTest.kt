package tim.core.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.PartType
import tim.core.game.Placement
import tim.desktop.Java2DPainter
import tim.desktop.Snap

class FreeplayShotTest {
    @Test
    fun `free play offers every part, never declares a win, and runs until stopped`() {
        val g = Game(MemoryStorage()).also { it.resize(1280, 800); it.update(0.0) }
        g.startFreeform(); g.update(0.02)
        val ps = g.screen as PlayScreen
        assertTrue(ps.isFreeform)
        assertEquals(PartType.values().size, ps.level.tray.size)
        for (t in PartType.values()) assertTrue(ps.remaining(t) > 50, "$t should be unlimited")
        // build a little machine directly and run it
        ps.board.playerParts.add(Placement(PartType.INCLINE, 96.0, 320.0))
        ps.board.playerParts.add(Placement(PartType.BOWLING_BALL, 100.0, 260.0))
        ps.board.playerParts.add(Placement(PartType.SEESAW, 240.0, 352.0))
        ps.board.playerParts.add(Placement(PartType.BASEBALL, 248.0, 320.0))
        ps.board.playerParts.add(Placement(PartType.BALLOON, 400.0, 300.0))
        val p0 = Java2DPainter.create(1280, 800); g.render(p0); Snap.save(p0, "screen-freeplay-build")
        ps.startRun()
        repeat(60 * 40) { g.update(1.0 / 60) }
        assertTrue(ps.running, "free play keeps running after 40 s (no time limit, no fail screen)")
        assertFalse(ps.won)
        val p1 = Java2DPainter.create(1280, 800); g.render(p1); Snap.save(p1, "screen-freeplay-running")
        // editing is disabled while running; stop, then the category tabs and scrolling reach every part
        ps.stopRun(); g.update(0.02)
        val tabs = ps.tabRects()
        assertTrue(tabs.size >= 6, "free play should show category tabs, got ${tabs.size}")
        val goalTab = tabs.first { it.first == tim.core.game.PartCategory.GOAL }.second
        g.touch(TouchEvent(TouchAction.DOWN, goalTab.center.x, goalTab.center.y)); g.update(0.02)
        g.touch(TouchEvent(TouchAction.UP, goalTab.center.x, goalTab.center.y)); g.update(0.02)
        assertTrue(ps.visibleTiles().any { it.type == PartType.STAR }, "goal tab should show the star")
        val linkTab = tabs.first { it.first == tim.core.game.PartCategory.LINK }.second
        g.touch(TouchEvent(TouchAction.DOWN, linkTab.center.x, linkTab.center.y)); g.update(0.02)
        g.touch(TouchEvent(TouchAction.UP, linkTab.center.x, linkTab.center.y)); g.update(0.02)
        assertTrue(ps.visibleTiles().map { it.type }.containsAll(listOf(PartType.ROPE, PartType.BELT, PartType.WIRE)), "link tab should show the rope, belt and wire tools")
        val machineTab = tabs.first { it.first == tim.core.game.PartCategory.MACHINE }.second
        g.touch(TouchEvent(TouchAction.DOWN, machineTab.center.x, machineTab.center.y)); g.update(0.02)
        g.touch(TouchEvent(TouchAction.UP, machineTab.center.x, machineTab.center.y)); g.update(0.02)
        val p2 = Java2DPainter.create(1280, 800); g.render(p2); Snap.save(p2, "screen-freeplay-tabs")
        val tray = ps.layout.tray
        g.touch(TouchEvent(TouchAction.DOWN, tray.center.x, tray.minY + 300)); g.update(0.02)
        g.touch(TouchEvent(TouchAction.MOVE, tray.center.x, tray.minY + 100)); g.update(0.02)
        g.touch(TouchEvent(TouchAction.MOVE, tray.center.x, tray.minY - 9000)); g.update(0.02)
        g.touch(TouchEvent(TouchAction.UP, tray.center.x, tray.minY - 9000)); g.update(0.02)
        assertTrue(ps.visibleTiles().any { it.type == PartType.MOTOR }, "machine tab should scroll down to the motor")
    }
}
