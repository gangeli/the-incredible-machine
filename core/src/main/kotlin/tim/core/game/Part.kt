package tim.core.game

import tim.core.physics.AABB
import tim.core.physics.Body
import tim.core.physics.ContactEvent
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Painter
import tim.core.render.Path

/**
 * A part instance living inside a [Machine]. Parts own physics bodies, react to simulation events
 * and draw themselves in world coordinates. Geometry helpers are relative to the placement's
 * top-left corner so the same code serves fixed and player parts.
 */
abstract class Part(val placement: Placement, val index: Int) {
    val type: PartType get() = placement.type
    val x: Double get() = placement.x
    val y: Double get() = placement.y
    val w: Double get() = placement.w
    val h: Double get() = placement.h
    val flipped: Boolean get() = placement.flipped
    val rotation: Int get() = placement.rotation
    val cx: Double get() = x + w / 2
    val cy: Double get() = y + h / 2

    lateinit var machine: Machine
    val bodies = ArrayList<Body>()
    var built = false

    /** Electric power state, refreshed each step from wires; parts with no wires default to on. */
    var powered: Boolean = true
    var hasPowerInput: Boolean = false

    /** Convert a placement-local point (0..w, 0..h, unflipped) to world space, honouring flips. */
    fun local(lx: Double, ly: Double): Vec2 = Vec2(x + (if (flipped) w - lx else lx), y + ly)
    /** Horizontal direction the part faces: +1 for unflipped (facing right), -1 when flipped. */
    val dir: Double get() = if (flipped) -1.0 else 1.0

    open fun build(world: World) {}
    /** Called before each physics step (apply forces, drive kinematics). */
    open fun preStep() {}
    /** Called after each physics step and event dispatch. */
    open fun postStep() {}
    open fun onContact(self: Body, other: Body, ev: ContactEvent) {}
    open fun onSensor(self: Body, other: Body) {}
    /** A flame (candle, rocket exhaust, explosion) touched this part. */
    open fun onFlame(source: Part) {}
    /** An explosion happened at [center]; [radius] is its reach. */
    open fun onExplosion(center: Vec2, radius: Double) {}
    /** Generic activation: hit hard, rope pulled, powered up, etc. */
    open fun trigger() {}
    /** Whether this part outputs electricity (outlets, closed switches). */
    open val powerOutput: Boolean get() = false
    /** Whether this part outputs rotation for belts (motors). */
    open val spinOutput: Boolean get() = false
    /** Belt-driven parts receive their drive here each step. */
    open fun setBeltDrive(running: Boolean, direction: Int) {}
    /** Local anchor point for ropes (relative to placement top-left, unflipped), or null if ropes cannot attach. */
    open fun ropeAnchor(): Vec2? = null
    /** Body that a rope should attach to; null means anchor to the world (static). */
    open val ropeBody: Body? get() = null
    /** Called when a rope attached to this part is pulled taut by something heavier. */
    open fun onRopePull() {}
    /** For pulleys: the world point ropes bend around. */
    open fun pulleyPoint(): Vec2? = null
    /** Hub for belts (world coords). */
    open fun beltHub(): Vec2? = null

    /** Bounding box used for goals and pick tests, following the main body when the part moves. */
    open val worldBounds: AABB
        get() {
            val b = bodies.firstOrNull { it.isDynamic }
            return if (b != null) AABB(b.pos.x - w / 2, b.pos.y - h / 2, b.pos.x + w / 2, b.pos.y + h / 2)
            else placement.aabb
        }
    open val center: Vec2 get() = worldBounds.center

    /** Draws the part in world space. [t] is the animation clock in seconds. */
    abstract fun draw(p: Painter, t: Double)

    /** Draws an icon of this part fitted into a [size] x [size] box at the origin. */
    open fun drawIcon(p: Painter, size: Double) {
        val s = (size * 0.86) / maxOf(w, h)
        p.save()
        p.translate((size - w * s) / 2 - x * s, (size - h * s) / 2 - y * s)
        p.scale(s, s)
        draw(p, 0.0)
        p.restore()
    }

    /** True if a body belongs to this part. */
    fun owns(b: Body) = b.owner === this
    override fun toString() = "${type.name}#$index@($x,$y)"
}

/** Small drawing helpers shared by parts. */
object Draw {
    fun outlinedCircle(p: Painter, cx: Double, cy: Double, r: Double, fill: Int, line: Double = Style.LINE) {
        p.fillCircle(cx, cy, r, fill)
        p.strokeCircle(cx, cy, r, Style.OUTLINE, line)
    }
    fun outlinedRoundRect(p: Painter, x: Double, y: Double, w: Double, h: Double, rad: Double, fill: Int, line: Double = Style.LINE) {
        p.fillRoundRect(x, y, w, h, rad, fill)
        p.strokeRoundRect(x, y, w, h, rad, Style.OUTLINE, line)
    }
    fun outlinedRect(p: Painter, x: Double, y: Double, w: Double, h: Double, fill: Int, line: Double = Style.LINE) {
        p.fillRect(x, y, w, h, fill)
        p.strokeRect(x, y, w, h, Style.OUTLINE, line)
    }
    fun outlinedPath(p: Painter, path: Path, fill: Int, line: Double = Style.LINE) {
        p.fillPath(path, fill)
        p.strokePath(path, Style.OUTLINE, line)
    }
    /** Glossy ball: radial highlight, outline and a soft shadow ellipse below. */
    fun ball(p: Painter, cx: Double, cy: Double, r: Double, color: Int, dark: Int, angle: Double = 0.0, marks: ((Painter) -> Unit)? = null) {
        p.gradientCircle(cx, cy, r, color, dark, -r * 0.35, -r * 0.35)
        if (marks != null) {
            p.save(); p.translate(cx, cy); p.rotate(angle); marks(p); p.restore()
        }
        p.fillCircle(cx - r * 0.35, cy - r * 0.4, r * 0.22, 0x99FFFFFF.toInt())
        p.strokeCircle(cx, cy, r, Style.OUTLINE, Style.LINE)
    }
    fun shadow(p: Painter, cx: Double, cy: Double, rx: Double, ry: Double) {
        p.fillOval(cx - rx, cy - ry, rx * 2, ry * 2, Style.SHADOW)
    }
}
