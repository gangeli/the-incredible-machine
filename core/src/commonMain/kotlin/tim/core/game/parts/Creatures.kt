package tim.core.game.parts

import tim.core.game.Activatable
import tim.core.game.Draw
import tim.core.game.Effect
import tim.core.game.Part
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.physics.AABB
import tim.core.physics.Body
import tim.core.physics.BodyKind
import tim.core.physics.Category
import tim.core.physics.ContactEvent
import tim.core.physics.PolygonShape
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shared walking and animation logic for creatures: a non-rotating box that accelerates and
 * decelerates, turns at walls, and drives a distance-based walk cycle, blinking, landing squash
 * and a smooth turn-around for its drawing.
 */
abstract class Creature(placement: Placement, index: Int, val mass: Double, val speed: Double, val accel: Double, private val strideLength: Double) : Part(placement, index) {
    lateinit var body: Body
    var facing = 1.0
    var walking = true
    /** How much narrower than the picture the solid body is, so a cage of the same width fits over it. */
    open val bodyInset: Double get() = 1.0
    var caught = false
    private var turnCooldown = 0
    val pos: Vec2 get() = if (built) body.pos else Vec2(cx, cy)

    // ---- animation state (visual only)
    /** Walk-cycle phase in radians, advanced by distance travelled so feet never slide. */
    var stride = 0.0
    /** Smoothed facing used for drawing; passes through 0 during a turn. */
    var face = 1.0
    var squash = 0.0
    var airTime = 0.0
    var blink = 0.0
    private var nextBlink = 2.0
    var idleTime = 0.0
    private var wasGrounded = true
    val airborne: Boolean get() = built && !body.grounded && airTime > 0.08
    val moving: Boolean get() = built && abs(body.vel.x) > 4.0
    val time: Double get() = if (built) machine.time else 0.0

    override fun build(world: World) {
        facing = dir
        face = dir
        body = Body(PolygonShape.box(w / 2 - bodyInset, h / 2 - 1), Vec2(cx, cy), BodyKind.DYNAMIC, mass = mass, restitution = 0.0, friction = 0.8, owner = this, tag = type.name)
        body.category = Category.CREATURE
        bodies.add(world.add(body))
        nextBlink = 1.5 + noise(1) * 2.0
    }

    /** Deterministic pseudo-random number in [0,1) from a seed and this creature's index. */
    fun noise(seed: Int): Double {
        val v = kotlin.math.sin(seed * 12.9898 + index * 78.233 + 1.0) * 43758.5453
        return v - kotlin.math.floor(v)
    }

    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        val n = ev.normalFrom(self)
        // a wall in front (not a small step we could climb): turn around
        if (abs(n.x) > 0.7 && other.aabb.minY < self.aabb.maxY - 5 && (n.x * facing) > 0 && turnCooldown == 0) {
            facing = -facing
            turnCooldown = 15
        }
    }

    open fun think() {}

    override fun preStep() {
        if (caught) return
        if (turnCooldown > 0) turnCooldown--
        think()
        if (body.grounded) {
            val target = if (walking) facing * speed else 0.0
            val dv = (target - body.vel.x).coerceIn(-accel * World.STEP, accel * World.STEP)
            body.vel = Vec2(body.vel.x + dv, body.vel.y)
        }
    }

    override fun postStep() {
        if (caught) return
        val dt = World.STEP
        stride += abs(body.vel.x) * dt / strideLength * 2 * kotlin.math.PI
        face += (facing - face) * min(1.0, 10.0 * dt)
        if (!body.grounded) airTime += dt
        else {
            if (!wasGrounded && airTime > 0.15) { squash = 1.0; onLanded() }
            airTime = 0.0
        }
        wasGrounded = body.grounded
        squash *= 0.82
        nextBlink -= dt
        if (nextBlink <= 0) { blink = 0.12; nextBlink = 2.0 + noise((time * 10).toInt()) * 3.0 }
        if (blink > 0) blink -= dt
        idleTime = if (moving) 0.0 else idleTime + dt
    }

    open fun onLanded() {}

    /** Whether some cage traps this creature. */
    fun isCaged(): Boolean = machine.parts.any { it is Cage && it.traps(this) }
    override val worldBounds: AABB get() = if (built) AABB(body.pos.x - w / 2, body.pos.y - h / 2, body.pos.x + w / 2, body.pos.y + h / 2) else placement.aabb

    /** Sets up the drawing frame: origin at the feet centre, x flipped for the facing, landing squash applied. */
    protected fun beginSprite(p: Painter) {
        val c = pos
        val fx = if (built) face else dir
        val sx = if (abs(fx) < 0.18) (if (fx < 0) -0.18 else 0.18) else fx
        p.translate(c.x, c.y + h / 2)
        p.scale(sx * (1 + 0.22 * squash), 1 - 0.22 * squash)
    }
}

/** Mort the mouse: scurries in bursts, sniffs, runs for cheese, flees cats, hides when caught. */
class Mouse(placement: Placement, index: Int) : Creature(placement, index, 0.1, 70.0, 900.0, 9.0) {
    var eating = false
    var scared = false
    private var burstRunning = true
    private var burstTimer = 0.9
    private var sniff = 0.0

    override fun think() {
        scared = false
        if (isCaged()) { walking = false; return }
        // Mort spots cheese on his level in the direction he is facing (or right next to him)
        val cheese = machine.parts.filterIsInstance<Cheese>()
            .filter { !it.eaten && abs(it.center.y - pos.y) < 48 && ((it.center.x - pos.x) * facing > -20) }
            .minByOrNull { abs(it.center.x - pos.x) }
        val cat = machine.parts.filterIsInstance<Cat>().firstOrNull { !it.caught && !it.isCaged() && abs(it.pos.x - pos.x) < 110 && abs(it.pos.y - pos.y) < 40 }
        if (cat != null) {
            facing = if (cat.pos.x > pos.x) -1.0 else 1.0
            walking = true; scared = true
            return
        }
        if (cheese != null && abs(cheese.center.y - pos.y) < 48) {
            val dx = cheese.center.x - pos.x
            if (abs(dx) < 14) {
                if (!eating) { eating = true; cheese.eat(); machine.sounds.add("nibble") }
                walking = false
                return
            }
            facing = if (dx > 0) 1.0 else -1.0
            walking = true
            return
        }
        if (eating) { walking = false; return }
        // no goal in sight: scurry a little, stop to sniff, scurry again
        burstTimer -= World.STEP
        if (burstTimer <= 0) {
            burstRunning = !burstRunning
            burstTimer = if (burstRunning) 0.7 + noise((time * 7).toInt()) * 0.8 else 0.35 + noise((time * 11).toInt() + 3) * 0.45
        }
        walking = burstRunning
        if (!walking) sniff += World.STEP
    }

    fun catch() {
        if (caught) return
        caught = true
        body.enabled = false
        machine.effects.add(Effect(Effect.Kind.PUFF, pos, 0.5, 1.0))
        machine.sounds.add("squeak")
    }

    override fun draw(p: Painter, t: Double) {
        if (caught) return
        val tt = if (built) time else t
        p.save()
        beginSprite(p)
        val run = built && moving && walking
        val sniffing = built && !walking && !eating && !airborne
        val bob = if (run) abs(kotlin.math.sin(stride)) * 1.2 else 0.0
        val nod = if (sniffing) kotlin.math.sin(tt * 14) * 0.8 else if (eating) kotlin.math.sin(tt * 16) * 1.4 else 0.0
        val stretch = if (scared) 1.12 else 1.0
        val bodyY = -6.5 - bob
        // tail: sways while running, hangs still while sniffing, up in the air
        val sway = if (run) kotlin.math.sin(stride * 0.5) * 3 else kotlin.math.sin(tt * 1.5) * 1.5
        val tail = Path()
        tail.moveTo(-10.0, bodyY + 2)
        if (airborne) tail.quadTo(-18.0, bodyY - 10, -24.0, bodyY - 4)
        else tail.quadTo(-19.0, bodyY - 4 + sway, -23.0, bodyY + 3 + sway * 0.5)
        p.strokePath(tail, Style.OUTLINE, 2.2)
        p.strokePath(tail, Style.PINK, 1.1)
        // legs: four tiny scurrying legs, spread wide when airborne
        for ((i, lx) in listOf(-7.0, -3.0, 2.0, 6.0).withIndex()) {
            val phase = stride * 1.6 + i * kotlin.math.PI / 2
            val swing = if (run) kotlin.math.sin(phase) * 2.5 else if (airborne) (if (i < 2) -2.5 else 2.5) else 0.0
            val lift = if (run) max(0.0, -kotlin.math.cos(phase)) * 1.5 else 0.0
            p.line(lx, bodyY + 5, lx + swing, 0.0 - lift, Style.OUTLINE, 2.4)
            p.line(lx, bodyY + 5, lx + swing, 0.0 - lift, Style.PINK, 1.2)
        }
        // body
        p.fillOval(-11.0 * stretch, bodyY - 6, 20.0 * stretch, 13.0, Style.GREY)
        p.strokeOval(-11.0 * stretch, bodyY - 6, 20.0 * stretch, 13.0, Style.OUTLINE, 1.5)
        p.fillOval(-8.0, bodyY - 1, 12.0, 6.0, Colors.withAlpha(Style.WHITE, 0.25))
        // head with ear (back when scared), eye, nose, whiskers
        val hx = 8.0; val hy = bodyY - 2 + nod
        p.fillCircle(hx, hy, 5.0, Style.GREY)
        p.strokeCircle(hx, hy, 5.0, Style.OUTLINE, 1.5)
        val earX = if (scared) hx - 4.5 else hx - 2.0
        p.fillCircle(earX, hy - 5, 3.0, Style.PINK)
        p.strokeCircle(earX, hy - 5, 3.0, Style.OUTLINE, 1.2)
        if (blink > 0 && built) p.line(hx + 0.5, hy - 1, hx + 3, hy - 1, Style.OUTLINE, 1.0)
        else {
            p.fillCircle(hx + 1.5, hy - 1.2, if (scared) 2.2 else 1.8, Style.WHITE)
            p.fillCircle(hx + 1.9, hy - 1.0, 1.0, Style.OUTLINE)
        }
        p.fillCircle(hx + 5, hy + 1, 1.5, Style.PINK)
        val twitch = if (sniffing) kotlin.math.sin(tt * 20) * 1.0 else 0.0
        p.line(hx + 4, hy + 1, hx + 9, hy - 1 + twitch, Style.OUTLINE, 0.8)
        p.line(hx + 4, hy + 2, hx + 9, hy + 3 - twitch, Style.OUTLINE, 0.8)
        p.restore()
    }
}

/**
 * Pokey the cat: sits and grooms when nothing is going on, glances about, stalks a mouse it can
 * see, jumps when something hits him, and lands with a bounce.
 */
class Cat(placement: Placement, index: Int) : Creature(placement, index, 12.0, 55.0, 380.0, 22.0) {
    override val bodyInset: Double get() = 5.0
    private var startled = 0.0
    private var scanTimer = 0
    private var groom = 0.0
    private var nextGroom = 4.0
    private var nextGlance = 5.0
    val sitting: Boolean get() = built && !walking && !airborne && startled <= 0
    val chasing: Boolean get() = walking

    override fun think() {
        if (isCaged()) { walking = false; return }
        if (startled > 0) { startled -= World.STEP; walking = false; return }
        // idle habits: groom now and then, glance the other way after a while
        if (!walking) {
            nextGroom -= World.STEP
            if (groom > 0) groom -= World.STEP
            else if (nextGroom <= 0) { groom = 1.4; nextGroom = 5.0 + noise((time * 3).toInt()) * 6.0 }
            nextGlance -= World.STEP
            if (nextGlance <= 0 && groom <= 0) { facing = -facing; nextGlance = 4.0 + noise((time * 5).toInt() + 9) * 5.0 }
        }
        if (scanTimer-- > 0) return
        scanTimer = 10
        val mouse = machine.parts.filterIsInstance<Mouse>().firstOrNull { !it.caught && abs(it.pos.y - pos.y) < 40 && abs(it.pos.x - pos.x) < 240 }
        if (mouse != null) {
            facing = if (mouse.pos.x > pos.x) 1.0 else -1.0
            walking = true
            groom = 0.0
            if (abs(mouse.pos.x - pos.x) < 30) mouse.catch()
        } else walking = false
    }

    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        super.onContact(self, other, ev)
        if (other.isDynamic && ev.relativeSpeed > 60 && machine.partOf(other) !is Creature) {
            if (startled <= 0) { startled = 0.9; groom = 0.0; body.vel = Vec2(body.vel.x, -150.0); machine.sounds.add("meow") }
        }
    }

    override fun onLanded() { machine.sounds.add("bump") }

    override fun draw(p: Painter, t: Double) {
        val tt = if (built) time else t
        p.save()
        beginSprite(p)
        val fur = Style.ORANGE
        val dark = Colors.withAlpha(Style.BROWN, 0.55)
        val run = built && moving && walking
        val jumping = built && (startled > 0 || airborne)
        val sit = !built || sitting
        val grooming = built && groom > 0 && sit
        val bob = if (run) abs(kotlin.math.sin(stride)) * 1.6 else 0.0
        // vertical placement of the body (feet at y=0)
        val bodyY = (if (sit) -9.0 else if (jumping) -13.0 else -11.0) - bob
        val sway = kotlin.math.sin(tt * 2.2 + index) * 3.0
        // tail
        val tail = Path()
        if (jumping) {
            tail.moveTo(-17.0, bodyY - 2); tail.quadTo(-24.0, bodyY - 12, -22.0, bodyY - 24)
            p.strokePath(tail, Style.OUTLINE, 7.0); p.strokePath(tail, fur, 5.0)
        } else if (sit) {
            tail.moveTo(-14.0, bodyY + 8); tail.quadTo(-26.0, bodyY + 10 + sway * 0.3, -24.0 + sway * 0.6, bodyY + 3)
            p.strokePath(tail, Style.OUTLINE, 5.0); p.strokePath(tail, fur, 3.0)
        } else {
            tail.moveTo(-17.0, bodyY); tail.quadTo(-28.0, bodyY - 5 + sway, -25.0, bodyY - 17 + sway)
            p.strokePath(tail, Style.OUTLINE, 5.0); p.strokePath(tail, fur, 3.0)
        }
        // legs
        fun leg(hipX: Double, hipY: Double, footX: Double, footY: Double) {
            p.line(hipX, hipY, footX, footY, Style.OUTLINE, 6.5)
            p.line(hipX, hipY, footX, footY, fur, 4.2)
            p.fillOval(footX - 3.5, footY - 2.5, 7.0, 4.0, fur)
            p.strokeOval(footX - 3.5, footY - 2.5, 7.0, 4.0, Style.OUTLINE, 1.2)
        }
        if (sit) {
            // haunch and folded hind leg, front legs straight
            p.fillOval(-16.0, bodyY - 2, 16.0, 16.0, fur)
            p.strokeOval(-16.0, bodyY - 2, 16.0, 16.0, Style.OUTLINE, 1.6)
            leg(-10.0, bodyY + 10, -5.0, -1.0)
            val liftPaw = if (grooming) 1.0 else 0.0
            leg(6.0, bodyY + 4, 5.0 + liftPaw * 6, -1.0 - liftPaw * 12)
            leg(11.0, bodyY + 4, 11.0, -1.0)
        } else if (jumping) {
            leg(-12.0, bodyY + 6, -18.0, 2.0)
            leg(-7.0, bodyY + 6, -13.0, 1.0)
            leg(7.0, bodyY + 6, 14.0, -2.0)
            leg(12.0, bodyY + 6, 19.0, -3.0)
        } else {
            // trot: diagonal pairs move together
            for ((i, hip) in listOf(-12.0, -7.0, 7.0, 12.0).withIndex()) {
                val phase = stride + (if (i == 0 || i == 3) 0.0 else kotlin.math.PI)
                val swing = if (run) kotlin.math.sin(phase) * 6 else 0.0
                val lift = if (run) max(0.0, -kotlin.math.cos(phase)) * 4 else 0.0
                leg(hip, bodyY + 6, hip + swing, -lift)
            }
        }
        // body (arched when jumping)
        if (jumping) {
            val arch = Path()
            arch.moveTo(-18.0, bodyY + 8); arch.quadTo(-4.0, bodyY - 16, 14.0, bodyY + 6); arch.quadTo(0.0, bodyY + 14, -18.0, bodyY + 8); arch.close()
            p.fillPath(arch, fur); p.strokePath(arch, Style.OUTLINE, 1.6)
            for (k in 0 until 6) { val sx = -14.0 + k * 5.5; val sy = bodyY - 2 - (1 - abs(k - 2.5) / 3.0) * 8; p.line(sx, sy, sx + 1.0, sy - 5.0, Style.OUTLINE, 1.6) }
        } else if (sit) {
            val chest = Path()
            chest.moveTo(-8.0, bodyY + 10); chest.quadTo(-6.0, bodyY - 12, 12.0, bodyY - 4); chest.quadTo(16.0, bodyY + 8, 8.0, bodyY + 12); chest.close()
            p.fillPath(chest, fur); p.strokePath(chest, Style.OUTLINE, 1.6)
            p.line(-2.0, bodyY, 4.0, bodyY - 1, dark, 2.0)
        } else {
            p.fillOval(-20.0, bodyY - 8, 34.0, 20.0, fur)
            p.strokeOval(-20.0, bodyY - 8, 34.0, 20.0, Style.OUTLINE, 1.6)
            p.line(-12.0, bodyY - 2, -4.0, bodyY - 2, dark, 2.0)
            p.line(-10.0, bodyY + 3, -2.0, bodyY + 3, dark, 2.0)
        }
        // head
        val hx = if (grooming) 9.0 else if (sit) 12.0 else 16.0
        val hy = (if (grooming) bodyY - 1 else if (sit) bodyY - 14 else bodyY - 8) + (if (run) -bob * 0.5 else 0.0)
        p.fillCircle(hx, hy, 9.0, fur)
        val earL = Path.polygon(hx - 7, hy - 4, hx - 5, hy - 13, hx, hy - 7)
        val earR = Path.polygon(hx + 2, hy - 8, hx + 6, hy - 13, hx + 8, hy - 4)
        Draw.outlinedPath(p, earL, fur, 1.2); Draw.outlinedPath(p, earR, fur, 1.2)
        p.fillPath(Path.polygon(hx - 5.5, hy - 5, hx - 4.5, hy - 10.5, hx - 1, hy - 7), Style.PINK)
        p.strokeCircle(hx, hy, 9.0, Style.OUTLINE, 1.6)
        val eyeR = if (jumping) 3.0 else 2.4
        for (ex in listOf(hx - 2.5, hx + 3.5)) {
            if (blink > 0 && built && !jumping) { p.line(ex - 2, hy - 1, ex + 2, hy - 1, Style.OUTLINE, 1.2) }
            else {
                p.fillCircle(ex, hy - 1, eyeR, Style.WHITE)
                p.strokeCircle(ex, hy - 1, eyeR, Style.OUTLINE, 1.0)
                p.fillCircle(ex + 0.6, hy - 0.6, eyeR * (if (chasing) 0.6 else 0.5), Style.OUTLINE)
            }
        }
        p.fillCircle(hx + 5.5, hy + 3.5, 1.4, Style.PINK)
        val mouth = Path().moveTo(hx + 3, hy + 5).quadTo(hx + 5.5, hy + 7, hx + 8, hy + 5)
        p.strokePath(mouth, Style.OUTLINE, 1.0)
        p.line(hx + 7, hy + 3, hx + 13, hy + 1, Style.OUTLINE, 1.0)
        p.line(hx + 7, hy + 4, hx + 13, hy + 5, Style.OUTLINE, 1.0)
        p.restore()
    }
}

/** Cheese: bait for Mort. */
class Cheese(placement: Placement, index: Int) : Part(placement, index), Activatable {
    var eaten = false
    override val activated get() = eaten
    lateinit var body: Body
    override fun build(world: World) {
        body = Body(PolygonShape.box(w / 2 - 1, h / 2 - 1), Vec2(cx, cy), BodyKind.DYNAMIC, mass = 4.0, restitution = 0.1, friction = 0.8, owner = this, tag = "cheese")
        body.mask = Category.ALL and Category.CREATURE.inv()
        bodies.add(world.add(body))
    }
    fun eat() { if (!eaten) { eaten = true; machine.effects.add(Effect(Effect.Kind.SPARKLE, center, 0.6, 0.8)) } }
    override val worldBounds: AABB get() = if (built) AABB(body.pos.x - w / 2, body.pos.y - h / 2, body.pos.x + w / 2, body.pos.y + h / 2) else placement.aabb
    override fun draw(p: Painter, t: Double) {
        val b = worldBounds
        val wedge = Path.polygon(b.minX, b.maxY, b.maxX, b.maxY, b.maxX, b.minY + 4, b.minX + 6, b.minY)
        if (eaten) p.alpha = 0.35
        Draw.outlinedPath(p, wedge, Style.CHEESE)
        p.fillCircle(b.minX + 8, b.maxY - 5, 2.2, Style.ORANGE)
        p.fillCircle(b.minX + 15, b.maxY - 9, 1.8, Style.ORANGE)
        p.fillCircle(b.maxX - 6, b.maxY - 4, 1.5, Style.ORANGE)
        p.alpha = 1.0
    }
}
