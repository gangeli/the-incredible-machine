package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.parts.Bellows
import tim.core.game.parts.BoxingGlove
import tim.core.game.parts.Cannon
import tim.core.game.parts.Fan
import tim.core.game.parts.Flashlight
import tim.core.game.parts.Scissors
import tim.core.physics.Vec2
import tim.desktop.Java2DPainter
import tim.desktop.Snap
import java.awt.image.BufferedImage

/**
 * Flipping a part must mirror both its picture and what it does: a flipped fan blows the other
 * way and looks like it does. Every flippable type is checked so a new part cannot forget.
 */
class FlipAuditTest {
    private val floorY = 384.0
    private fun floor(b: MachineBuilder) { for (i in 0 until 7) b.part(PartType.BRICK_WALL, i * 96.0, floorY) }

    private fun render(type: PartType, flipped: Boolean): BufferedImage {
        val m = machine { part(type, 256.0, 200.0, flipped = flipped) }
        val part = m.parts[0]
        val scale = 4
        val margin = 8.0
        val p = Java2DPainter.create(((part.w + 2 * margin) * scale).toInt(), ((part.h + 2 * margin) * scale).toInt())
        p.save(); p.scale(scale.toDouble(), scale.toDouble()); p.translate(-part.x + margin, -part.y + margin)
        part.draw(p, 0.0)
        p.restore()
        return p.image
    }

    private fun mirrored(img: BufferedImage): BufferedImage {
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until img.height) for (x in 0 until img.width) out.setRGB(img.width - 1 - x, y, img.getRGB(x, y))
        return out
    }

    /** Fraction of painted pixels that differ noticeably between two images. */
    private fun difference(a: BufferedImage, b: BufferedImage): Double {
        var painted = 0; var differ = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val pa = a.getRGB(x, y); val pb = b.getRGB(x, y)
            val aa = (pa ushr 24) and 0xFF; val ab = (pb ushr 24) and 0xFF
            if (aa > 40 || ab > 40) painted++ else continue
            val d = maxOf(Math.abs(aa - ab), Math.abs(((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF)), Math.abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF)), Math.abs((pa and 0xFF) - (pb and 0xFF)))
            if (d > 64) differ++
        }
        return if (painted == 0) 0.0 else differ.toDouble() / painted
    }

    @Test
    fun `every flippable part draws as the mirror image of itself when flipped`() {
        val bad = ArrayList<String>()
        for (t in PartType.values().filter { it.flippable }) {
            val plain = render(t, false)
            val flipped = render(t, true)
            val diff = difference(mirrored(plain), flipped)
            val sheet = Java2DPainter.create(plain.width * 3 + 16, plain.height)
            sheet.clear(0xFFFFFFFF.toInt())
            sheet.g.drawImage(plain, 0, 0, null); sheet.g.drawImage(flipped, plain.width + 8, 0, null); sheet.g.drawImage(mirrored(plain), plain.width * 2 + 16, 0, null)
            Snap.save(sheet, "flip-${t.name.lowercase()}")
            if (diff > 0.12) bad += "${t.name} (${(diff * 100).toInt()}% of pixels differ)"
        }
        assertTrue(bad.isEmpty(), "flipped art is not a mirror image for: $bad")
    }

    @Test
    fun `a flipped part must look different from an unflipped one so players can tell which way it faces`() {
        val same = ArrayList<String>()
        for (t in PartType.values().filter { it.flippable }) {
            val diff = difference(render(t, false), render(t, true))
            if (diff < 0.01) same += t.name
        }
        assertTrue(same.isEmpty(), "flipping changes nothing visible for: $same")
    }

    @Test
    fun `fans blow the way they face`() {
        for (flipped in listOf(false, true)) {
            val m = machine {
                floor(this)
                // plugged in by the outlet beside it; the ball sits in front of the fan
                part(PartType.FAN, 344.0, floorY - 40, flipped = flipped)
                part(PartType.OUTLET, if (flipped) 376.0 else 320.0, floorY - 32)
                part(PartType.TENNIS_BALL, if (flipped) 296.0 else 400.0, floorY - 16)
            }
            val fan = m.part<Fan>(7)
            assertTrue(fan.running, "the fan is plugged in (flipped=$flipped)")
            val ball = m.parts[9].bodies[0]
            val x0 = ball.pos.x
            m.run(1.0)
            if (flipped) assertTrue(ball.pos.x < x0 - 40, "flipped fan blows left: ${ball.pos.x - x0}")
            else assertTrue(ball.pos.x > x0 + 40, "fan blows right: ${ball.pos.x - x0}")
        }
    }

    @Test
    fun `bellows puff, cannons fire and gloves punch the way they face`() {
        for (flipped in listOf(false, true)) {
            val sign = if (flipped) -1.0 else 1.0
            val bel = machine {
                floor(this)
                part(PartType.BELLOWS, 300.0, floorY - 32, flipped = flipped)
                part(PartType.TENNIS_BALL, if (flipped) 270.0 else 380.0, floorY - 16)
            }
            bel.part<Bellows>(7).trigger()
            val bx0 = bel.parts[8].bodies[0].pos.x
            bel.run(0.4)
            assertTrue((bel.parts[8].bodies[0].pos.x - bx0) * sign > 20, "bellows puff (flipped=$flipped): ${bel.parts[8].bodies[0].pos.x - bx0}")

            val can = machine { floor(this); part(PartType.CANNON, 300.0, floorY - 40, flipped = flipped) }
            can.part<Cannon>(7).trigger()
            assertTrue(can.runUntil(2.0) { it.spawned.isNotEmpty() }, "the cannon fires")
            val shot = can.spawned.first().bodies[0]
            assertTrue(shot.vel.x * sign > 200, "cannonball flies the way the cannon faces (flipped=$flipped): ${shot.vel.x}")

            val glove = machine {
                floor(this)
                part(PartType.BOXING_GLOVE, 300.0, floorY - 40, flipped = flipped)
                part(PartType.BASKETBALL, if (flipped) 272.0 else 360.0, floorY - 32)
            }
            glove.part<BoxingGlove>(7).trigger()
            val gx0 = glove.parts[8].bodies[0].pos.x
            glove.run(0.5)
            assertTrue((glove.parts[8].bodies[0].pos.x - gx0) * sign > 30, "punch (flipped=$flipped): ${glove.parts[8].bodies[0].pos.x - gx0}")
        }
    }

    @Test
    fun `flashlight beams and scissor blades sit on the facing side`() {
        val m = machine {
            part(PartType.FLASHLIGHT, 100.0, 100.0)
            part(PartType.FLASHLIGHT, 100.0, 200.0, flipped = true)
            part(PartType.SCISSORS, 300.0, 100.0)
            part(PartType.SCISSORS, 300.0, 200.0, flipped = true)
        }
        assertTrue(m.part<Flashlight>(0).beam().minX >= 148.0)
        assertTrue(m.part<Flashlight>(1).beam().maxX <= 100.0)
        assertTrue(m.part<Scissors>(2).isSharpAt(Vec2(340.0, 118.0)) && !m.part<Scissors>(2).isSharpAt(Vec2(306.0, 118.0)))
        assertTrue(m.part<Scissors>(3).isSharpAt(Vec2(308.0, 218.0)) && !m.part<Scissors>(3).isSharpAt(Vec2(342.0, 218.0)))
    }
}
