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
        // grain lines parallel to the slope, kept inside the triangle
        val grain = Colors.withAlpha(Style.WOOD_DARK, 0.55)
        val n = maxOf(2, (h / 9).toInt())
        for (i in 1..n) {
            val d = h * i / (n + 1.0)                       // vertical offset below the slope
            val ax = v[0].x; val ay = v[0].y + d           // on the vertical side
            val bx = v[0].x + (v[1].x - v[0].x) * (1 - d / h)  // where the shifted line meets the base
            val by = v[1].y
            p.line(ax, ay, bx, by, grain, 1.0)
        }
        // slope surface highlight and base shadow
        p.line(v[0].x, v[0].y, v[1].x, v[1].y, Colors.withAlpha(Style.WHITE, 0.4), 2.2)
        p.line(minOf(v[1].x, v[2].x) + 3, v[1].y - 1.5, maxOf(v[1].x, v[2].x) - 3, v[1].y - 1.5, Colors.withAlpha(Style.WOOD_DARK, 0.6), 2.0)
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
