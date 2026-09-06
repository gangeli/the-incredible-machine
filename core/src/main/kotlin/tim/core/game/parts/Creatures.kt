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

/** Shared walking logic for creatures: a non-rotating box that walks on surfaces and turns at walls. */
abstract class Creature(placement: Placement, index: Int, val mass: Double, val speed: Double) : Part(placement, index) {
    lateinit var body: Body
    var facing = 1.0
    var walking = true
    var caught = false
    private var turnCooldown = 0
    val pos: Vec2 get() = if (built) body.pos else Vec2(cx, cy)
    var stepPhase = 0.0

    override fun build(world: World) {
        facing = dir
        body = Body(PolygonShape.box(w / 2 - 1, h / 2 - 1), Vec2(cx, cy), BodyKind.DYNAMIC, mass = mass, restitution = 0.0, friction = 0.8, owner = this, tag = type.name)
        body.category = Category.CREATURE
        bodies.add(world.add(body))
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
        if (walking && body.grounded) {
            body.vel = Vec2(facing * speed, body.vel.y)
            stepPhase += World.STEP * 10
        } else if (!walking && body.grounded) {
            body.vel = Vec2(body.vel.x * 0.6, body.vel.y)
        }
    }

    /** Whether some cage traps this creature. */
    fun isCaged(): Boolean = machine.parts.any { it is Cage && it.traps(this) }
    override val worldBounds: AABB get() = if (built) AABB(body.pos.x - w / 2, body.pos.y - h / 2, body.pos.x + w / 2, body.pos.y + h / 2) else placement.aabb
}

/** Mort the mouse: runs towards cheese, away from cats, and hides when caught. */
class Mouse(placement: Placement, index: Int) : Creature(placement, index, 0.1, 70.0) {
    var eating = false
    override fun think() {
        if (isCaged()) { walking = false; return }
        // Mort only notices cheese that is fairly close and at his level
        val cheese = machine.parts.filterIsInstance<Cheese>().filter { !it.eaten && abs(it.center.x - pos.x) < 220 }.minByOrNull { abs(it.center.x - pos.x) }
        val cat = machine.parts.filterIsInstance<Cat>().firstOrNull { !it.caught && !it.isCaged() && abs(it.pos.x - pos.x) < 110 && abs(it.pos.y - pos.y) < 40 }
        if (cat != null) { facing = if (cat.pos.x > pos.x) -1.0 else 1.0; walking = true; return }
        if (cheese != null && abs(cheese.center.y - pos.y) < 48) {
            val dx = cheese.center.x - pos.x
            if (abs(dx) < 14) {
                if (!eating) { eating = true; cheese.eat(); machine.sounds.add("nibble") }
                walking = false
                return
            }
            facing = if (dx > 0) 1.0 else -1.0
        }
        walking = !eating
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
        val c = pos
        val f = if (built) facing else dir
        p.save()
        p.translate(c.x, c.y)
        if (f < 0) p.scale(-1.0, 1.0)
        val bob = if (walking && built) StrictMath.sin(stepPhase) * 1.0 else 0.0
        // tail
        val tail = Path(); tail.moveTo(-10.0, 2.0); tail.quadTo(-18.0, -6.0 + bob, -22.0, 4.0)
        p.strokePath(tail, Style.OUTLINE, 2.0)
        p.strokePath(tail, Style.PINK, 1.0)
        // body
        p.fillOval(-11.0, -6.0 + bob, 20.0, 13.0, Style.GREY)
        p.strokeOval(-11.0, -6.0 + bob, 20.0, 13.0, Style.OUTLINE, 1.5)
        // head + ear + eye + nose
        p.fillCircle(8.0, -2.0 + bob, 5.0, Style.GREY)
        p.strokeCircle(8.0, -2.0 + bob, 5.0, Style.OUTLINE, 1.5)
        p.fillCircle(6.0, -7.0 + bob, 3.0, Style.PINK)
        p.strokeCircle(6.0, -7.0 + bob, 3.0, Style.OUTLINE, 1.2)
        p.fillCircle(9.5, -3.2 + bob, 1.8, Style.WHITE)
        p.fillCircle(9.8, -3.0 + bob, 1.0, Style.OUTLINE)
        p.fillCircle(13.0, -1.0 + bob, 1.5, Style.PINK)
        // feet
        p.fillOval(-6.0, 5.0, 5.0, 3.0, Style.PINK)
        p.fillOval(2.0, 5.0, 5.0, 3.0, Style.PINK)
        p.restore()
    }
}

/** Pokey the cat: walks towards a mouse it can see, otherwise sits. Gets startled when hit. */
class Cat(placement: Placement, index: Int) : Creature(placement, index, 12.0, 55.0) {
    private var startled = 0.0
    private var scanTimer = 0
    override fun think() {
        if (isCaged()) { walking = false; return }
        if (startled > 0) { startled -= World.STEP; walking = false; return }
        if (scanTimer-- > 0) return
        scanTimer = 10
        val mouse = machine.parts.filterIsInstance<Mouse>().firstOrNull { !it.caught && abs(it.pos.y - pos.y) < 40 && abs(it.pos.x - pos.x) < 240 }
        if (mouse != null) {
            facing = if (mouse.pos.x > pos.x) 1.0 else -1.0
            walking = true
            if (abs(mouse.pos.x - pos.x) < 30) mouse.catch()
        } else walking = false
    }
    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        super.onContact(self, other, ev)
        if (other.isDynamic && ev.relativeSpeed > 60 && machine.partOf(other) !is Creature) {
            if (startled <= 0) { startled = 0.8; body.vel = Vec2(body.vel.x, -140.0); machine.sounds.add("meow") }
        }
    }
    override fun draw(p: Painter, t: Double) {
        val c = pos
        val f = if (built) facing else dir
        p.save()
        p.translate(c.x, c.y)
        if (f < 0) p.scale(-1.0, 1.0)
        val bob = if (walking && built) StrictMath.sin(stepPhase) * 1.5 else 0.0
        val fur = Style.ORANGE
        // tail
        val tail = Path(); tail.moveTo(-18.0, 0.0); tail.quadTo(-30.0, -4.0, -26.0, -16.0 + bob)
        p.strokePath(tail, Style.OUTLINE, 5.0); p.strokePath(tail, fur, 3.0)
        // body
        p.fillOval(-20.0, -8.0 + bob, 34.0, 20.0, fur)
        p.strokeOval(-20.0, -8.0 + bob, 34.0, 20.0, Style.OUTLINE, 1.6)
        p.line(-12.0, -2.0 + bob, -4.0, -2.0 + bob, Colors.withAlpha(Style.BROWN, 0.6), 2.0)
        p.line(-10.0, 3.0 + bob, -2.0, 3.0 + bob, Colors.withAlpha(Style.BROWN, 0.6), 2.0)
        // legs
        for (lx in listOf(-12.0, -4.0, 6.0, 12.0)) p.fillRoundRect(lx, 6.0, 5.0, 10.0, 2.0, fur)
        for (lx in listOf(-12.0, -4.0, 6.0, 12.0)) p.strokeRoundRect(lx, 6.0, 5.0, 10.0, 2.0, Style.OUTLINE, 1.2)
        // head
        p.fillCircle(16.0, -8.0 + bob, 9.0, fur)
        val earL = Path.polygon(9.0, -12.0 + bob, 11.0, -21.0 + bob, 16.0, -15.0 + bob)
        val earR = Path.polygon(18.0, -16.0 + bob, 22.0, -21.0 + bob, 24.0, -12.0 + bob)
        Draw.outlinedPath(p, earL, fur, 1.2); Draw.outlinedPath(p, earR, fur, 1.2)
        p.strokeCircle(16.0, -8.0 + bob, 9.0, Style.OUTLINE, 1.6)
        // eyes: big and friendly, wide open when startled
        val eyeR = if (startled > 0) 3.0 else 2.4
        for (ex in listOf(13.5, 19.5)) {
            p.fillCircle(ex, -9.0 + bob, eyeR, Style.WHITE)
            p.strokeCircle(ex, -9.0 + bob, eyeR, Style.OUTLINE, 1.0)
            p.fillCircle(ex + 0.6, -8.6 + bob, eyeR * 0.5, Style.OUTLINE)
        }
        p.fillCircle(21.5, -4.5 + bob, 1.4, Style.PINK)
        val smile = Path().moveTo(19.0, -3.0 + bob).quadTo(21.5, -1.0 + bob, 24.0, -3.0 + bob)
        p.strokePath(smile, Style.OUTLINE, 1.0)
        p.line(23.0, -5.0 + bob, 29.0, -7.0 + bob, Style.OUTLINE, 1.0)
        p.line(23.0, -4.0 + bob, 29.0, -3.0 + bob, Style.OUTLINE, 1.0)
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
