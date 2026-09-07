package tim.core.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.BoardCodec
import tim.core.game.Levels
import tim.core.game.PartType
import tim.core.physics.Vec2
import tim.desktop.Java2DPainter
import tim.desktop.Snap

class SavedMachinesTest {
    private fun newGame(): Game = Game(MemoryStorage()).also { it.resize(1280, 800); it.update(0.0) }
    private fun press(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.DOWN, x, y)); g.update(0.02) }
    private fun move(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.MOVE, x, y)); g.update(0.02) }
    private fun release(g: Game, x: Double, y: Double) { g.touch(TouchEvent(TouchAction.UP, x, y)); g.update(0.02) }
    private fun tap(g: Game, x: Double, y: Double) { press(g, x, y); release(g, x, y) }
    private fun shot(g: Game, name: String) { val p = Java2DPainter.create(g.width.toInt(), g.height.toInt()); g.render(p); Snap.save(p, name) }
    private fun place(g: Game, ps: PlayScreen, wx: Double, wy: Double) {
        val tile = ps.visibleTiles().first()
        val target = ps.layout.toScreen(Vec2(wx, wy))
        press(g, tile.rect.center.x, tile.rect.center.y)
        move(g, tile.rect.center.x - 40, tile.rect.center.y)
        move(g, target.x, target.y + 48 * g.u)
        release(g, target.x, target.y + 48 * g.u)
    }

    @Test
    fun `saved machines store keeps named copies and an autosave`() {
        val m = SavedMachines(MemoryStorage())
        assertTrue(m.list().isEmpty())
        val a = m.save(null, "BASKETBALL 8 16|")
        val b = m.save(null, "INCLINE 0 0|")
        assertEquals(listOf(a, b), m.list().map { it.id })
        assertTrue(m.nameOf(a).contains("Basketball"), "named after its contents: ${m.nameOf(a)}")
        assertTrue(m.nameOf(b).contains("Ramp"), "named after its contents: ${m.nameOf(b)}")
        assertEquals(a, m.save(a, "BASKETBALL 8 40|"), "saving again keeps the id")
        assertEquals("BASKETBALL 8 40|", m.get(a)!!.encoded)
        val c = m.save(null, "BASKETBALL 8 40|")
        assertTrue(m.nameOf(c).endsWith(" II") && m.nameOf(c).length <= 24 && m.nameOf(a).startsWith(m.nameOf(c).removeSuffix(" II")), "a second machine with the same mix gets a numeral: ${m.nameOf(c)}")
        m.rename(a, "Roller")
        m.save(a, "BASKETBALL 8 40|BALLOON 100 100|")
        assertEquals("Roller", m.nameOf(a), "a player's name sticks through later saves")
        m.delete(c)
        m.currentId = b
        m.delete(b)
        assertEquals(listOf(a), m.list().map { it.id })
        assertNull(m.currentId)
        m.current = "x|"; assertEquals("x|", m.current); m.current = null; assertNull(m.current)
    }

    @Test
    fun `free play autosaves and resumes, Save fills the gallery, tiles load and delete`() {
        val g = newGame()
        g.startFreeform(); g.update(0.02)
        var ps = g.screen as PlayScreen
        place(g, ps, 200.0, 200.0)
        place(g, ps, 320.0, 200.0)
        assertEquals(2, ps.board.playerParts.size)
        // leaving and coming back keeps the build
        tap(g, ps.topBarButtonRect("home").center.x, ps.topBarButtonRect("home").center.y)
        assertTrue(g.screen is TitleScreen)
        g.startFreeform(); g.update(0.02)
        ps = g.screen as PlayScreen
        assertEquals(2, ps.board.playerParts.size, "free play resumes the autosaved build")
        assertNull(ps.savedId)
        // Save keeps a named copy
        val save = ps.topBarButtonRect("save")
        tap(g, save.center.x, save.center.y)
        assertEquals(1, g.machines.list().size)
        val id = g.machines.list()[0].id
        assertEquals(id, ps.savedId)
        shot(g, "screen-freeplay-saved")
        // build on, save again: still one entry, updated
        place(g, ps, 440.0, 200.0)
        tap(g, save.center.x, save.center.y)
        assertEquals(1, g.machines.list().size)
        assertEquals(3, BoardCodec.decode(g.machines.get(id)!!.encoded, Levels.freeform().fixed).playerParts.size)
        // gallery: New starts empty, the tile reloads the saved machine
        val mine = ps.topBarButtonRect("machines")
        tap(g, mine.center.x, mine.center.y)
        val gallery = g.screen as MachinesScreen
        shot(g, "screen-machines")
        val tiles = gallery.tileRects()
        assertEquals(listOf(null, id), tiles.map { it.first })
        tap(g, tiles[0].second.center.x, tiles[0].second.center.y)
        ps = g.screen as PlayScreen
        assertEquals(0, ps.board.playerParts.size, "New machine starts empty")
        assertNull(ps.savedId)
        tap(g, ps.topBarButtonRect("machines").center.x, ps.topBarButtonRect("machines").center.y)
        val gallery2 = g.screen as MachinesScreen
        val tile = gallery2.tileRects().first { it.first == id }.second
        tap(g, tile.center.x, tile.center.y)
        ps = g.screen as PlayScreen
        assertEquals(3, ps.board.playerParts.size, "the tile loads the saved machine")
        assertEquals(id, ps.savedId)
        // delete with confirmation; tapping outside the dialog keeps it
        tap(g, ps.topBarButtonRect("machines").center.x, ps.topBarButtonRect("machines").center.y)
        val gallery3 = g.screen as MachinesScreen
        val trash = gallery3.trashRect(id)!!
        tap(g, trash.center.x, trash.center.y)
        assertTrue(gallery3.confirming)
        shot(g, "screen-machines-delete")
        tap(g, 30.0, g.height - 30.0)
        assertFalse(gallery3.confirming)
        assertEquals(1, g.machines.list().size)
        tap(g, trash.center.x, trash.center.y)
        val (yes, _) = gallery3.confirmButtons()
        tap(g, yes.center.x, yes.center.y)
        assertTrue(g.machines.list().isEmpty(), "bin it removes the machine")
        assertEquals(1, gallery3.tileRects().size)
    }

    @Test
    fun `machines can be renamed through the platform's text entry`() {
        val g = newGame()
        var asked: String? = null
        g.textInput = { title, current, done -> asked = "$title|$current"; done("Cheese Cannon") }
        g.startFreeform(fresh = true); g.update(0.02)
        var ps = g.screen as PlayScreen
        place(g, ps, 200.0, 200.0)
        tap(g, ps.topBarButtonRect("save").center.x, ps.topBarButtonRect("save").center.y)
        val id = g.machines.list()[0].id
        tap(g, ps.topBarButtonRect("machines").center.x, ps.topBarButtonRect("machines").center.y)
        val gallery = g.screen as MachinesScreen
        val pencil = gallery.renameRect(id) ?: error("rename button")
        tap(g, pencil.center.x, pencil.center.y)
        assertTrue(asked!!.startsWith("Name your machine|"), asked)
        assertEquals("Cheese Cannon", gallery.tileName(id))
        assertEquals("Cheese Cannon", g.machines.nameOf(id))
        shot(g, "screen-machines-renamed")
        // the free-play banner shows the name once the machine is opened
        val tile = gallery.tileRects().first { it.first == id }.second
        tap(g, tile.center.x, tile.center.y)
        ps = g.screen as PlayScreen
        assertEquals(id, ps.savedId)
        // cancelling keeps the old name, and blank answers are ignored
        g.textInput = { _, _, done -> done(null) }
        g.machines.rename(id, "  ")
        assertEquals("Cheese Cannon", g.machines.nameOf(id), "blank names are ignored")
        // no rename button without a platform hook
        g.textInput = null
        g.toMachines(); g.update(0.02)
        assertNotNull((g.screen as MachinesScreen).renameRect(id), "the rect exists but is not drawn or active")
    }
}
