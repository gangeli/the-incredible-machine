package tim.core.game.parts

import tim.core.game.Activatable
import tim.core.game.Draw
import tim.core.game.Part
import tim.core.game.PartType
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.game.Effect
import tim.core.physics.Body
import tim.core.physics.BodyKind
import tim.core.physics.CircleShape
import tim.core.physics.ContactEvent
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.Path

/** Physical constants for each ball, scaled from the original engine's part table. */
class BallSpec(val radius: Double, val mass: Double, val restitution: Double, val rolling: Double, val color: Int, val dark: Int) {
    companion object {
        fun of(t: PartType) = when (t) {
            PartType.BOWLING_BALL -> BallSpec(16.0, 20.0, 0.5, 0.008, Colors.rgb(0x2D4B9E), Colors.rgb(0x162A5C))
            PartType.BASKETBALL -> BallSpec(16.0, 2.0, 0.75, 0.02, Colors.rgb(0xF48C3B), Colors.rgb(0xB85A1A))
            PartType.BASEBALL -> BallSpec(8.0, 0.9, 0.25, 0.03, Colors.rgb(0xFFFFFF), Colors.rgb(0xC9CDD3))
            PartType.TENNIS_BALL -> BallSpec(8.0, 0.5, 0.75, 0.03, Colors.rgb(0xD7F04A), Colors.rgb(0x8FB320))
            PartType.SUPER_BALL -> BallSpec(8.0, 1.0, 0.98, 0.01, Colors.rgb(0xFF5E5B), Colors.rgb(0xB8322F))
            PartType.CANNONBALL -> BallSpec(12.0, 200.0, 0.125, 0.01, Colors.rgb(0x4A4E57), Colors.rgb(0x1E2126))
            else -> BallSpec(16.0, 1.0, 0.5, 0.02, Style.GREY, Style.GREY_DARK)
        }
    }
}

open class Ball(placement: Placement, index: Int) : Part(placement, index) {
    val spec = BallSpec.of(type)
    var body: Body? = null
    val pos: Vec2 get() = body?.pos ?: Vec2(cx, cy)
    val angle: Double get() = body?.angle ?: 0.0

    override fun build(world: World) {
        val b = Body(CircleShape(spec.radius), Vec2(cx, cy), BodyKind.DYNAMIC, mass = spec.mass, restitution = spec.restitution, friction = 0.6, owner = this, tag = type.name)
        b.rollingFriction = spec.rolling
        body = world.add(b)
        bodies.add(b)
    }

    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (ev.relativeSpeed > 120 && spec.mass >= 2.0) machine.sounds.add("thud")
        else if (ev.relativeSpeed > 90) machine.sounds.add("bounce")
    }

    override fun draw(p: Painter, t: Double) {
        val c = pos
        val r = spec.radius
        when (type) {
            PartType.BOWLING_BALL -> Draw.ball(p, c.x, c.y, r, spec.color, spec.dark, angle) { q ->
                q.fillCircle(-r * 0.15, -r * 0.35, r * 0.13, Style.OUTLINE)
                q.fillCircle(r * 0.2, -r * 0.3, r * 0.13, Style.OUTLINE)
                q.fillCircle(0.0, -r * 0.05, r * 0.13, Style.OUTLINE)
            }
            PartType.BASKETBALL -> Draw.ball(p, c.x, c.y, r, spec.color, spec.dark, angle) { q ->
                q.line(-r, 0.0, r, 0.0, Style.OUTLINE, 1.5)
                q.line(0.0, -r, 0.0, r, Style.OUTLINE, 1.5)
                val seamL = Path().moveTo(-r * 0.55, -r * 0.83).quadTo(-r * 1.15, 0.0, -r * 0.55, r * 0.83)
                val seamR = Path().moveTo(r * 0.55, -r * 0.83).quadTo(r * 1.15, 0.0, r * 0.55, r * 0.83)
                q.strokePath(seamL, Style.OUTLINE, 1.5)
                q.strokePath(seamR, Style.OUTLINE, 1.5)
            }
            PartType.BASEBALL -> Draw.ball(p, c.x, c.y, r, spec.color, spec.dark, angle) { q ->
                val s1 = Path().moveTo(-r * 0.5, -r * 0.85).quadTo(-r * 1.0, 0.0, -r * 0.5, r * 0.85)
                val s2 = Path().moveTo(r * 0.5, -r * 0.85).quadTo(r * 1.0, 0.0, r * 0.5, r * 0.85)
                q.strokePath(s1, Style.RED, 1.2)
                q.strokePath(s2, Style.RED, 1.2)
                for (i in 0 until 4) {
                    val t = -0.6 + i * 0.4
                    val yy = t * r * 0.85
                    val xx = -r * (0.5 + 0.25 * (1 - t * t))
                    q.line(xx - 1.2, yy - 0.8, xx + 1.2, yy + 0.8, Style.RED, 0.9)
                    q.line(-xx - 1.2, yy + 0.8, -xx + 1.2, yy - 0.8, Style.RED, 0.9)
                }
            }
            PartType.TENNIS_BALL -> Draw.ball(p, c.x, c.y, r, spec.color, spec.dark, angle) { q ->
                val s1 = Path().moveTo(-r * 0.45, -r * 0.88).quadTo(-r * 1.05, 0.0, -r * 0.45, r * 0.88)
                val s2 = Path().moveTo(r * 0.45, -r * 0.88).quadTo(r * 1.05, 0.0, r * 0.45, r * 0.88)
                q.strokePath(s1, Style.WHITE, 1.6)
                q.strokePath(s2, Style.WHITE, 1.6)
            }
            PartType.SUPER_BALL -> Draw.ball(p, c.x, c.y, r, spec.color, spec.dark, angle) { q ->
                q.fillCircle(-r * 0.3, r * 0.2, r * 0.25, Style.YELLOW)
                q.fillCircle(r * 0.35, -r * 0.1, r * 0.2, Style.TEAL)
                q.fillCircle(r * 0.1, r * 0.45, r * 0.15, Style.WHITE)
            }
            PartType.CANNONBALL -> Draw.ball(p, c.x, c.y, r, spec.color, spec.dark, angle)
            else -> Draw.ball(p, c.x, c.y, r, spec.color, spec.dark, angle)
        }
    }
}

/** Balloon: buoyant, pops on flame, sharp things and explosions. Ropes tie to its knot. */
class Balloon(placement: Placement, index: Int) : Part(placement, index), Activatable {
    companion object {
        const val R = 15.0; const val LIFT = 351.0
        /** Upward pull a balloon gives whatever it is tied to: half a cage's weight, so two balloons hold a cage and three lift it. */
        const val PULL = 3500.0
    }
    var body: Body? = null
    var popped = false
    private var popTime = -1.0
    override val activated get() = popped
    val pos: Vec2 get() = body?.pos ?: Vec2(x + 16, y + 15)
    val color = Style.RED

    override fun build(world: World) {
        // slippery, so it slides along ceilings and upside-down ramps instead of sticking
        val b = Body(CircleShape(R), Vec2(x + 16, y + 15), BodyKind.DYNAMIC, mass = 0.1, restitution = 0.25, friction = 0.05, owner = this, tag = "BALLOON")
        b.rollingFriction = 0.2
        b.linearDamping = 1.75
        body = world.add(b)
        bodies.add(b)
    }

    override fun preStep() {
        val b = body ?: return
        if (popped) return
        val g = machine.world.gravity.y
        val lift = (g + LIFT) * machine.world.airPressure
        b.applyForce(Vec2(0.0, -lift * b.mass))
    }

    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        val o = machine.partOf(other)
        if (o is Sharp && o.isSharpAt(ev.point)) pop()
    }

    override fun onFlame(source: Part) = pop()
    override fun onExplosion(center: Vec2, radius: Double) { if (pos.distanceTo(center) < radius) pop() }

    fun pop() {
        if (popped) return
        popped = true
        popTime = machine.time
        // a string tied to a popped balloon goes slack and falls, like the original's severed end
        for (r in machine.ropesOf(this)) machine.cutRope(r)
        body?.enabled = false
        machine.effects.add(Effect(Effect.Kind.POP, pos, 0.35, 1.0, color))
        machine.sounds.add("pop")
        for (r in machine.ropesOf(this)) machine.cutRope(r)
    }

    override fun ropeAnchor() = Vec2(16.0, 38.0)
    override val ropeBody: Body? get() = body

    override fun draw(p: Painter, t: Double) {
        if (popped) return
        val c = pos
        val bob = if (built) 0.0 else kotlin.math.sin(t * 3) * 1.0
        val cx = c.x; val cy = c.y + bob
        // string stub
        p.line(cx, cy + R + 6, cx + 2, cy + R + 14, Style.OUTLINE, 1.2)
        // balloon body (slightly egg shaped)
        val path = Path()
        path.moveTo(cx, cy - R)
        path.cubicTo(cx + R * 1.35, cy - R, cx + R * 1.15, cy + R * 0.9, cx, cy + R + 2)
        path.cubicTo(cx - R * 1.15, cy + R * 0.9, cx - R * 1.35, cy - R, cx, cy - R)
        path.close()
        p.fillPath(path, color)
        p.fillOval(cx - R * 0.6, cy - R * 0.75, R * 0.45, R * 0.7, 0x66FFFFFF)
        p.strokePath(path, Style.OUTLINE, Style.LINE)
        // knot
        val knot = Path.polygon(cx - 4, cy + R + 7, cx + 4, cy + R + 7, cx, cy + R + 1)
        p.fillPath(knot, Style.RED_DARK)
        p.strokePath(knot, Style.OUTLINE, 1.2)
    }
}

/** Parts with pointy bits that pop balloons. */
interface Sharp { fun isSharpAt(point: Vec2): Boolean }
