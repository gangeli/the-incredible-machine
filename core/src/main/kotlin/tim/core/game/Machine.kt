package tim.core.game

import tim.core.physics.AABB
import tim.core.physics.Body
import tim.core.physics.Rope
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Painter
import tim.core.game.parts.PartFactory

/** Transient visual effect (explosion puff, balloon pop, sparkle, splash). */
class Effect(val kind: Kind, val pos: Vec2, val life: Double, val size: Double = 1.0, val color: Int = 0) {
    enum class Kind { EXPLOSION, POP, SPARKLE, PUFF, RING, STARBURST }
    var age = 0.0
    val done get() = age >= life
    val t get() = (age / life).coerceIn(0.0, 1.0)
}

/** A rope as placed on the board, with its physical constraint. */
class RopeLink(val link: Link, val from: Part, val to: Part, val pulleys: List<Part>, val rope: Rope) {
    var cut = false
}

/**
 * A running (or freshly built) machine: the physics world plus all part instances built from a
 * [Board]. Building is deterministic, so restarting simply rebuilds the machine.
 */
class Machine(val board: Board, val width: Double = WIDTH, val height: Double = HEIGHT, gravity: Double = World.DEFAULT_GRAVITY, airPressure: Double = 1.0) {
    companion object {
        const val WIDTH = 640.0
        const val HEIGHT = 400.0
        const val GRID = 8.0
    }

    val world = World(Vec2(0.0, gravity), width, height).also { it.airPressure = airPressure }
    val parts: List<Part>
    /** Parts created while running (cannonballs). */
    val spawned = ArrayList<Part>()
    val allParts: List<Part> get() = if (spawned.isEmpty()) parts else parts + spawned
    val ropes = ArrayList<RopeLink>()
    val belts = ArrayList<Link>()
    val wires = ArrayList<Link>()
    val effects = ArrayList<Effect>()
    /** Sound cues raised during the last step, consumed by the UI layer. */
    val sounds = ArrayList<String>()
    val time: Double get() = world.time
    var steps = 0
        private set

    /** Whether every moving thing has settled (or left), so the run can be considered over. */
    val settled: Boolean get() = world.allAtRest(60) && effects.isEmpty()

    init {
        parts = board.all.mapIndexed { i, pl -> PartFactory.create(pl, i) }
        for (p in parts) { p.machine = this; p.build(world); p.built = true }
        for (l in board.links) when (l.kind) {
            LinkKind.ROPE -> addRope(l)
            LinkKind.BELT -> belts.add(l)
            LinkKind.WIRE -> { wires.add(l); parts.getOrNull(l.to)?.hasPowerInput = true }
        }
        refreshPower()
    }

    private fun addRope(l: Link) {
        val a = parts.getOrNull(l.from) ?: return
        val b = parts.getOrNull(l.to) ?: return
        val aa = a.ropeAnchor() ?: return
        val ba = b.ropeAnchor() ?: return
        val pulleys = l.via.mapNotNull { parts.getOrNull(it) }.filter { it.pulleyPoint() != null }
        val via = pulleys.map { it.pulleyPoint()!! }
        val bodyA = a.ropeBody ?: staticAnchor(a.local(aa.x, aa.y))
        val bodyB = b.ropeBody ?: staticAnchor(b.local(ba.x, ba.y))
        val anchorA = a.local(aa.x, aa.y) - bodyA.pos
        val anchorB = b.local(ba.x, ba.y) - bodyB.pos
        val rope = Rope(bodyA, anchorA, bodyB, anchorB, via, maxLength = 0.0, owner = this)
        rope.maxLength = rope.length() + l.slack
        world.add(rope)
        ropes.add(RopeLink(l, a, b, pulleys, rope))
    }

    private fun staticAnchor(p: Vec2): Body {
        val b = Body(tim.core.physics.PolygonShape.box(1.0, 1.0), p, tim.core.physics.BodyKind.STATIC, owner = this, tag = "anchor")
        b.enabled = true
        b.category = tim.core.physics.Category.ROPE
        b.mask = 0
        world.add(b)
        return b
    }

    fun spawn(pl: Placement): Part {
        val part = PartFactory.create(pl, -1 - spawned.size)
        part.machine = this
        part.build(world)
        part.built = true
        spawned.add(part)
        return part
    }

    fun ropesOf(part: Part): List<RopeLink> = ropes.filter { !it.cut && (it.from === part || it.to === part) }

    /** Cut every rope whose polyline passes through [box]. Returns true if any rope was cut. */
    fun cutRopesIn(box: AABB): Boolean {
        var any = false
        for (r in ropes) {
            if (r.cut) continue
            val pts = r.rope.points()
            for (i in 0 until pts.size - 1) if (segmentHitsBox(pts[i], pts[i + 1], box)) { cutRope(r); any = true; break }
        }
        return any
    }

    fun cutRope(r: RopeLink) {
        if (r.cut) return
        r.cut = true
        r.rope.enabled = false
        world.remove(r.rope)
        sounds.add("snip")
    }

    private fun segmentHitsBox(a: Vec2, b: Vec2, box: AABB): Boolean {
        if (box.contains(a) || box.contains(b)) return true
        // Liang-Barsky clipping
        var t0 = 0.0; var t1 = 1.0
        val dx = b.x - a.x; val dy = b.y - a.y
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(a.x - box.minX, box.maxX - a.x, a.y - box.minY, box.maxY - a.y)
        for (i in 0 until 4) {
            if (p[i] == 0.0) { if (q[i] < 0) return false }
            else {
                val t = q[i] / p[i]
                if (p[i] < 0) { if (t > t1) return false; if (t > t0) t0 = t }
                else { if (t < t0) return false; if (t < t1) t1 = t }
            }
        }
        return true
    }

    /** Recompute electric power for every part from outlets, switches and wires. */
    fun refreshPower() {
        // iterate a few times so chains (outlet -> switch -> fan) settle
        repeat(4) {
            for (p in parts) if (p.hasPowerInput) {
                p.powered = wires.any { it.to == p.index && (parts.getOrNull(it.from)?.powerOutput == true) }
            }
        }
        for (l in belts) {
            val src = parts.getOrNull(l.from) ?: continue
            val dst = parts.getOrNull(l.to) ?: continue
            dst.setBeltDrive(src.spinOutput, 1)
        }
    }

    fun step() {
        sounds.clear()
        refreshPower()
        for (p in allParts) p.preStep()
        world.step()
        for (ev in world.contactEvents) {
            (ev.a.owner as? Part)?.onContact(ev.a, ev.b, ev)
            (ev.b.owner as? Part)?.onContact(ev.b, ev.a, ev)
        }
        for (ev in world.sensorEvents) {
            (ev.sensor.owner as? Part)?.onSensor(ev.sensor, ev.other)
            if (ev.other.isSensor) (ev.other.owner as? Part)?.onSensor(ev.other, ev.sensor)
        }
        for (p in allParts) p.postStep()
        // things that leave the playfield far enough are gone for good (the original clamps far off-screen)
        for (b in world.bodies) if (b.isDynamic && b.enabled && (b.pos.y > height + 160 || b.pos.y < -400 || b.pos.x < -160 || b.pos.x > width + 160)) b.enabled = false
        val it = effects.iterator()
        while (it.hasNext()) { val e = it.next(); e.age += World.STEP; if (e.done) it.remove() }
        steps++
    }

    fun explode(center: Vec2, radius: Double, strength: Double) {
        effects.add(Effect(Effect.Kind.EXPLOSION, center, 0.6, radius / 40.0))
        sounds.add("boom")
        for (b in world.bodies) {
            if (!b.isDynamic || !b.enabled) continue
            val d = b.pos - center
            val dist = d.length
            if (dist > radius) continue
            val falloff = 1.0 - dist / radius
            val n = if (dist < 1e-6) Vec2(0.0, -1.0) else d / dist
            // impulse is applied as a velocity change so heavy and light things both move (like the original)
            val kick = strength * (0.35 + 0.65 * falloff)
            b.vel = b.vel + n * (kick / (1.0 + b.mass * 0.05))
            b.restSteps = 0
        }
        for (p in allParts) {
            val c = p.center
            if (c.distanceTo(center) <= radius + maxOf(p.w, p.h) / 2) p.onExplosion(center, radius)
        }
        cutRopesIn(AABB(center.x - radius * 0.4, center.y - radius * 0.4, center.x + radius * 0.4, center.y + radius * 0.4))
    }

    fun partOf(b: Body): Part? = b.owner as? Part

    fun draw(p: Painter, t: Double) {
        for (part in allParts) part.draw(p, t)
        drawRopes(p)
        drawBelts(p)
        for (e in effects) Effects.draw(p, e)
    }

    private fun drawRopes(p: Painter) {
        for (r in ropes) {
            if (r.cut) continue
            val pts = r.rope.points()
            val slack = (r.rope.maxLength - r.rope.length()).coerceAtLeast(0.0)
            for (i in 0 until pts.size - 1) {
                val a = pts[i]; val b = pts[i + 1]
                val sag = if (pts.size == 2) slack * 0.5 else if (i == 0 || i == pts.size - 2) slack * 0.25 else 0.0
                val path = tim.core.render.Path()
                path.moveTo(a.x, a.y)
                path.quadTo((a.x + b.x) / 2, (a.y + b.y) / 2 + sag, b.x, b.y)
                p.strokePath(path, Style.OUTLINE, 3.0)
                p.strokePath(path, Style.WOOD, 1.6)
            }
        }
    }

    private fun drawBelts(p: Painter) {
        for (l in belts) {
            val a = parts.getOrNull(l.from)?.beltHub() ?: continue
            val b = parts.getOrNull(l.to)?.beltHub() ?: continue
            val d = (b - a).normalized().perp() * 5.0
            p.line(a.x + d.x, a.y + d.y, b.x + d.x, b.y + d.y, Style.OUTLINE, 2.5)
            p.line(a.x - d.x, a.y - d.y, b.x - d.x, b.y - d.y, Style.OUTLINE, 2.5)
        }
    }
}

object Effects {
    fun draw(p: Painter, e: Effect) {
        val t = e.t
        when (e.kind) {
            Effect.Kind.EXPLOSION -> {
                val r = 40.0 * e.size * (0.3 + 0.7 * t)
                p.alpha = 1.0 - t
                p.fillCircle(e.pos.x, e.pos.y, r, Style.FLAME)
                p.fillCircle(e.pos.x, e.pos.y, r * 0.6, Style.FLAME_CORE)
                p.fillCircle(e.pos.x, e.pos.y, r * 0.3, Style.WHITE)
                p.alpha = 1.0
            }
            Effect.Kind.POP -> {
                p.alpha = 1.0 - t
                val r = 16.0 * e.size * (1.0 + t * 1.5)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    p.line(e.pos.x + StrictMath.cos(a) * r * 0.4, e.pos.y + StrictMath.sin(a) * r * 0.4,
                        e.pos.x + StrictMath.cos(a) * r, e.pos.y + StrictMath.sin(a) * r, e.color, 2.5)
                }
                p.alpha = 1.0
            }
            Effect.Kind.SPARKLE, Effect.Kind.STARBURST -> {
                p.alpha = 1.0 - t
                val r = 20.0 * e.size * (0.5 + t)
                for (i in 0 until 6) {
                    val a = i * Math.PI / 3 + t * 2
                    p.fillCircle(e.pos.x + StrictMath.cos(a) * r, e.pos.y + StrictMath.sin(a) * r, 3.0 * e.size, Style.YELLOW)
                }
                p.alpha = 1.0
            }
            Effect.Kind.PUFF -> {
                p.alpha = (1.0 - t) * 0.7
                p.fillCircle(e.pos.x, e.pos.y - t * 20, 10.0 * e.size * (0.5 + t), Style.GREY_LIGHT)
                p.alpha = 1.0
            }
            Effect.Kind.RING -> {
                p.alpha = 1.0 - t
                p.strokeCircle(e.pos.x, e.pos.y, 20.0 * e.size * (0.2 + t), Style.YELLOW, 3.0)
                p.alpha = 1.0
            }
        }
    }
}
