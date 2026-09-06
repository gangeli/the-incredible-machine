package tim.core.game

import org.junit.jupiter.api.Test
import tim.core.game.parts.*
import tim.core.physics.World
import tim.core.render.Colors
import tim.core.render.textCentered
import tim.desktop.Java2DPainter
import tim.desktop.Snap

/**
 * Renders every part at high zoom in several states (idle, animating, flipped/rotated, active) so
 * the art can be inspected closely for glitches.
 */
class ArtSheetTest {
    private fun cellFor(t: PartType) = 4.0 * maxOf(t.w, t.h) + 40.0

    /** Builds a machine containing just this part plus a floor, runs it a bit, and returns the part. */
    private fun activated(t: PartType, flipped: Boolean, rotation: Int): Pair<Machine, Part> {
        val b = MachineBuilder()
        for (i in 0 until 7) b.part(PartType.BRICK_WALL, i * 96.0, 384.0)
        val idx = b.part(t, 300.0, 384.0 - t.h - (if (rotation % 2 == 1) 0.0 else 0.0), flipped, rotation)
        val m = b.build()
        val part = m.parts[idx]
        m.run(0.3)
        when (part) {
            is Seesaw -> part.startTip(if (flipped) -1 else 1)
            is Conveyor -> {}
            is Switch -> part.trigger()
            is Flashlight -> part.trigger()
            is Bell -> part.ring()
            is Star -> part.onSensor(part.bodies[0], part.bodies[0])
            is Cannon -> { part.lightFuse(); m.run(0.6) }
            is Dynamite -> part.light()
            is Rocket -> { part.light(); m.run(0.65) }
            is BoxingGlove -> { part.trigger(); m.run(0.08) }
            is Scissors -> part.trigger()
            is Bellows -> part.trigger()
            is Balloon -> {}
            is Candle -> {}
            is Cat -> { part.walking = true }
            is Mouse -> { part.walking = true }
            else -> {}
        }
        m.run(0.12)
        return m to part
    }

    @Test
    fun `render art sheet`() {
        val types = PartType.values()
        val states = listOf("idle", "anim", "flip/rot", "active")
        val cols = states.size
        val cellW = 300.0
        val cellH = 300.0
        val p = Java2DPainter.create((cellW * cols + 160).toInt(), (cellH * types.size).toInt())
        p.clear(Colors.rgb(0xF3F7FB))
        types.forEachIndexed { row, t ->
            val y0 = row * cellH
            p.text(t.label, 8.0, y0 + 30, 18.0, Style.OUTLINE)
            p.text("${t.w.toInt()}x${t.h.toInt()}", 8.0, y0 + 52, 14.0, Style.GREY_DARK, bold = false)
            for ((col, state) in states.withIndex()) {
                val x0 = 160 + col * cellW
                p.fillRoundRect(x0 + 6, y0 + 6, cellW - 12, cellH - 12, 16.0, Style.WHITE)
                p.strokeRoundRect(x0 + 6, y0 + 6, cellW - 12, cellH - 12, 16.0, Colors.withAlpha(Style.OUTLINE, 0.15), 1.5)
                p.text(state, x0 + 14, y0 + 26, 13.0, Style.GREY_DARK, bold = false)
                val flipped = state == "flip/rot" && t.flippable
                val rotation = if (state == "flip/rot" && t.rotatable) 1 else 0
                val pw = if (rotation == 1) t.h else t.w
                val ph = if (rotation == 1) t.w else t.h
                val scale = minOf((cellW - 60) / pw, (cellH - 70) / ph, 5.0)
                p.save()
                p.translate(x0 + (cellW - pw * scale) / 2, y0 + 40 + (cellH - 70 - ph * scale) / 2)
                p.scale(scale, scale)
                if (state == "active") {
                    val (m, part) = activated(t, flipped, rotation)
                    val b = part.worldBounds
                    // draw relative to the part's current bounds
                    p.translate(-b.minX, -b.minY)
                    part.draw(p, 0.4)
                    for (e in m.effects) Effects.draw(p, e)
                } else {
                    val part = PartFactory.create(Placement(t, 0.0, 0.0, flipped, rotation), 0)
                    part.draw(p, if (state == "anim") 0.37 else 0.0)
                }
                p.restore()
            }
        }
        Snap.save(p, "art-sheet")
    }
}
