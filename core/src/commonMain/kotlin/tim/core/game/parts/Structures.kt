package tim.core.game.parts

import tim.core.game.Draw
import tim.core.game.Part
import tim.core.game.PartType
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.physics.Body
import tim.core.physics.BodyKind
import tim.core.physics.PolygonShape
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.Path

/** Brick, wood and small walls: static rectangles that can be rotated. */
class Wall(placement: Placement, index: Int) : Part(placement, index) {
    override fun build(world: World) {
        val friction = when (type) { PartType.WOOD_WALL -> 0.3; else -> 0.5 }
        val b = Body(PolygonShape.rect(x, y, x + w, y + h), Vec2.ZERO, BodyKind.STATIC, friction = friction, owner = this, tag = type.name)
        bodies.add(world.add(b))
    }

    override fun draw(p: Painter, t: Double) {
        when (type) {
            PartType.WOOD_WALL -> drawWood(p)
            else -> drawBricks(p)
        }
    }

    private fun drawBricks(p: Painter) {
        p.fillRect(x, y, w, h, Style.MORTAR)
        p.save()
        p.clipRect(x, y, w, h)
        val bw = 16.0; val bh = 8.0
        val cols = (w / bw).toInt() + 2
        val rows = (h / bh).toInt() + 1
        for (r in 0 until rows) {
            val off = if (r % 2 == 0) 0.0 else -bw / 2
            for (c in 0 until cols) {
                val bx = x + off + c * bw
                val by = y + r * bh
                p.fillRoundRect(bx + 1, by + 1, bw - 2, bh - 2, 1.5, if ((r + c) % 3 == 0) Style.BRICK_DARK else Style.BRICK)
            }
        }
        p.restore()
        p.strokeRect(x, y, w, h, Style.OUTLINE, Style.LINE)
    }

    private fun drawWood(p: Painter) {
        p.fillRect(x, y, w, h, Style.WOOD)
        p.save()
        p.clipRect(x, y, w, h)
        Draw.woodGrain(p, x, y, w, h, vertical = h > w)
        // bevel: light top edge, dark bottom edge
        if (w >= h) {
            p.fillRect(x, y, w, 2.0, Colors.withAlpha(Style.WHITE, 0.35))
            p.fillRect(x, y + h - 2, w, 2.0, Colors.withAlpha(Style.WOOD_DARK, 0.6))
        } else {
            p.fillRect(x, y, 2.0, h, Colors.withAlpha(Style.WHITE, 0.35))
            p.fillRect(x + w - 2, y, 2.0, h, Colors.withAlpha(Style.WOOD_DARK, 0.6))
        }
        p.restore()
        p.strokeRect(x, y, w, h, Style.OUTLINE, Style.LINE)
    }
}

/**
 * Wooden ramp. Unflipped it is high on the left and slopes down to the right; flipping mirrors it
 * and turning it upside down (two quarter turns) makes a slide for things that float upwards.
 */
class Incline(placement: Placement, index: Int) : Part(placement, index) {
    /** Triangle vertices in world space: acute tip, far end of the slope, right-angle corner. */
    fun vertices(): List<Vec2> {
        val w0 = type.w; val h0 = type.h
        var pts = listOf(Vec2(0.0, 0.0), Vec2(w0, h0), Vec2(0.0, h0))
        if (flipped) pts = pts.map { Vec2(w0 - it.x, it.y) }
        var bh = h0
        var bw = w0
        repeat(rotation % 4) { pts = pts.map { Vec2(bh - it.y, it.x) }; val t = bw; bw = bh; bh = t }
        return pts.map { Vec2(x + it.x, y + it.y) }
    }

    override fun build(world: World) {
        val b = Body(PolygonShape(vertices()), Vec2.ZERO, BodyKind.STATIC, friction = 0.35, owner = this, tag = type.name)
        bodies.add(world.add(b))
    }

    override fun draw(p: Painter, t: Double) {
        val v = vertices()
        val a = v[0]; val b = v[1]; val c = v[2]
        val path = Path.polygon(a.x, a.y, b.x, b.y, c.x, c.y)
        p.fillPath(path, Style.WOOD)
        // grain lines parallel to the slope, kept inside the triangle
        val grain = Colors.withAlpha(Style.WOOD_DARK, 0.55)
        val n = maxOf(2, (minOf(type.w, type.h) / 9).toInt())
        for (i in 1..n) {
            val k = i / (n + 1.0)
            p.line(a.x + (c.x - a.x) * k, a.y + (c.y - a.y) * k, b.x + (c.x - b.x) * k, b.y + (c.y - b.y) * k, grain, 1.0)
        }
        // slope highlight and a shadow along the base (the edge from the right angle to the far end)
        p.line(a.x, a.y, b.x, b.y, Colors.withAlpha(Style.WHITE, 0.4), 2.2)
        val mid = Vec2((b.x + c.x) / 2, (b.y + c.y) / 2)
        val inward = (a - mid).let { val l = it.length; if (l > 0) Vec2(it.x / l * 1.5, it.y / l * 1.5) else Vec2.ZERO }
        val along = (b - c).let { val l = it.length; if (l > 0) Vec2(it.x / l * 3, it.y / l * 3) else Vec2.ZERO }
        p.line(c.x + inward.x + along.x, c.y + inward.y + along.y, b.x + inward.x - along.x, b.y + inward.y - along.y, Colors.withAlpha(Style.WOOD_DARK, 0.6), 2.0)
        p.strokePath(path, Style.OUTLINE, Style.LINE)
    }
}

/** Rope, belt and wire tools: they never sit on the board, this only draws their tray icons. */
class LinkTool(placement: Placement, index: Int) : Part(placement, index) {
    override fun draw(p: Painter, t: Double) {
        when (type) {
            PartType.ROPE -> {
                // a coiled rope
                for (i in 0 until 3) {
                    val cy = y + 7 + i * 5.0
                    p.strokeOval(x + 4, cy - 4, w - 8, 9.0, Style.OUTLINE, 4.2)
                    p.strokeOval(x + 4, cy - 4, w - 8, 9.0, Style.WOOD, 2.4)
                }
                val tail = Path().moveTo(x + w - 9, y + 20).quadTo(x + w - 6, y + 23, x + w - 12, y + 24)
                p.strokePath(tail, Style.OUTLINE, 4.2); p.strokePath(tail, Style.WOOD, 2.4)
            }
            PartType.BELT -> {
                // a belt loop around two wheels
                p.strokeRoundRect(x + 3, y + 5, w - 6, h - 10, (h - 10) / 2, Style.OUTLINE, 4.5)
                for (cx2 in listOf(x + 3 + (h - 10) / 2, x + w - 3 - (h - 10) / 2)) {
                    p.fillCircle(cx2, y + h / 2, 4.0, Style.STEEL_LIGHT)
                    p.strokeCircle(cx2, y + h / 2, 4.0, Style.OUTLINE, 1.4)
                }
            }
            else -> {
                // a cable with a plug
                val cable = Path().moveTo(x + 2, y + h - 4).quadTo(x + 10, y - 4, x + w - 12, y + 10)
                p.strokePath(cable, Style.OUTLINE, 3.2); p.strokePath(cable, Style.GREY_DARK, 1.6)
                Draw.outlinedRoundRect(p, x + w - 14, y + 4, 12.0, 12.0, 2.0, Style.CREAM, 1.4)
                p.fillRect(x + w - 11, y + 7, 2.0, 5.0, Style.OUTLINE)
                p.fillRect(x + w - 7, y + 7, 2.0, 5.0, Style.OUTLINE)
            }
        }
    }
}

/** Factory mapping part types to implementations. */
object PartFactory {
    fun create(pl: Placement, index: Int): Part = when (pl.type) {
        PartType.ROPE, PartType.BELT, PartType.WIRE -> LinkTool(pl, index)
        PartType.BOWLING_BALL, PartType.BASKETBALL, PartType.BASEBALL, PartType.TENNIS_BALL,
        PartType.SUPER_BALL, PartType.CANNONBALL -> Ball(pl, index)
        PartType.BALLOON -> Balloon(pl, index)
        PartType.BRICK_WALL, PartType.WOOD_WALL, PartType.SMALL_WALL -> Wall(pl, index)
        PartType.INCLINE, PartType.STEEP_INCLINE -> Incline(pl, index)
        PartType.SEESAW -> Seesaw(pl, index)
        PartType.TRAMPOLINE -> Trampoline(pl, index)
        PartType.CONVEYOR -> Conveyor(pl, index)
        PartType.FAN -> Fan(pl, index)
        PartType.BUCKET -> Bucket(pl, index)
        PartType.CAGE -> Cage(pl, index)
        PartType.CANDLE -> Candle(pl, index)
        PartType.CANNON -> Cannon(pl, index)
        PartType.DYNAMITE -> Dynamite(pl, index)
        PartType.ROCKET -> Rocket(pl, index)
        PartType.BUMPER -> Bumper(pl, index)
        PartType.BOXING_GLOVE -> BoxingGlove(pl, index)
        PartType.SCISSORS -> Scissors(pl, index)
        PartType.BELLOWS -> Bellows(pl, index)
        PartType.PULLEY -> Pulley(pl, index)
        PartType.HOOK -> Hook(pl, index)
        PartType.MOTOR -> Motor(pl, index)
        PartType.MOUSE -> Mouse(pl, index)
        PartType.CAT -> Cat(pl, index)
        PartType.CHEESE -> Cheese(pl, index)
        PartType.SWITCH -> Switch(pl, index)
        PartType.OUTLET -> Outlet(pl, index)
        PartType.FLASHLIGHT -> Flashlight(pl, index)
        PartType.HOOP -> Hoop(pl, index)
        PartType.BELL -> Bell(pl, index)
        PartType.STAR -> Star(pl, index)
    }
}
