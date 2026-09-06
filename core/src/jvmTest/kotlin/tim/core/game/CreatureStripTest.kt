package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.parts.*
import tim.core.render.Colors
import tim.desktop.Java2DPainter
import tim.desktop.Snap

/** Renders film strips of the cat and mouse in every behaviour so the animation can be eyeballed. */
class CreatureStripTest {
    private fun floor(b: MachineBuilder) { for (i in 0 until 7) b.part(PartType.BRICK_WALL, i * 96.0, 384.0) }

    private fun strip(name: String, frames: Int, scale: Double, step: (Int) -> Pair<Machine, Part>) {
        val cellW = 220; val cellH = 160
        val p = Java2DPainter.create(cellW * frames, cellH)
        p.clear(Colors.rgb(0xF3F7FB))
        for (f in 0 until frames) {
            val (m, part) = step(f)
            val c = part.center
            p.save()
            p.translate(f * cellW + cellW / 2.0, cellH * 0.62)
            p.scale(scale, scale)
            p.translate(-c.x, -c.y)
            p.line(c.x - 60, c.y + part.h / 2, c.x + 60, c.y + part.h / 2, Colors.withAlpha(Style.OUTLINE, 0.2), 1.0)
            part.draw(p, m.time)
            p.restore()
            p.text("t=%.2f".format(m.time), f * cellW + 8.0, 18.0, 12.0, Style.GREY_DARK, bold = false)
        }
        Snap.save(p, name)
    }

    @Test
    fun `cat walk cycle, sitting, grooming, startle and fall`() {
        // walking towards a mouse: one machine stepped progressively
        val walk = machine { floor(this); part(PartType.CAT, 100.0, 352.0); part(PartType.MOUSE, 300.0, 368.0, flipped = true) }
        val cat = walk.part<Cat>(7)
        walk.runUntil(3.0) { cat.chasing && cat.moving }
        strip("strip-cat-walk", 8, 3.0) { f -> if (f > 0) walk.run(0.07); walk to cat }
        assertTrue(cat.chasing, "cat should be walking towards the mouse")
        // sitting and grooming over a longer idle period
        val idle = machine { floor(this); part(PartType.CAT, 300.0, 352.0) }
        val cat2 = idle.part<Cat>(7)
        strip("strip-cat-idle", 8, 3.0) { f -> idle.run(if (f == 0) 0.3 else 1.1); idle to cat2 }
        assertTrue(cat2.sitting)
        // startled by a ball, then falling off a ledge
        val hit = machine { floor(this); part(PartType.WOOD_WALL, 200.0, 240.0); part(PartType.CAT, 208.0, 208.0); part(PartType.BASKETBALL, 216.0, 40.0) }
        val cat3 = hit.part<Cat>(8)
        strip("strip-cat-startle", 8, 3.0) { f -> hit.run(if (f == 0) 0.55 else 0.12); hit to cat3 }
        // turning around at a wall
        val turn = machine { floor(this); part(PartType.BRICK_WALL, 400.0, 288.0, rotation = 1); part(PartType.CAT, 300.0, 352.0); part(PartType.MOUSE, 380.0, 368.0) }
        val cat4 = turn.part<Cat>(8)
        strip("strip-cat-turn", 8, 3.0) { f -> turn.run(if (f == 0) 0.2 else 0.15); turn to cat4 }
    }

    @Test
    fun `mouse scurry, sniff, flee, eat and fall`() {
        val run = machine { floor(this); part(PartType.MOUSE, 100.0, 368.0) }
        val mouse = run.part<Mouse>(7)
        strip("strip-mouse-scurry", 10, 4.0) { f -> run.run(if (f == 0) 0.2 else 0.25); run to mouse }
        val flee = machine { floor(this); part(PartType.MOUSE, 300.0, 368.0); part(PartType.CAT, 360.0, 352.0, flipped = true) }
        val m2 = flee.part<Mouse>(7)
        strip("strip-mouse-flee", 6, 4.0) { f -> flee.run(if (f == 0) 0.1 else 0.08); flee to m2 }
        assertTrue(m2.scared)
        val eat = machine { floor(this); part(PartType.MOUSE, 300.0, 368.0); part(PartType.CHEESE, 340.0, 368.0) }
        val m3 = eat.part<Mouse>(7)
        strip("strip-mouse-eat", 6, 4.0) { f -> eat.run(if (f == 0) 0.3 else 0.12); eat to m3 }
        assertTrue(m3.eating)
        val fall = machine { floor(this); part(PartType.WOOD_WALL, 200.0, 240.0); part(PartType.MOUSE, 232.0, 224.0) }
        val m4 = fall.part<Mouse>(8)
        strip("strip-mouse-fall", 8, 4.0) { f -> fall.run(if (f == 0) 0.2 else 0.12); fall to m4 }
    }
}
