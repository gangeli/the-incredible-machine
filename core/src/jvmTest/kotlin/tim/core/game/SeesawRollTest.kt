package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.parts.*
import tim.core.render.Colors
import tim.desktop.Java2DPainter
import tim.desktop.Snap

class SeesawRollTest {
    private fun floor(b: MachineBuilder) { for (i in 0 until 7) b.part(PartType.BRICK_WALL, i * 96.0, 384.0) }

    @Test
    fun `a ball dropped on the high end of an empty seesaw tips it and rolls to the new low end`() {
        val m = machine { floor(this); part(PartType.SEESAW, 200.0, 352.0); part(PartType.BASEBALL, 264.0, 328.0) }
        val ball = m.part<Ball>(8)
        val seesaw = m.part<Seesaw>(7)
        m.run(2.0)
        assertEquals(1, seesaw.tilt, "nothing on the other end: the seesaw should tip")
        assertTrue(ball.pos.x > 276.0, "ball should have rolled out to the right lip, x=${ball.pos.x}")
    }

    @Test
    fun `a light ball cannot tip a seesaw holding a heavy ball, and rolls down to it`() {
        val m = machine { floor(this); part(PartType.SEESAW, 200.0, 352.0); part(PartType.BOWLING_BALL, 204.0, 348.0); part(PartType.BASEBALL, 272.0, 328.0) }
        val seesaw = m.part<Seesaw>(7)
        val baseball = m.part<Ball>(9)
        m.run(2.5)
        assertEquals(-1, seesaw.tilt, "a baseball must not outweigh a bowling ball")
        assertTrue(baseball.pos.x < 250.0, "baseball should roll down the plank towards the bowling ball, x=${baseball.pos.x}")
    }

    @Test
    fun `a ball set gently on the low end stays against the lip`() {
        val m = machine { floor(this); part(PartType.SEESAW, 200.0, 352.0); part(PartType.BASEBALL, 208.0, 344.0) }
        val ball = m.part<Ball>(8)
        m.run(3.0)
        assertTrue(ball.pos.x in 200.0..232.0, "ball should rest at the low end, x=${ball.pos.x}")
        assertTrue(ball.pos.y < 384.0 - 8.0 - 1.0, "ball should still be on the plank, y=${ball.pos.y}")
        // film strip for a look
        val p = Java2DPainter.create(900, 220); p.clear(Colors.rgb(0xF3F7FB))
        val m2 = machine { floor(this); part(PartType.SEESAW, 200.0, 352.0); part(PartType.BASEBALL, 264.0, 328.0) }
        for (f in 0 until 6) { m2.run(0.22); p.save(); p.translate(f * 150.0 - 190 * 1.0 + 0.0, 40.0); p.scale(1.4, 1.4); p.translate(-120.0, -300.0); m2.draw(p, m2.time); p.restore() }
        Snap.save(p, "strip-seesaw-roll")
    }
}
