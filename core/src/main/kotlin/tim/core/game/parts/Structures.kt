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
        if (w >= h) {
            var gx = x + 6
            while (gx < x + w) { p.line(gx, y + 3, gx + 8, y + h - 3, Style.WOOD_DARK, 1.0); gx += 14 }
            p.line(x, y + h / 2, x + w, y + h / 2, Colors.withAlpha(Style.WOOD_DARK, 0.5), 1.0)
        } else {
            var gy = y + 6
            while (gy < y + h) { p.line(x + 3, gy, x + w - 3, gy + 8, Style.WOOD_DARK, 1.0); gy += 14 }
            p.line(x + w / 2, y, x + w / 2, y + h, Colors.withAlpha(Style.WOOD_DARK, 0.5), 1.0)
        }
        p.restore()
        p.strokeRect(x, y, w, h, Style.OUTLINE, Style.LINE)
    }
}

/** Wooden ramp. Unflipped it is high on the left and slopes down to the right. */
class Incline(placement: Placement, index: Int) : Part(placement, index) {
    /** Triangle vertices in world space: top of the high side, bottom-far, bottom-near. */
    fun vertices(): List<Vec2> {
        val topX = if (flipped) x + w else x
        val farX = if (flipped) x else x + w
        return listOf(Vec2(topX, y), Vec2(farX, y + h), Vec2(topX, y + h))
    }

    override fun build(world: World) {
        val b = Body(PolygonShape(vertices()), Vec2.ZERO, BodyKind.STATIC, friction = 0.35, owner = this, tag = type.name)
        bodies.add(world.add(b))
    }

    override fun draw(p: Painter, t: Double) {
        val v = vertices()
        val path = Path.polygon(v[0].x, v[0].y, v[1].x, v[1].y, v[2].x, v[2].y)
        p.fillPath(path, Style.WOOD)
        p.save()
        p.clipRect(x, y, w, h)
        // grain lines parallel to the slope
        val n = 3
        for (i in 1..n) {
            val f = i / (n + 1.0)
            val ax = v[0].x + (v[2].x - v[0].x) * f
            val ay = v[0].y + (v[2].y - v[0].y) * f
            val bx = v[0].x + (v[1].x - v[0].x) * f + (v[2].x - v[0].x) * (1 - f) * 0.0
            val by = v[0].y + (v[1].y - v[0].y) * f
            p.line(ax, ay, v[1].x + (ax - v[2].x) * 0.0 * f + (bx - bx), by, Style.WOOD_DARK, 1.0)
        }
        p.restore()
        // slope surface highlight
        p.line(v[0].x, v[0].y, v[1].x, v[1].y, Colors.withAlpha(Style.WHITE, 0.35), 2.0)
        p.strokePath(path, Style.OUTLINE, Style.LINE)
    }
}

/** Factory mapping part types to implementations. */
object PartFactory {
    fun create(pl: Placement, index: Int): Part = when (pl.type) {
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
