package tim.core.game.parts

import tim.core.game.Activatable
import tim.core.game.Container
import tim.core.game.Draw
import tim.core.game.Effect
import tim.core.game.Part
import tim.core.game.PartType
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.physics.AABB
import tim.core.physics.Body
import tim.core.physics.BodyKind
import tim.core.physics.Category
import tim.core.physics.CircleShape
import tim.core.physics.ContactEvent
import tim.core.physics.PolygonShape
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.Path
import kotlin.math.abs

/** Normal pointing from [self]'s body towards [other] for a contact event. */
internal fun ContactEvent.normalFrom(self: Body): Vec2 = if (a === self) normal else -normal

/**
 * Seesaw (teeter-totter). The plank is kinematic and tips as a short animation when something
 * lands on the raised end; parts on the rising end are launched with a speed that depends on their
 * mass, just like the original engine.
 */
class Seesaw(placement: Placement, index: Int) : Part(placement, index) {
    companion object {
        const val MAX_ANGLE = 0.24 // radians (~14 degrees)
        const val TIP_TIME = 0.22
        fun launchSpeed(mass: Double): Double = when {
            mass < 0.2 -> 420.0
            mass < 0.6 -> 400.0
            mass < 1.0 -> 370.0
            mass < 2.1 -> 340.0
            mass < 12.0 -> 300.0
            mass < 15.0 -> 270.0
            else -> 240.0
        }
    }
    lateinit var plank: Body
    /** Invisible anchor riding on the end that starts low; a rope tied here pulls that end up. */
    lateinit var ropeEnd: Body
    /** -1: left end down, +1: right end down. Default (unflipped) has the left end down; flipping swaps it. */
    var tilt = -1
    private var targetTilt = -1
    private var ropedSide = -1
    val pivot: Vec2 get() = Vec2(x + w / 2, y + 20)
    val tipping get() = plank.angVel != 0.0

    override fun build(world: World) {
        tilt = if (flipped) 1 else -1
        targetTilt = tilt
        val base = Body(PolygonShape(listOf(Vec2(x + w / 2, y + 20), Vec2(x + w / 2 + 12, y + h), Vec2(x + w / 2 - 12, y + h))), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "seesaw-base")
        bodies.add(world.add(base))
        plank = Body(PolygonShape.rect(-w / 2, -6.0, w / 2, 0.0), pivot, BodyKind.KINEMATIC, friction = 0.35, owner = this, tag = "seesaw-plank")
        plank.angle = -tilt * MAX_ANGLE * -1.0 // tilt -1 (left down) => angle positive? see below
        // In y-down coords a positive angle rotates the +x end downward, so right-end-down is +MAX.
        plank.angle = tilt * MAX_ANGLE
        // the plank grips balls so they rest on the low end instead of rolling off (as in the original)
        plank.gripsCircles = true
        bodies.add(world.add(plank))
        ropedSide = tilt
        ropeEnd = Body(PolygonShape.box(2.0, 2.0), pivot, BodyKind.KINEMATIC, owner = this, tag = "seesaw-rope-end")
        ropeEnd.category = Category.ROPE
        ropeEnd.mask = 0
        ropeEnd.follow = plank
        ropeEnd.followOffset = Vec2(ropedSide * (w / 2 - 4), -6.0)
        ropeEnd.followRotates = true
        ropeEnd.syncFollow()
        bodies.add(world.add(ropeEnd))
    }

    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (self !== plank || !other.isDynamic || tipping) return
        val n = ev.normalFrom(self)
        if (n.y > -0.3) return // must land on top of the plank
        val rel = ev.point.x - pivot.x
        if (abs(rel) < 6) return // dead zone over the fulcrum
        val side = if (rel < 0) -1 else 1
        if (side == tilt) return // already down on that side
        // heavy enough to matter: anything moving or resting on the high end tips it
        startTip(side)
    }

    fun startTip(side: Int) {
        if (side == tilt || tipping) return
        targetTilt = side
        plank.angVel = side * (2 * MAX_ANGLE / TIP_TIME)
        machine.sounds.add("creak")
        // launch parts resting on the rising side
        val risingSide = -side
        for (b in machine.world.bodies) {
            if (!b.isDynamic || !b.enabled) continue
            val onPlank = b.groundedOn === plank || (b.aabb.maxY >= plank.aabb.minY - 2 && b.aabb.minY <= plank.aabb.maxY && b.pos.x > x - 4 && b.pos.x < x + w + 4 && b.pos.y < pivot.y)
            if (!onPlank) continue
            val rel = b.pos.x - pivot.x
            if (rel * risingSide <= 0) continue
            val speed = launchSpeed(b.mass)
            b.vel = Vec2(risingSide * speed / 4, -speed)
            b.restSteps = 0
        }
    }

    override fun preStep() {
        if (!tipping) return
        val target = targetTilt * MAX_ANGLE
        val remaining = target - plank.angle
        if (remaining * targetTilt <= 0.0001) {
            plank.angle = target
            plank.angVel = 0.0
            tilt = targetTilt
        }
    }

    /** Placement-local coordinates of the rope end (unflipped frame, so [local] maps it back). */
    override fun ropeAnchor(): Vec2 {
        if (!built) return Vec2(if (flipped) w - 4 else 4.0, 26.0)
        val px = ropeEnd.pos.x - x
        return Vec2(if (flipped) w - px else px, ropeEnd.pos.y - y)
    }
    override val ropeBody: Body? get() = if (built) ropeEnd else null
    /** A pull lifts the roped (low) end, so the other end drops and whatever sat on the roped end flies. */
    override fun onRopePull() { startTip(-ropedSide) }

    override fun draw(p: Painter, t: Double) {
        val pv = pivot
        // base
        val base = Path.polygon(pv.x, pv.y, pv.x + 14, y + h, pv.x - 14, y + h)
        p.fillPath(base, Style.STEEL)
        p.strokePath(base, Style.OUTLINE, Style.LINE)
        // plank
        val ang = if (built) plank.angle else (if (flipped) 1 else -1) * MAX_ANGLE
        p.save()
        p.translate(pv.x, pv.y)
        p.rotate(ang)
        p.fillRoundRect(-w / 2, -6.0, w, 6.0, 2.0, Style.WOOD)
        p.fillRect(-w / 2 + 3, -2.5, w - 6, 1.0, Style.WOOD_DARK)
        p.strokeRoundRect(-w / 2, -6.0, w, 6.0, 2.0, Style.OUTLINE, Style.LINE)
        p.fillRect(-w / 2, -8.0, 6.0, 4.0, Style.RED)
        p.fillRect(w / 2 - 6, -8.0, 6.0, 4.0, Style.RED)
        p.restore()
        p.fillCircle(pv.x, pv.y, 3.0, Style.OUTLINE)
    }
}

/** Trampoline: things landing on the mat bounce back up with their impact speed plus a little extra. */
class Trampoline(placement: Placement, index: Int) : Part(placement, index) {
    lateinit var mat: Body
    private var wobble = 0.0

    override fun build(world: World) {
        mat = Body(PolygonShape.rect(x + 4, y + 4, x + w - 4, y + 12), Vec2.ZERO, BodyKind.STATIC, restitution = 0.4, friction = 0.4, owner = this, tag = "trampoline")
        bodies.add(world.add(mat))
        val legs = Body(PolygonShape.rect(x + 8, y + 12, x + w - 8, y + h), Vec2.ZERO, BodyKind.STATIC, restitution = 0.2, friction = 0.4, owner = this, tag = "trampoline-legs")
        bodies.add(world.add(legs))
    }

    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (self !== mat || !other.isDynamic) return
        val n = ev.normalFrom(self)
        if (n.y > -0.5) return
        val speed = ev.relativeSpeed
        if (speed < 25) return
        val vx = if (abs(other.vel.x) > 60) other.vel.x * 0.5 else other.vel.x
        other.vel = Vec2(vx, -(speed + 60.0))
        other.restSteps = 0
        wobble = 1.0
        machine.sounds.add("boing")
    }

    override fun postStep() { wobble *= 0.85 }

    override fun draw(p: Painter, t: Double) {
        val dip = wobble * 4
        // legs: two splayed pairs
        for (lx in listOf(x + 12, x + w - 12)) {
            p.line(lx, y + 12, lx - 8, y + h, Style.OUTLINE, 4.0)
            p.line(lx, y + 12, lx + 8, y + h, Style.OUTLINE, 4.0)
            p.line(lx, y + 12, lx - 8, y + h, Style.STEEL, 2.0)
            p.line(lx, y + 12, lx + 8, y + h, Style.STEEL, 2.0)
        }
        // frame
        p.fillRoundRect(x, y + 2, w, 12.0, 6.0, Style.GREY_DARK)
        // springs
        for (side in listOf(-1.0, 1.0)) {
            val sx0 = if (side < 0) x + 4 else x + w - 4
            val sx1 = if (side < 0) x + 12 else x + w - 12
            val zig = Path().moveTo(sx0, y + 8)
            for (k in 1..4) zig.lineTo(sx0 + (sx1 - sx0) * k / 4, y + 8 + (if (k % 2 == 1) -2.5 else 2.5))
            p.strokePath(zig, Style.STEEL_LIGHT, 1.2)
        }
        // mat (sags a little when hit)
        val mat = Path()
        mat.moveTo(x + 12, y + 5); mat.lineTo(x + w - 12, y + 5)
        mat.quadTo(x + w / 2, y + 11 + dip, x + 12, y + 11)
        mat.close()
        p.fillPath(mat, Style.BLUE)
        p.line(x + 14, y + 6.5, x + w - 14, y + 6.5, Colors.withAlpha(Style.WHITE, 0.45), 1.2)
        p.strokeRoundRect(x, y + 2, w, 12.0, 6.0, Style.OUTLINE, Style.LINE)
    }
}

/** Conveyor belt: a solid block whose surface drags things along. Flip reverses direction. */
class Conveyor(placement: Placement, index: Int) : Part(placement, index) {
    companion object { const val SPEED = 200.0 }
    lateinit var body: Body
    private var beltDriven = placement.needsPower
    private var beltRunning = false
    private var beltDir = 1
    init {
        // for a conveyor "needs power" means "needs a belt from a motor"; electricity is only relevant when wired
        hasPowerInput = false
        powered = true
    }
    private var phase = 0.0
    val running: Boolean get() = powered && (!beltDriven || beltRunning)
    /** Belted conveyors turn the way the motor faces; free ones the way they face themselves. */
    val direction: Double get() = if (beltDriven && beltRunning) beltDir.toDouble() else dir

    override fun build(world: World) {
        body = Body(PolygonShape.rect(x, y + 4, x + w, y + h - 4), Vec2.ZERO, BodyKind.STATIC, restitution = 0.1, friction = 0.9, owner = this, tag = "conveyor")
        bodies.add(world.add(body))
    }

    override fun setBeltDrive(running: Boolean, direction: Int) { beltDriven = true; beltRunning = running; beltDir = direction }

    override fun preStep() {
        body.surfaceSpeed = if (running) direction * SPEED else 0.0
    }

    override fun postStep() { if (running) phase += direction * World.STEP * 6 }

    override fun draw(p: Painter, t: Double) {
        val r = h / 2 - 2
        // belt loop
        val belt = Path.roundRect(x, y + 2, w, h - 4, r)
        p.fillPath(belt, Style.GREY_DARK)
        p.strokePath(belt, Style.OUTLINE, Style.LINE)
        // moving stripes
        p.save()
        p.clipRoundRect(x + 1, y + 3, w - 2, h - 6, r)
        val ph = if (built) phase else t * direction * 6
        val spacing = 12.0
        var sx = x - spacing + ((ph * 6) % spacing + spacing) % spacing
        while (sx < x + w + spacing) {
            p.line(sx, y + 3, sx, y + 8, Style.GREY_LIGHT, 2.0)
            p.line(sx + spacing / 2, y + h - 8, sx + spacing / 2, y + h - 3, Style.GREY_LIGHT, 2.0)
            sx += spacing
        }
        p.restore()
        // rollers
        val n = (w / 24).toInt().coerceAtLeast(2)
        for (i in 0 until n) {
            val cx = x + r + 2 + i * (w - 2 * r - 4) / (n - 1)
            p.fillCircle(cx, y + h / 2, r - 3, Style.STEEL_LIGHT)
            p.strokeCircle(cx, y + h / 2, r - 3, Style.OUTLINE, 1.5)
            p.save(); p.translate(cx, y + h / 2); p.rotate(ph)
            p.line(-(r - 5), 0.0, r - 5, 0.0, Style.OUTLINE, 1.5)
            p.restore()
        }
        // direction arrow
        val ax = x + w / 2
        val d = direction
        p.line(ax - 6 * d, y + h / 2, ax + 6 * d, y + h / 2, Style.YELLOW, 2.0)
        p.line(ax + 6 * d, y + h / 2, ax + 2 * d, y + h / 2 - 4, Style.YELLOW, 2.0)
        p.line(ax + 6 * d, y + h / 2, ax + 2 * d, y + h / 2 + 4, Style.YELLOW, 2.0)
    }

    override fun beltHub(): Vec2 = Vec2(x + (if (flipped) w - h / 2 else h / 2), y + h / 2)
}

/** Fan: blows a stream of air in the direction it faces, pushing light things and blowing out candles. */
class Fan(placement: Placement, index: Int) : Part(placement, index) {
    companion object { const val REACH = 176.0; const val FORCE = 110.0 }
    private var spin = 0.0
    val running get() = powered && machine.world.airPressure > 0.05
    fun windZone(): AABB = if (flipped) AABB(x - REACH, y - 4, x, y + h + 4) else AABB(x + w, y - 4, x + w + REACH, y + h + 4)

    override fun build(world: World) {
        val b = Body(PolygonShape.rect(x, y, x + w, y + h), Vec2.ZERO, BodyKind.STATIC, friction = 0.4, owner = this, tag = "fan")
        bodies.add(world.add(b))
    }

    override fun preStep() {
        if (!running) return
        val zone = windZone()
        val pressure = machine.world.airPressure
        for (b in machine.world.bodiesIn(zone)) {
            if (!b.isDynamic || b.owner === this) continue
            val dist = if (flipped) x - b.pos.x else b.pos.x - (x + w)
            val falloff = (1.0 - dist / REACH).coerceIn(0.0, 1.0)
            val accel = minOf(FORCE * pressure / b.mass, 520.0) * (0.4 + 0.6 * falloff)
            b.applyForce(Vec2(dir * accel * b.mass, 0.0))
            b.restSteps = 0
        }
        for (part in machine.parts) if (part is Candle && part.lit && zone.overlaps(part.worldBounds)) part.blowOut()
    }

    override fun postStep() { if (running) spin += 0.5 }

    override fun draw(p: Painter, t: Double) {
        val sp = if (built) spin else t * 8
        // base + stand
        p.fillRoundRect(x + 6, y + h - 6, w - 12, 6.0, 3.0, Style.STEEL)
        p.strokeRoundRect(x + 6, y + h - 6, w - 12, 6.0, 3.0, Style.OUTLINE, Style.LINE)
        p.line(cx, y + h - 6, cx, y + 20, Style.OUTLINE, 3.0)
        // cage (circle) with blades
        val r = 14.0
        val ccx = cx; val ccy = y + 16
        p.fillCircle(ccx, ccy, r, Style.STEEL_LIGHT)
        p.save(); p.translate(ccx, ccy); p.rotate(sp)
        for (i in 0 until 3) {
            p.save(); p.rotate(i * Math.PI * 2 / 3)
            val blade = Path(); blade.moveTo(0.0, 0.0); blade.quadTo(10.0, -8.0, 11.0, 2.0); blade.quadTo(6.0, 6.0, 0.0, 0.0)
            p.fillPath(blade, Style.BLUE); p.strokePath(blade, Style.OUTLINE, 1.0)
            p.restore()
        }
        p.restore()
        p.fillCircle(ccx, ccy, 2.5, Style.OUTLINE)
        p.strokeCircle(ccx, ccy, r, Style.OUTLINE, Style.LINE)
        // wind lines when running
        if (!built || running) {
            p.alpha = 0.5
            for (i in 0 until 3) {
                val wy = ccy - 8 + i * 8
                val phase = ((sp * 3 + i * 7) % 24)
                val sx = if (flipped) x - 6 - phase else x + w + 6 + phase
                p.line(sx, wy, sx + dir * 10, wy, Style.BLUE, 1.5)
            }
            p.alpha = 1.0
        }
    }
}

/** Bucket: an open container that falls, holds balls and can hang from a rope by its handle. */
class Bucket(placement: Placement, index: Int) : Part(placement, index), Container {
    lateinit var bottom: Body
    private val sides = ArrayList<Body>()
    private var inside = ArrayList<Part>()
    private val insideSteps = HashMap<Part, Int>()
    override val contents: List<Part> get() = inside
    val pos: Vec2 get() = if (built) Vec2(bottom.pos.x, bottom.pos.y - (h - 5)) else Vec2(cx, y)

    override fun build(world: World) {
        bottom = Body(PolygonShape.box(w / 2 - 2, 5.0), Vec2(cx, y + h - 5), BodyKind.DYNAMIC, mass = 10.0, restitution = 0.125, friction = 0.6, owner = this, tag = "bucket")
        bodies.add(world.add(bottom))
        for (side in listOf(-1.0, 1.0)) {
            val s = Body(PolygonShape.box(2.5, h / 2 - 2), Vec2(cx + side * (w / 2 - 3), cy), BodyKind.KINEMATIC, restitution = 0.125, friction = 0.4, owner = this, tag = "bucket-side")
            s.follow = bottom
            s.followOffset = s.pos - bottom.pos
            sides.add(s)
            bodies.add(world.add(s))
        }
    }

    override fun postStep() {
        val top = bottom.pos.y - (h - 10)
        val box = AABB(bottom.pos.x - w / 2 + 6, top, bottom.pos.x + w / 2 - 6, bottom.pos.y)
        val now = ArrayList<Part>()
        for (b in machine.world.bodiesIn(box)) {
            if (!b.isDynamic || b.owner === this) continue
            val part = machine.partOf(b) ?: continue
            if (box.contains(b.pos)) now.add(part)
        }
        inside = now
    }

    override fun ropeAnchor() = Vec2(w / 2, 0.0)
    override val ropeBody: Body? get() = bottom
    override fun hangingMass(): Double = bottom.mass + contents.sumOf { it.hangingMass() }
    override val worldBounds: AABB get() = if (built) AABB(bottom.pos.x - w / 2, bottom.pos.y - h + 5, bottom.pos.x + w / 2, bottom.pos.y + 5) else placement.aabb

    override fun draw(p: Painter, t: Double) {
        val b = worldBounds
        val bx = b.minX; val by = b.minY
        // handle
        val handle = Path(); handle.moveTo(bx + 4, by + 8); handle.quadTo(bx + w / 2, by - 10, bx + w - 4, by + 8)
        p.strokePath(handle, Style.OUTLINE, 3.0)
        p.strokePath(handle, Style.STEEL_LIGHT, 1.5)
        // body: slightly tapered
        val body = Path.polygon(bx + 2, by + 6, bx + w - 2, by + 6, bx + w - 6, by + h, bx + 6, by + h)
        p.fillPath(body, Style.STEEL)
        p.fillRect(bx + 2, by + 6, w - 4, 5.0, Style.STEEL_LIGHT)
        p.line(bx + 10, by + 14, bx + 11, by + h - 4, Colors.withAlpha(Style.WHITE, 0.35), 3.0)
        p.strokePath(body, Style.OUTLINE, Style.LINE)
    }
}

/** Cage: falls and traps creatures under it. Balls bounce off its top. */
class Cage(placement: Placement, index: Int) : Part(placement, index) {
    lateinit var body: Body
    override fun build(world: World) {
        body = Body(PolygonShape.box(w / 2, h / 2), Vec2(cx, cy), BodyKind.DYNAMIC, mass = 15.0, restitution = 0.1, friction = 0.6, owner = this, tag = "cage")
        body.mask = Category.ALL and Category.CREATURE.inv()
        bodies.add(world.add(body))
        for (side in listOf(-1.0, 1.0)) {
            val s = Body(PolygonShape.box(2.0, h / 2 - 4), Vec2(cx + side * (w / 2 - 2), cy), BodyKind.KINEMATIC, friction = 0.3, owner = this, tag = "cage-bar")
            s.mask = Category.CREATURE
            s.follow = body
            s.followOffset = s.pos - body.pos
            bodies.add(world.add(s))
        }
    }
    /** Creatures whose centre is inside the cage while it rests on something. */
    fun traps(part: Part): Boolean {
        val b = worldBounds
        val c = part.center
        return body.grounded && c.x > b.minX && c.x < b.maxX && c.y > b.minY && c.y < b.maxY + 2
    }
    override fun ropeAnchor() = Vec2(w / 2, 0.0)
    override val ropeBody: Body? get() = body

    override fun draw(p: Painter, t: Double) {
        val b = worldBounds
        val bx = b.minX; val by = b.minY
        // ring on top
        p.strokeCircle(bx + w / 2, by, 4.0, Style.OUTLINE, 2.0)
        // dome top
        val dome = Path(); dome.moveTo(bx, by + 12); dome.quadTo(bx + w / 2, by - 8, bx + w, by + 12); dome.close()
        p.fillPath(dome, Style.YELLOW)
        p.strokePath(dome, Style.OUTLINE, Style.LINE)
        // bars
        val n = 5
        for (i in 0 until n) {
            val lx = bx + 3 + i * (w - 6) / (n - 1)
            p.line(lx, by + 10, lx, by + h - 2, Style.OUTLINE, 2.5)
            p.line(lx, by + 10, lx, by + h - 2, Style.YELLOW, 1.2)
        }
        // base ring
        p.fillRoundRect(bx, by + h - 4, w, 4.0, 2.0, Style.YELLOW)
        p.strokeRoundRect(bx, by + h - 4, w, 4.0, 2.0, Style.OUTLINE, Style.LINE)
        p.fillRoundRect(bx, by + 10, w, 3.0, 1.5, Style.OUTLINE)
    }
}

/** Candle: a portable flame. Lights fuses, pops balloons and burns ropes; fans blow it out. */
class Candle(placement: Placement, index: Int) : Part(placement, index), Activatable {
    lateinit var body: Body
    lateinit var flame: Body
    var lit = true
    override val activated get() = lit
    val pos: Vec2 get() = if (built) body.pos else Vec2(cx, y + 20)

    override fun build(world: World) {
        body = Body(PolygonShape.box(w / 2, 12.0), Vec2(cx, y + 20), BodyKind.DYNAMIC, mass = 1.2, restitution = 0.1, friction = 0.7, owner = this, tag = "candle")
        bodies.add(world.add(body))
        flame = Body(PolygonShape.box(12.0, 10.0), Vec2(cx, y + 2), BodyKind.KINEMATIC, owner = this, tag = "flame")
        flame.isSensor = true
        flame.follow = body
        flame.followOffset = Vec2(0.0, -18.0)
        bodies.add(world.add(flame))
    }

    override fun onSensor(self: Body, other: Body) {
        if (!lit || self !== flame) return
        machine.partOf(other)?.onFlame(this)
    }

    override fun postStep() {
        if (!lit) return
        val f = flame.aabb
        machine.cutRopesIn(AABB(f.minX, f.minY - 4, f.maxX, f.maxY))
    }

    fun blowOut() {
        if (!lit) return
        lit = false
        machine.effects.add(Effect(Effect.Kind.PUFF, Vec2(pos.x, pos.y - 20), 0.6, 0.8))
        machine.sounds.add("puff")
    }

    fun light() { if (!lit) { lit = true; machine.sounds.add("fwoosh") } }
    override fun onFlame(source: Part) { if (source !== this) light() }
    override fun onExplosion(center: Vec2, radius: Double) { light() }

    override fun draw(p: Painter, t: Double) {
        val c = pos
        val bx = c.x - w / 2
        val by = c.y - 12
        // holder
        p.fillRoundRect(bx - 4, by + 20, w + 8, 6.0, 2.0, Style.STEEL)
        p.strokeRoundRect(bx - 4, by + 20, w + 8, 6.0, 2.0, Style.OUTLINE, Style.LINE)
        // wax
        Draw.outlinedRoundRect(p, bx + 1, by, w - 2, 22.0, 2.0, Style.CREAM)
        p.line(bx + 4, by + 3, bx + 4, by + 18, Colors.withAlpha(Style.ORANGE, 0.5), 1.5)
        // wick
        p.line(c.x, by, c.x, by - 4, Style.OUTLINE, 1.5)
        if (lit) {
            val fl = StrictMath.sin(t * 17) * 1.5
            val fh = 14.0 + fl
            val fpath = Path()
            fpath.moveTo(c.x, by - 4 - fh)
            fpath.quadTo(c.x + 6 + fl * 0.5, by - 6, c.x, by - 2)
            fpath.quadTo(c.x - 6 - fl * 0.5, by - 6, c.x, by - 4 - fh)
            p.fillPath(fpath, Style.FLAME)
            val core = Path()
            core.moveTo(c.x, by - 4 - fh * 0.55)
            core.quadTo(c.x + 3, by - 6, c.x, by - 3)
            core.quadTo(c.x - 3, by - 6, c.x, by - 4 - fh * 0.55)
            p.fillPath(core, Style.FLAME_CORE)
        }
    }
}

/** Cannon: fires a cannonball when its fuse is lit. */
class Cannon(placement: Placement, index: Int) : Part(placement, index), Activatable {
    lateinit var fuse: Body
    var fired = false
    private var fuseLit = -1.0
    private var recoil = 0.0
    override val activated get() = fired

    override fun build(world: World) {
        val barrel = Body(PolygonShape.rect(x, y + 8, x + w, y + h), Vec2.ZERO, BodyKind.STATIC, restitution = 0.4, friction = 0.5, owner = this, tag = "cannon")
        bodies.add(world.add(barrel))
        fuse = Body(PolygonShape.box(14.0, 12.0), local(4.0, 8.0), BodyKind.STATIC, owner = this, tag = "fuse")
        fuse.isSensor = true
        bodies.add(world.add(fuse))
    }

    override fun onFlame(source: Part) { lightFuse() }
    override fun onExplosion(center: Vec2, radius: Double) { lightFuse() }
    override fun trigger() { lightFuse() }
    fun lightFuse() { if (!fired && fuseLit < 0) { fuseLit = machine.time; machine.sounds.add("fizz") } }

    override fun postStep() {
        if (fired) { recoil *= 0.85; return }
        if (fuseLit >= 0 && machine.time - fuseLit > 0.5) fire()
    }

    private fun fire() {
        fired = true
        recoil = 1.0
        val muzzle = local(w - 6.0, 22.0)
        val ball = machine.spawn(Placement(PartType.CANNONBALL, muzzle.x + dir * 10 - 12, muzzle.y - 12)) as Ball
        ball.body?.vel = Vec2(dir * 560.0, -60.0)
        machine.effects.add(Effect(Effect.Kind.EXPLOSION, Vec2(muzzle.x + dir * 14, muzzle.y), 0.35, 0.8))
        machine.sounds.add("boom")
    }

    override fun draw(p: Painter, t: Double) {
        p.save()
        if (flipped) { p.translate(x + w, 0.0); p.scale(-1.0, 1.0); p.translate(-x, 0.0) }
        val rx = x - recoil * 6
        // wheel
        Draw.outlinedCircle(p, x + 24, y + h - 10, 10.0, Style.WOOD)
        p.fillCircle(x + 24, y + h - 10, 3.0, Style.OUTLINE)
        // barrel
        val barrel = Path.polygon(rx + 4, y + 14, rx + w - 2, y + 10, rx + w - 2, y + 34, rx + 4, y + 30)
        p.fillPath(barrel, Style.GREY_DARK)
        p.line(rx + 8, y + 17, rx + w - 10, y + 14, Colors.withAlpha(Style.WHITE, 0.25), 2.5)
        p.strokePath(barrel, Style.OUTLINE, Style.LINE)
        p.fillRoundRect(rx + w - 8, y + 8, 6.0, 28.0, 2.0, Style.GREY_DARK)
        p.strokeRoundRect(rx + w - 8, y + 8, 6.0, 28.0, 2.0, Style.OUTLINE, Style.LINE)
        p.fillRoundRect(rx, y + 16, 10.0, 12.0, 4.0, Style.GREY_DARK)
        p.strokeRoundRect(rx, y + 16, 10.0, 12.0, 4.0, Style.OUTLINE, Style.LINE)
        // fuse
        val fz = Path(); fz.moveTo(rx + 10, y + 16); fz.quadTo(rx + 6, y + 6, rx + 12, y + 4)
        p.strokePath(fz, Style.OUTLINE, 2.0)
        if (fuseLit >= 0 && !fired) {
            p.fillCircle(rx + 12 + StrictMath.sin(t * 30) * 1, y + 4, 3.5, Style.FLAME)
            p.fillCircle(rx + 12, y + 4, 1.8, Style.FLAME_CORE)
        }
        p.restore()
    }
}

/** Dynamite: explodes shortly after its fuse is lit, or when caught in another explosion. */
class Dynamite(placement: Placement, index: Int) : Part(placement, index), Activatable {
    lateinit var body: Body
    var exploded = false
    private var litAt = -1.0
    override val activated get() = exploded
    val pos: Vec2 get() = if (built) body.pos else Vec2(cx, cy)

    override fun build(world: World) {
        body = Body(PolygonShape.box(w / 2 - 2, 10.0), Vec2(cx, y + h - 10), BodyKind.DYNAMIC, mass = 9.0, restitution = 0.2, friction = 0.7, owner = this, tag = "dynamite")
        bodies.add(world.add(body))
        val fuse = Body(PolygonShape.box(16.0, 12.0), Vec2(cx, y + 4), BodyKind.KINEMATIC, owner = this, tag = "dyn-fuse")
        fuse.isSensor = true
        fuse.follow = body
        fuse.followOffset = Vec2(0.0, -14.0)
        bodies.add(world.add(fuse))
    }

    override fun onFlame(source: Part) { light() }
    override fun onExplosion(center: Vec2, radius: Double) { if (!exploded) { litAt = minOf(if (litAt < 0) machine.time else litAt, machine.time - 0.5) } }
    override fun trigger() { light() }
    fun light() { if (!exploded && litAt < 0) { litAt = machine.time; machine.sounds.add("fizz") } }

    override fun postStep() {
        if (exploded || litAt < 0) return
        if (machine.time - litAt >= 0.7) {
            exploded = true
            for (b in bodies) b.enabled = false
            machine.explode(pos, 96.0, 420.0)
        }
    }

    override val worldBounds: AABB get() = if (built) AABB(body.pos.x - w / 2, body.pos.y - 22, body.pos.x + w / 2, body.pos.y + 10) else placement.aabb

    override fun draw(p: Painter, t: Double) {
        if (exploded) return
        val c = pos
        val bx = c.x - w / 2 + 2
        val by = c.y - 10
        for (i in 0 until 3) {
            val sx = bx + i * 9
            p.fillRoundRect(sx, by, 9.0, 20.0, 2.5, Style.RED)
            p.fillRect(sx + 1, by + 6, 7.0, 2.2, Style.RED_DARK)
            p.fillRect(sx + 1, by + 13, 7.0, 2.2, Style.RED_DARK)
            p.fillRect(sx + 2, by + 2, 1.5, 16.0, Colors.withAlpha(Style.WHITE, 0.25))
            p.strokeRoundRect(sx, by, 9.0, 20.0, 2.5, Style.OUTLINE, 1.6)
        }
        p.fillRoundRect(bx - 1, by + 8, 29.0, 4.0, 1.0, Style.GREY_DARK)
        p.strokeRoundRect(bx - 1, by + 8, 29.0, 4.0, 1.0, Style.OUTLINE, 1.0)
        // fuse
        val fz = Path(); fz.moveTo(c.x, by); fz.quadTo(c.x + 4, by - 8, c.x, by - 12)
        p.strokePath(fz, Style.OUTLINE, 2.0)
        if (litAt >= 0) {
            p.fillCircle(c.x + StrictMath.sin(t * 40), by - 12, 3.5, Style.FLAME)
            p.fillCircle(c.x, by - 12, 1.8, Style.FLAME_CORE)
        }
    }
}

/** Rocket: when its fuse is lit it blasts straight up, its exhaust lighting anything below. */
class Rocket(placement: Placement, index: Int) : Part(placement, index), Activatable {
    lateinit var body: Body
    lateinit var exhaust: Body
    var launched = false
    private var litAt = -1.0
    var gone = false
    override val activated get() = launched
    val pos: Vec2 get() = if (built) body.pos else Vec2(cx, cy)

    override fun build(world: World) {
        body = Body(PolygonShape.box(w / 2 - 2, h / 2 - 6), Vec2(cx, cy), BodyKind.DYNAMIC, mass = 18.0, restitution = 0.1, friction = 0.7, owner = this, tag = "rocket")
        bodies.add(world.add(body))
        exhaust = Body(PolygonShape.box(8.0, 10.0), Vec2(cx, y + h + 6), BodyKind.KINEMATIC, owner = this, tag = "exhaust")
        exhaust.isSensor = true
        exhaust.enabled = false
        exhaust.follow = body
        exhaust.followOffset = Vec2(0.0, h / 2 + 4)
        bodies.add(world.add(exhaust))
        // the fuse dangles around the base: a candle standing next to the rocket lights it
        val fuse = Body(PolygonShape.box(24.0, 16.0), Vec2(cx, y + h - 8), BodyKind.KINEMATIC, owner = this, tag = "rocket-fuse")
        fuse.isSensor = true
        fuse.follow = body
        fuse.followOffset = Vec2(0.0, h / 2 - 8)
        bodies.add(world.add(fuse))
    }

    override fun onFlame(source: Part) { light() }
    override fun onExplosion(center: Vec2, radius: Double) { light() }
    override fun trigger() { light() }
    fun light() { if (!launched && litAt < 0) { litAt = machine.time; machine.sounds.add("fizz") } }

    override fun preStep() {
        if (!launched || gone) return
        body.applyForce(Vec2(0.0, -(machine.world.gravity.y + 900.0) * body.mass))
        body.vel = Vec2(body.vel.x * 0.9, body.vel.y)
    }

    override fun onSensor(self: Body, other: Body) {
        if (self === exhaust && launched) machine.partOf(other)?.onFlame(this)
    }

    override fun postStep() {
        if (gone) return
        if (!launched && litAt >= 0 && machine.time - litAt >= 0.6) {
            launched = true
            exhaust.enabled = true
            body.mask = Category.ALL and Category.SOLID.inv() // flies through things once lit, like the original
            machine.sounds.add("whoosh")
        }
        if (launched && body.pos.y < -80) { gone = true; body.enabled = false; exhaust.enabled = false }
    }

    override fun draw(p: Painter, t: Double) {
        if (gone) return
        val c = pos
        val bx = c.x - w / 2; val by = c.y - h / 2
        // fins
        val finL = Path.polygon(bx + 4, by + h - 22, bx - 2, by + h - 6, bx + 4, by + h - 6)
        val finR = Path.polygon(bx + w - 4, by + h - 22, bx + w + 2, by + h - 6, bx + w - 4, by + h - 6)
        Draw.outlinedPath(p, finL, Style.RED); Draw.outlinedPath(p, finR, Style.RED)
        // body
        Draw.outlinedRoundRect(p, bx + 4, by + 14, w - 8, h - 20, 3.0, Style.WHITE)
        p.fillRect(bx + 4, by + 30, w - 8, 6.0, Style.RED)
        p.fillCircle(c.x, by + 24, 3.5, Style.BLUE)
        p.strokeCircle(c.x, by + 24, 3.5, Style.OUTLINE, 1.2)
        // nose
        val nose = Path.polygon(bx + 4, by + 14, c.x, by, bx + w - 4, by + 14)
        Draw.outlinedPath(p, nose, Style.RED)
        // fuse or flame
        if (launched) {
            val fl = 16.0 + StrictMath.sin(t * 25) * 4
            val flame = Path.polygon(bx + 6, by + h - 6, bx + w - 6, by + h - 6, c.x, by + h - 6 + fl)
            p.fillPath(flame, Style.FLAME)
            val core = Path.polygon(bx + 9, by + h - 6, bx + w - 9, by + h - 6, c.x, by + h - 6 + fl * 0.6)
            p.fillPath(core, Style.FLAME_CORE)
        } else {
            val fz = Path(); fz.moveTo(c.x, by + h - 6); fz.quadTo(c.x + 4, by + h + 2, c.x, by + h + 6)
            p.strokePath(fz, Style.OUTLINE, 2.0)
            if (litAt >= 0) { p.fillCircle(c.x, by + h + 6, 3.0, Style.FLAME) }
        }
    }
}

/** Pinball bumper: bounces balls away at full speed with a flash. */
class Bumper(placement: Placement, index: Int) : Part(placement, index) {
    private var flash = 0.0
    override fun build(world: World) {
        val b = Body(CircleShape(16.0), Vec2(cx, cy), BodyKind.STATIC, restitution = 1.0, friction = 0.1, owner = this, tag = "bumper")
        bodies.add(world.add(b))
    }
    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (!other.isDynamic || ev.relativeSpeed < 15) return
        val n = ev.normalFrom(self)
        val speed = maxOf(other.vel.length, 240.0)
        val away = (n + other.vel.normalized() * 0.2).normalized()
        other.vel = away * speed
        other.restSteps = 0
        flash = 1.0
        machine.sounds.add("bump")
    }
    override fun postStep() { flash *= 0.8 }
    override fun draw(p: Painter, t: Double) {
        val r = 16.0
        Draw.outlinedCircle(p, cx, cy, r, Style.RED)
        p.fillCircle(cx, cy, r - 5, if (flash > 0.3) Style.WHITE else Style.YELLOW)
        p.strokeCircle(cx, cy, r - 5, Style.OUTLINE, 1.5)
        p.fillCircle(cx, cy, 4.0, Style.RED)
        if (flash > 0.2) { p.alpha = flash; p.strokeCircle(cx, cy, r + 6, Style.YELLOW, 3.0); p.alpha = 1.0 }
    }
}

/** Boxing glove: punch forward when its back button is pushed. */
class BoxingGlove(placement: Placement, index: Int) : Part(placement, index) {
    companion object { const val REACH = 84.0; const val PUNCH_SPEED = 720.0 }
    lateinit var fist: Body
    lateinit var button: Body
    private var extension = 0.0
    private var punching = false
    private var retracting = false
    private val fistRest: Vec2 get() = local(48.0, 20.0)

    lateinit var base: Body
    override fun build(world: World) {
        base = Body(PolygonShape.rect(x + (if (flipped) w - 28 else 0.0), y + 6, x + (if (flipped) w else 28.0), y + h), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "glove-base")
        bodies.add(world.add(base))
        button = Body(PolygonShape.box(4.0, 8.0), local(-2.0, 20.0), BodyKind.STATIC, friction = 0.3, owner = this, tag = "glove-button")
        bodies.add(world.add(button))
        fist = Body(PolygonShape.box(14.0, 12.0), fistRest, BodyKind.KINEMATIC, restitution = 0.3, friction = 0.4, owner = this, tag = "fist")
        bodies.add(world.add(fist))
    }

    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (!other.isDynamic) return
        if (self === button) trigger()
        else if (self === base && ev.normalFrom(self).y < -0.5) trigger()
    }

    override fun trigger() {
        if (punching || retracting) return
        punching = true
        machine.sounds.add("punch")
    }

    override fun preStep() {
        if (punching) {
            fist.vel = Vec2(dir * PUNCH_SPEED, 0.0)
            extension += PUNCH_SPEED * World.STEP
            if (extension >= REACH) { punching = false; retracting = true; extension = REACH }
        } else if (retracting) {
            fist.vel = Vec2(-dir * PUNCH_SPEED * 0.4, 0.0)
            extension -= PUNCH_SPEED * 0.4 * World.STEP
            if (extension <= 0) { extension = 0.0; retracting = false; fist.vel = Vec2.ZERO; fist.pos = fistRest }
        } else fist.vel = Vec2.ZERO
    }

    override fun draw(p: Painter, t: Double) {
        p.save()
        if (flipped) { p.translate(x + w, 0.0); p.scale(-1.0, 1.0); p.translate(-x, 0.0) }
        val ext = if (built) extension else 0.0
        // base box + button
        Draw.outlinedRoundRect(p, x, y + 6, 28.0, h - 6, 3.0, Style.GREY_DARK)
        Draw.outlinedRoundRect(p, x - 6, y + 12, 8.0, 16.0, 2.0, Style.RED)
        // spring
        val sx0 = x + 28; val sx1 = x + 34 + ext
        val spring = Path(); spring.moveTo(sx0, y + 20)
        val coils = 5
        for (i in 1..coils) {
            val fx = sx0 + (sx1 - sx0) * i / coils
            spring.lineTo(fx - (sx1 - sx0) / coils / 2, y + 20 + (if (i % 2 == 0) 6 else -6))
            spring.lineTo(fx, y + 20)
        }
        p.strokePath(spring, Style.OUTLINE, 2.0)
        // fist (glove): cuff, palm, thumb
        val fx = x + 34 + ext
        Draw.outlinedRoundRect(p, fx, y + 10, 9.0, 20.0, 2.5, Style.RED_DARK)
        p.fillRoundRect(fx + 6, y + 8, 24.0, 24.0, 10.0, Style.RED)
        p.fillCircle(fx + 13, y + 11, 5.0, Style.RED)
        p.strokeCircle(fx + 13, y + 11, 5.0, Style.OUTLINE, 1.4)
        p.fillRoundRect(fx + 6, y + 8, 24.0, 24.0, 10.0, Style.RED)
        p.line(fx + 16, y + 22, fx + 24, y + 22, Style.RED_DARK, 1.4)
        p.line(fx + 16, y + 26, fx + 23, y + 26, Style.RED_DARK, 1.4)
        p.gradientCircle(fx + 20, y + 15, 6.0, 0x99FFFFFF.toInt(), 0x00FFFFFF, 0.0, 0.0)
        p.strokeRoundRect(fx + 6, y + 8, 24.0, 24.0, 10.0, Style.OUTLINE, Style.LINE)
        p.restore()
    }
}

/** Scissors: snap shut when their handles are pushed, cutting any rope between the blades. */
class Scissors(placement: Placement, index: Int) : Part(placement, index), Sharp {
    lateinit var handle: Body
    private var snapAt = -1.0
    val snapped get() = snapAt >= 0
    fun bladeZone(): AABB = if (flipped) AABB(x, y + 10, x + 22, y + 26) else AABB(x + w - 22, y + 10, x + w, y + 26)

    override fun build(world: World) {
        handle = Body(PolygonShape.rect(x + (if (flipped) w - 26 else 0.0), y + 12, x + (if (flipped) w else 26.0), y + h), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "scissors")
        bodies.add(world.add(handle))
        val tip = Body(PolygonShape.box(8.0, 6.0), local(40.0, 18.0), BodyKind.STATIC, friction = 0.2, owner = this, tag = "scissor-tip")
        bodies.add(world.add(tip))
    }
    override fun isSharpAt(point: Vec2) = bladeZone().expanded(4.0).contains(point)
    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (self === handle && other.isDynamic && ev.normalFrom(self).y < -0.3) trigger()
    }
    override fun trigger() {
        if (snapped) return
        snapAt = machine.time
        machine.sounds.add("snip")
        machine.cutRopesIn(bladeZone())
    }
    override fun postStep() { if (snapped) machine.cutRopesIn(bladeZone()) }

    override fun draw(p: Painter, t: Double) {
        p.save()
        if (flipped) { p.translate(x + w, 0.0); p.scale(-1.0, 1.0); p.translate(-x, 0.0) }
        val open = if (snapped) 0.04 else 0.32
        val pivotX = x + 22; val pivotY = y + 16
        // each half is one rigid piece: a blade to the right of the pivot and a ring handle to the left
        for (side in listOf(1.0, -1.0)) {
            p.save(); p.translate(pivotX, pivotY); p.rotate(side * open)
            val blade = Path.polygon(0.0, -3.0 * side, 6.0, -3.5 * side, 26.0, -1.0 * side, 26.0, 0.0, 0.0, 2.5 * side)
            p.fillPath(blade, Style.STEEL_LIGHT)
            p.line(4.0, -1.5 * side, 24.0, -0.5 * side, Colors.withAlpha(Style.WHITE, 0.6), 1.0)
            p.strokePath(blade, Style.OUTLINE, 1.4)
            // handle shaft and finger ring
            p.line(0.0, 0.0, -9.0, 2.5 * side, Style.OUTLINE, 3.5)
            p.line(0.0, 0.0, -9.0, 2.5 * side, Style.BLUE, 1.8)
            p.strokeOval(-22.0, side * 3.0 - 5.0, 14.0, 10.0, Style.OUTLINE, 5.0)
            p.strokeOval(-22.0, side * 3.0 - 5.0, 14.0, 10.0, Style.BLUE, 2.6)
            p.restore()
        }
        p.fillCircle(pivotX, pivotY, 2.8, Style.GREY_DARK)
        p.strokeCircle(pivotX, pivotY, 2.8, Style.OUTLINE, 1.2)
        p.restore()
    }
}

/** Bellows: puffs air sideways when something lands on its handle. */
class Bellows(placement: Placement, index: Int) : Part(placement, index) {
    companion object { const val REACH = 150.0 }
    lateinit var top: Body
    private var squeeze = 0.0
    private var cooldown = 0.0
    override fun build(world: World) {
        top = Body(PolygonShape.rect(x + (if (flipped) 20.0 else 4.0), y + 4, x + (if (flipped) w - 4 else w - 20), y + h), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "bellows")
        bodies.add(world.add(top))
    }
    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (self === top && other.isDynamic && ev.normalFrom(self).y < -0.5 && ev.relativeSpeed > 30) trigger()
    }
    override fun trigger() {
        if (cooldown > 0) return
        cooldown = 0.8
        squeeze = 1.0
        val nozzle = local(w.toDouble(), 20.0)
        val zone = if (flipped) AABB(x - REACH, y - 10, x, y + h) else AABB(x + w, y - 10, x + w + REACH, y + h)
        val pressure = machine.world.airPressure
        for (b in machine.world.bodiesIn(zone)) {
            if (!b.isDynamic || b.owner === this) continue
            val dist = abs(b.pos.x - nozzle.x)
            val falloff = (1.0 - dist / REACH).coerceIn(0.2, 1.0)
            val dv = minOf(90.0 * pressure / b.mass, 420.0) * falloff
            b.vel = Vec2(b.vel.x + dir * dv, b.vel.y - dv * 0.15)
            b.restSteps = 0
        }
        for (part in machine.parts) if (part is Candle && part.lit && zone.overlaps(part.worldBounds)) part.blowOut()
        machine.effects.add(Effect(Effect.Kind.PUFF, Vec2(nozzle.x + dir * 20, nozzle.y), 0.5, 1.2))
        machine.sounds.add("puff")
    }
    override fun postStep() { squeeze *= 0.9; if (cooldown > 0) cooldown -= World.STEP }
    override fun draw(p: Painter, t: Double) {
        p.save()
        if (flipped) { p.translate(x + w, 0.0); p.scale(-1.0, 1.0); p.translate(-x, 0.0) }
        val sq = squeeze * 5
        val hingeX = x + w - 22           // where the boards meet the nozzle
        val midY = y + h / 2
        // pleated leather bag between the boards
        val bag = Path()
        bag.moveTo(x + 4, y + 8 + sq); bag.lineTo(hingeX, midY - 5); bag.lineTo(hingeX, midY + 5); bag.lineTo(x + 4, y + h - 8 - sq); bag.close()
        Draw.outlinedPath(p, bag, Style.BROWN)
        val pleat = Colors.withAlpha(Style.BROWN_DARK, 0.7)
        for (i in 1..3) {
            val f = i / 4.0
            val px = x + 4 + (hingeX - x - 4) * f
            val top = (y + 8 + sq) + ((midY - 5) - (y + 8 + sq)) * f
            val bot = (y + h - 8 - sq) + ((midY + 5) - (y + h - 8 - sq)) * f
            p.line(px, top, px, bot, pleat, 1.3)
        }
        // wooden boards, hinged at the nozzle end
        val top = Path.polygon(x + 2, y + 2 + sq, hingeX, midY - 7, hingeX, midY - 3, x + 2, y + 8 + sq)
        val bottom = Path.polygon(x + 2, y + h - 8 - sq, hingeX, midY + 3, hingeX, midY + 7, x + 2, y + h - 2 - sq)
        Draw.outlinedPath(p, top, Style.WOOD)
        Draw.outlinedPath(p, bottom, Style.WOOD)
        // handles
        Draw.outlinedRoundRect(p, x, y + sq - 2, 14.0, 6.0, 3.0, Style.WOOD_DARK, 1.4)
        Draw.outlinedRoundRect(p, x, y + h - 4 - sq, 14.0, 6.0, 3.0, Style.WOOD_DARK, 1.4)
        // nozzle
        Draw.outlinedRoundRect(p, hingeX, midY - 5, 22.0, 10.0, 3.0, Style.STEEL)
        p.fillRect(hingeX + 18, midY - 3, 3.0, 6.0, Style.GREY_DARK)
        p.restore()
    }
}

/** Pulley: ropes bend around it. */
class Pulley(placement: Placement, index: Int) : Part(placement, index) {
    override fun pulleyPoint() = Vec2(cx, cy)
    override fun draw(p: Painter, t: Double) {
        p.line(cx, y, cx, cy, Style.OUTLINE, 2.0)
        Draw.outlinedCircle(p, cx, cy, 7.0, Style.YELLOW)
        p.fillCircle(cx, cy, 2.0, Style.OUTLINE)
    }
}

/** Hook: a fixed anchor for ropes. */
class Hook(placement: Placement, index: Int) : Part(placement, index) {
    override fun ropeAnchor() = Vec2(w / 2, h - 3)
    override fun draw(p: Painter, t: Double) {
        p.fillRect(cx - 5, y, 10.0, 4.0, Style.STEEL)
        p.strokeRect(cx - 5, y, 10.0, 4.0, Style.OUTLINE, 1.2)
        p.strokeCircle(cx, y + 10, 4.5, Style.OUTLINE, 3.0)
        p.strokeCircle(cx, y + 10, 4.5, Style.STEEL_LIGHT, 1.5)
    }
}

/** Electric motor: spins a wheel that can drive a belt, when powered. */
class Motor(placement: Placement, index: Int) : Part(placement, index) {
    private var spin = 0.0
    override val spinOutput: Boolean get() = powered
    override fun beltHub() = Vec2(x + (if (flipped) 12.0 else w - 12.0), y + 16)
    override fun build(world: World) {
        val b = Body(PolygonShape.rect(x, y + 6, x + w, y + h), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "motor")
        bodies.add(world.add(b))
    }
    override fun postStep() { if (powered) spin += 0.3 }
    override fun draw(p: Painter, t: Double) {
        val sp = if (built) spin else t * 5
        Draw.outlinedRoundRect(p, x + 2, y + h - 6, w - 4, 6.0, 2.0, Style.STEEL)
        Draw.outlinedRoundRect(p, x + 4, y + 10, w - 20, h - 16, 4.0, Style.RED)
        p.fillRect(x + 8, y + 16, w - 28, 3.0, Style.RED_DARK)
        p.fillRect(x + 8, y + 22, w - 28, 3.0, Style.RED_DARK)
        val hub = beltHub()
        Draw.outlinedCircle(p, hub.x, hub.y, 9.0, Style.GREY_LIGHT)
        p.save(); p.translate(hub.x, hub.y); p.rotate(sp)
        p.line(-6.0, 0.0, 6.0, 0.0, Style.OUTLINE, 2.0); p.line(0.0, -6.0, 0.0, 6.0, Style.OUTLINE, 2.0)
        p.restore()
        p.fillCircle(x + 11, y + 15, 3.0, if (powered) Style.GREEN else Style.GREY)
        p.strokeCircle(x + 11, y + 15, 3.0, Style.OUTLINE, 1.2)
    }
}
