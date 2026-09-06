package tim.core.physics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Reported once per step for each pair of solid bodies that touched, with the largest normal impulse seen. */
class ContactEvent(val a: Body, val b: Body, val normal: Vec2, val point: Vec2, var impulse: Double, var relativeSpeed: Double)

/** Reported once per step for each sensor body overlapping another body. */
class SensorEvent(val sensor: Body, val other: Body)

/**
 * Rope constraint: total path length from [a]'s anchor through [via] points to [b]'s anchor
 * must not exceed [maxLength]. Either body may be static (anchor to a hook on the wall).
 */
class Rope(
    val a: Body,
    val anchorA: Vec2,
    val b: Body,
    val anchorB: Vec2,
    val via: List<Vec2> = emptyList(),
    var maxLength: Double,
    val owner: Any? = null,
) {
    var enabled = true
    fun pointA() = a.pos + anchorA.rotated(a.angle)
    fun pointB() = b.pos + anchorB.rotated(b.angle)
    fun points(): List<Vec2> = buildList { add(pointA()); addAll(via); add(pointB()) }
    fun length(): Double {
        val pts = points()
        var l = 0.0
        for (i in 0 until pts.size - 1) l += pts[i].distanceTo(pts[i + 1])
        return l
    }
    val taut: Boolean get() = enabled && length() >= maxLength - 0.5
}

/**
 * Deterministic fixed-timestep 2D physics world. Bodies never rotate dynamically (matching the
 * sprite-based feel of the original game); kinematic bodies may be rotated by their owners.
 */
class World(
    var gravity: Vec2 = Vec2(0.0, DEFAULT_GRAVITY),
    val width: Double,
    val height: Double,
) {
    companion object {
        /**
         * The original engine accelerates a bowling ball by 0.53 px per 30 Hz tick squared,
         * i.e. about 480 px/s^2 on a playfield of similar size to ours.
         */
        const val DEFAULT_GRAVITY = 480.0
        const val STEP = 1.0 / 60.0
        const val SUBSTEPS = 4
        /** Terminal speed; the original clamps each axis at about 19 px per tick (570 px/s). */
        const val MAX_SPEED = 720.0
        const val REST_SPEED = 6.0
        const val RESTITUTION_THRESHOLD = 40.0
        const val SLOP = 0.4
        const val CORRECTION = 0.6
        const val ITERATIONS = 8
    }

    val bodies = ArrayList<Body>()
    val ropes = ArrayList<Rope>()
    val contactEvents = ArrayList<ContactEvent>()
    val sensorEvents = ArrayList<SensorEvent>()
    /** Fraction of normal air pressure (1.0 = normal). Affects drag and buoyancy. */
    var airPressure = 1.0
    var time = 0.0
    var stepCount = 0
    private val manifolds = ArrayList<Manifold>()

    fun add(body: Body): Body { bodies.add(body); body.updateCache(); return body }
    fun remove(body: Body) { bodies.remove(body) }
    fun add(rope: Rope): Rope { ropes.add(rope); return rope }
    fun remove(rope: Rope) { ropes.remove(rope) }

    /** Adds four static walls enclosing the playfield. */
    fun addBounds(thickness: Double = 200.0, owner: Any? = null) {
        val t = thickness
        add(Body(PolygonShape.rect(-t, -t, width + t, 0.0), Vec2.ZERO, BodyKind.STATIC, owner = owner, tag = "bound-top", friction = 0.5))
        add(Body(PolygonShape.rect(-t, height, width + t, height + t), Vec2.ZERO, BodyKind.STATIC, owner = owner, tag = "bound-bottom", friction = 0.5))
        add(Body(PolygonShape.rect(-t, 0.0, 0.0, height), Vec2.ZERO, BodyKind.STATIC, owner = owner, tag = "bound-left", friction = 0.5))
        add(Body(PolygonShape.rect(width, 0.0, width + t, height), Vec2.ZERO, BodyKind.STATIC, owner = owner, tag = "bound-right", friction = 0.5))
    }

    /** Advance the world by one fixed step of [STEP] seconds. */
    fun step() {
        contactEvents.clear()
        sensorEvents.clear()
        val eventIndex = HashMap<Long, ContactEvent>()
        val sensorIndex = HashSet<Long>()
        val dt = STEP / SUBSTEPS
        for (b in bodies) { b.grounded = false; b.groundedOn = null }
        repeat(SUBSTEPS) {
            substep(dt, eventIndex, sensorIndex)
        }
        for (b in bodies) {
            if (b.isDynamic) {
                b.force = Vec2.ZERO
                if (b.vel.length < REST_SPEED && (b.grounded || b.gravityScale == 0.0)) b.restSteps++ else b.restSteps = 0
                if (b.shape is CircleShape) {
                    // visual rolling spin: rolling right on top of a surface is clockwise on screen
                    if (b.grounded) b.angVel = b.vel.x / b.shape.radius
                    b.angle += b.angVel * STEP
                }
            }
        }
        time += STEP
        stepCount++
    }

    private fun substep(dt: Double, eventIndex: HashMap<Long, ContactEvent>, sensorIndex: HashSet<Long>) {
        // 1. integrate velocities
        for (b in bodies) {
            if (!b.enabled) continue
            if (b.isDynamic) {
                var v = b.vel + (gravity * b.gravityScale + b.force * b.invMass) * dt
                if (b.linearDamping > 0) v = v * max(0.0, 1.0 - b.linearDamping * dt)
                val sp = v.length
                if (sp > MAX_SPEED) v = v * (MAX_SPEED / sp)
                b.vel = v
            } else if (b.kind == BodyKind.KINEMATIC) {
                if (b.follow != null) b.syncFollow()
                else { b.pos = b.pos + b.vel * dt; b.angle += b.angVel * dt }
            }
            b.updateCache()
        }
        // 2. contacts
        manifolds.clear()
        val n = bodies.size
        for (i in 0 until n) {
            val a = bodies[i]
            if (!a.enabled) continue
            for (j in i + 1 until n) {
                val b = bodies[j]
                if (!b.enabled) continue
                if (!a.isDynamic && !b.isDynamic && !(a.isSensor || b.isSensor)) continue
                if (!a.isDynamic && !b.isDynamic && a.kind == BodyKind.STATIC && b.kind == BodyKind.STATIC) continue
                if (!a.canCollideWith(b)) continue
                if (!a.aabb.overlaps(b.aabb)) continue
                val m = Collision.collide(a, b) ?: continue
                if (a.isSensor || b.isSensor) {
                    val sensor = if (a.isSensor) a else b
                    val other = if (a.isSensor) b else a
                    val key = pairKey(sensor, other)
                    if (sensorIndex.add(key)) sensorEvents.add(SensorEvent(sensor, other))
                    continue
                }
                val rv = relativeVelocity(m)
                m.preVn = rv dot m.normal
                var e = min(a.restitution, b.restitution) + max(a.bounceBoost, b.bounceBoost)
                if (-m.preVn < RESTITUTION_THRESHOLD) e = 0.0
                m.restitution = min(e, 1.5)
                m.friction = contactFriction(a, b)
                manifolds.add(m)
                // grounded bookkeeping: normal points a -> b
                if (a.isDynamic && m.normal.y > 0.5) { a.grounded = true; a.groundedOn = b }
                if (b.isDynamic && m.normal.y < -0.5) { b.grounded = true; b.groundedOn = a }
            }
        }
        // 3. solve velocities
        repeat(ITERATIONS) { iter ->
            for (m in manifolds) solveContact(m, iter == 0)
            for (r in ropes) if (r.enabled) solveRope(r, dt)
        }
        // 4. integrate positions
        for (b in bodies) if (b.isDynamic && b.enabled) b.pos = b.pos + b.vel * dt
        // 5. positional correction
        for (m in manifolds) correctPosition(m)
        for (r in ropes) if (r.enabled) correctRope(r)
        for (b in bodies) {
            val f = b.follow
            if (f != null) b.syncFollow()
            if (b.isDynamic || f != null) b.updateCache()
        }
        // 6. events
        for (m in manifolds) {
            val key = pairKey(m.a, m.b)
            val ev = eventIndex[key]
            val rel = -m.preVn
            if (ev == null) {
                val e = ContactEvent(m.a, m.b, m.normal, m.point, m.normalImpulse, rel)
                eventIndex[key] = e
                contactEvents.add(e)
            } else {
                if (m.normalImpulse > ev.impulse) ev.impulse = m.normalImpulse
                if (rel > ev.relativeSpeed) ev.relativeSpeed = rel
            }
        }
    }

    /**
     * Circles roll: unless the surface is a conveyor (which grips), use the circle's rolling
     * resistance instead of sliding friction so balls accelerate realistically down ramps.
     */
    private fun contactFriction(a: Body, b: Body): Double {
        val ca = a.shape is CircleShape
        val cb = b.shape is CircleShape
        if (!ca && !cb) return sqrt(a.friction * b.friction)
        if (a.surfaceSpeed != 0.0 || b.surfaceSpeed != 0.0 || a.gripsCircles || b.gripsCircles) return sqrt(a.friction * b.friction)
        return when {
            ca && cb -> max(a.rollingFriction, b.rollingFriction)
            ca -> a.rollingFriction
            else -> b.rollingFriction
        }
    }

    private fun pairKey(a: Body, b: Body): Long {
        val ia = bodies.indexOf(a).toLong()
        val ib = bodies.indexOf(b).toLong()
        return if (ia < ib) (ia shl 32) or ib else (ib shl 32) or ia
    }

    /** Velocity of b relative to a at the contact point, including surface (conveyor) motion. */
    private fun relativeVelocity(m: Manifold): Vec2 {
        val a = m.a; val b = m.b
        var va = a.velocityAt(m.point)
        var vb = b.velocityAt(m.point)
        // surface speed: tangent is the outward normal rotated 90 degrees, so a belt loops around the body.
        if (a.surfaceSpeed != 0.0) va = va + m.normal.perp() * a.surfaceSpeed
        if (b.surfaceSpeed != 0.0) vb = vb + (-m.normal).perp() * b.surfaceSpeed
        return vb - va
    }

    private fun solveContact(m: Manifold, first: Boolean) {
        val a = m.a; val b = m.b
        val invSum = a.invMass + b.invMass
        if (invSum <= 0) return
        val n = m.normal
        var rv = relativeVelocity(m)
        val vn = rv dot n
        // normal impulse with restitution target
        val target = -m.restitution * m.preVn
        var dj = -(vn - target) / invSum
        val old = m.normalImpulse
        m.normalImpulse = max(0.0, old + dj)
        dj = m.normalImpulse - old
        val jn = n * dj
        if (a.isDynamic) a.vel = a.vel - jn * a.invMass
        if (b.isDynamic) b.vel = b.vel + jn * b.invMass
        // friction
        rv = relativeVelocity(m)
        val t = (rv - n * (rv dot n))
        val tl = t.length
        if (tl > 1e-9) {
            val tn = t / tl
            var djt = -(rv dot tn) / invSum
            val maxF = m.friction * m.normalImpulse
            val oldT = m.tangentImpulse
            m.tangentImpulse = min(maxF, max(-maxF, oldT + djt))
            djt = m.tangentImpulse - oldT
            val jt = tn * djt
            if (a.isDynamic) a.vel = a.vel - jt * a.invMass
            if (b.isDynamic) b.vel = b.vel + jt * b.invMass
        }
    }

    private fun correctPosition(m: Manifold) {
        val a = m.a; val b = m.b
        val invSum = a.invMass + b.invMass
        if (invSum <= 0) return
        val pen = max(m.penetration - SLOP, 0.0)
        if (pen <= 0) return
        val corr = m.normal * (pen * CORRECTION / invSum)
        if (a.isDynamic) a.pos = a.pos - corr * a.invMass
        if (b.isDynamic) b.pos = b.pos + corr * b.invMass
    }

    private fun solveRope(r: Rope, dt: Double) {
        val len = r.length()
        if (len <= r.maxLength) return
        val pts = r.points()
        val pa = pts[0]; val pb = pts[pts.size - 1]
        val nextA = pts[1]; val prevB = pts[pts.size - 2]
        val da = (nextA - pa).normalized()
        val db = (prevB - pb).normalized()
        val invSum = r.a.invMass + r.b.invMass
        if (invSum <= 0) return
        // rate at which the rope is lengthening
        val rate = -(r.a.vel dot da) - (r.b.vel dot db)
        val bias = (len - r.maxLength) * 0.2 / dt
        if (rate + bias <= 0) return
        val lambda = (rate + bias) / invSum
        if (r.a.isDynamic) r.a.vel = r.a.vel + da * (lambda * r.a.invMass)
        if (r.b.isDynamic) r.b.vel = r.b.vel + db * (lambda * r.b.invMass)
    }

    private fun correctRope(r: Rope) {
        val len = r.length()
        val excess = len - r.maxLength
        if (excess <= 0) return
        val pts = r.points()
        val pa = pts[0]; val pb = pts[pts.size - 1]
        val da = (pts[1] - pa).normalized()
        val db = (pts[pts.size - 2] - pb).normalized()
        val invSum = r.a.invMass + r.b.invMass
        if (invSum <= 0) return
        val k = excess * 0.8 / invSum
        if (r.a.isDynamic) r.a.pos = r.a.pos + da * (k * r.a.invMass)
        if (r.b.isDynamic) r.b.pos = r.b.pos + db * (k * r.b.invMass)
    }

    /** True when every enabled dynamic body has been resting for at least [steps] steps. */
    fun allAtRest(steps: Int = 45): Boolean =
        bodies.all { !it.isDynamic || !it.enabled || it.restSteps >= steps || it.gravityScale == 0.0 && it.vel.length < REST_SPEED }

    fun bodyAt(p: Vec2): Body? = bodies.firstOrNull { it.enabled && it.aabb.contains(p) && Collision.contains(it, p) }

    fun bodiesIn(box: AABB): List<Body> = bodies.filter { it.enabled && it.aabb.overlaps(box) }

    fun maxSpeed(): Double = bodies.filter { it.isDynamic }.maxOfOrNull { it.vel.length } ?: 0.0

    @Suppress("unused")
    private fun sign(x: Double) = if (x < 0) -1.0 else 1.0
}
