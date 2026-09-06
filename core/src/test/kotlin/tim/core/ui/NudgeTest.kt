package tim.core.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.desktop.Java2DPainter
import tim.desktop.Snap

class NudgeTest {
    @Test
    fun `an idle child is shown where the first part goes`() {
        val g = Game(MemoryStorage()).also { it.resize(1280, 800); it.update(0.0) }
        g.startLevel(0); g.update(0.016)
        val ps = g.screen as PlayScreen
        repeat(8 * 30) { g.update(1.0 / 30) }
        val p = Java2DPainter.create(1280, 800)
        g.render(p)
        Snap.save(p, "screen-play-nudge")
        // the hint ghost and arrow are drawn; the board itself stays untouched
        assertTrue(ps.board.playerParts.isEmpty())
    }
}
