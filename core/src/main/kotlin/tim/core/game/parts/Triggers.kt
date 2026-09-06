package tim.core.game.parts

import tim.core.game.Activatable
import tim.core.game.Container
import tim.core.game.Draw
import tim.core.game.Effect
import tim.core.game.Part
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.physics.AABB
import tim.core.physics.Body
import tim.core.physics.BodyKind
import tim.core.physics.ContactEvent
import tim.core.physics.PolygonShape
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.Path

/** Light switch: flips on when something lands on it. Passes power through to wired parts. */
class Switch(placement: Placement, index: Int) : Part(placement, index), Activatable {
    var on = false
    override val activated get() = on
    override val powerOutput: Boolean get() = on && (!hasPowerInput || powered)
    lateinit var lever: Body

    override fun build(world: World) {
        val plate = Body(PolygonShape.rect(x, y + 14, x + w, y + h), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "switch")
        bodies.add(world.add(plate))
        lever = Body(PolygonShape.rect(x + 4, y + 2, x + w - 4, y + 14), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "switch-lever")
        bodies.add(world.add(lever))
    }
    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (other.isDynamic && !on) trigger()
    }
    override fun trigger() {
        if (on) return
        on = true
        machine.sounds.add("click")
        machine.effects.add(Effect(Effect.Kind.SPARKLE, Vec2(cx, y + 6), 0.4, 0.6))
    }
    override fun draw(p: Painter, t: Double) {
        Draw.outlinedRoundRect(p, x, y + 14, w, h - 14, 3.0, Style.CREAM)
        p.fillRoundRect(x + 6, y + 20, w - 12, 12.0, 2.0, Style.GREY_LIGHT)
        p.strokeRoundRect(x + 6, y + 20, w - 12, 12.0, 2.0, Style.OUTLINE, 1.2)
        // lever
        val lx = cx; val ly = y + 26
        val tipY = if (on) ly - 22 else ly - 10
        val tipX = if (on) lx else lx - 6
        p.line(lx, ly, tipX, tipY, Style.OUTLINE, 6.0)
        p.line(lx, ly, tipX, tipY, if (on) Style.GREEN else Style.RED, 3.5)
        p.fillCircle(tipX, tipY, 4.0, if (on) Style.GREEN else Style.RED)
        p.strokeCircle(tipX, tipY, 4.0, Style.OUTLINE, 1.5)
    }
}

/** Wall outlet: always live. */
class Outlet(placement: Placement, index: Int) : Part(placement, index) {
    override val powerOutput: Boolean get() = true
    override fun build(world: World) {
        val b = Body(PolygonShape.rect(x, y, x + w, y + h), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "outlet")
        bodies.add(world.add(b))
    }
    override fun draw(p: Painter, t: Double) {
        Draw.outlinedRoundRect(p, x, y, w, h, 3.0, Style.CREAM)
        for (oy in listOf(y + 8, y + 20)) {
            p.fillRoundRect(x + 5, oy, 14.0, 8.0, 2.0, Style.GREY_LIGHT)
            p.fillRect(x + 8, oy + 2, 2.0, 4.0, Style.OUTLINE)
            p.fillRect(x + 14, oy + 2, 2.0, 4.0, Style.OUTLINE)
        }
    }
}

/** Flashlight: lights up when its button is pressed or when powered. */
class Flashlight(placement: Placement, index: Int) : Part(placement, index), Activatable {
    var on = false
    override val activated get() = on
    lateinit var button: Body
    override fun build(world: World) {
        val b = Body(PolygonShape.rect(x, y + 6, x + w, y + h), Vec2.ZERO, BodyKind.STATIC, friction = 0.5, owner = this, tag = "flashlight")
        bodies.add(world.add(b))
        button = Body(PolygonShape.rect(x + (if (flipped) w - 18 else 10.0), y + 2, x + (if (flipped) w - 10 else 18.0), y + 6), Vec2.ZERO, BodyKind.STATIC, owner = this, tag = "flashlight-button")
        bodies.add(world.add(button))
    }
    override fun onContact(self: Body, other: Body, ev: ContactEvent) { if (self === button && other.isDynamic) trigger() }
    override fun trigger() { if (!on) { on = true; machine.sounds.add("click") } }
    override fun preStep() { if (hasPowerInput && powered) on = true }
    fun beam(): AABB = if (flipped) AABB(x - 160, y, x, y + h) else AABB(x + w, y, x + w + 160, y + h)
    override fun draw(p: Painter, t: Double) {
        p.save()
        if (flipped) { p.translate(x + w, 0.0); p.scale(-1.0, 1.0); p.translate(-x, 0.0) }
        if (on) {
            val beam = Path.polygon(x + w, y + 8, x + w + 160, y - 30, x + w + 160, y + h + 30, x + w, y + h - 2)
            p.alpha = 0.35; p.fillPath(beam, Style.YELLOW); p.alpha = 1.0
        }
        Draw.outlinedRoundRect(p, x, y + 8, w - 12, h - 10, 4.0, Style.BLUE)
        val head = Path.polygon(x + w - 14, y + 8, x + w, y + 6, x + w, y + h, x + w - 14, y + h - 2)
        Draw.outlinedPath(p, head, Style.GREY_DARK)
        p.fillRect(x + w - 3, y + 8, 3.0, h - 10, if (on) Style.YELLOW else Style.GREY_LIGHT)
        Draw.outlinedRoundRect(p, x + 10, y + 2, 8.0, 6.0, 2.0, if (on) Style.GREEN else Style.RED, 1.2)
        p.restore()
    }
}

/** Basketball hoop: counts balls that fall through the rim. */
class Hoop(placement: Placement, index: Int) : Part(placement, index), Container {
    private val scored = ArrayList<Part>()
    override val contents: List<Part> get() = scored
    private var swish = 0.0
    lateinit var rimZone: Body
    override fun build(world: World) {
        // backboard on the far side, rim as a thin static bar at each end
        val boardX = if (flipped) x + w - 6 else x
        val board = Body(PolygonShape.rect(boardX, y, boardX + 6, y + 30), Vec2.ZERO, BodyKind.STATIC, restitution = 0.5, friction = 0.3, owner = this, tag = "backboard")
        bodies.add(world.add(board))
        val rimFarX = if (flipped) x + 2 else x + w - 8
        val rimTip = Body(PolygonShape.rect(rimFarX, y + 20, rimFarX + 6, y + 24), Vec2.ZERO, BodyKind.STATIC, restitution = 0.4, friction = 0.3, owner = this, tag = "rim")
        bodies.add(world.add(rimTip))
        rimZone = Body(PolygonShape.rect(x + 8, y + 24, x + w - 8, y + h), Vec2.ZERO, BodyKind.STATIC, owner = this, tag = "net")
        rimZone.isSensor = true
        bodies.add(world.add(rimZone))
    }
    override fun onSensor(self: Body, other: Body) {
        if (self !== rimZone || !other.isDynamic || other.vel.y < 20) return
        val part = machine.partOf(other) ?: return
        if (part in scored) return
        scored.add(part)
        swish = 1.0
        machine.effects.add(Effect(Effect.Kind.STARBURST, Vec2(cx, y + 26), 0.6, 1.0))
        machine.sounds.add("swish")
    }
    override fun postStep() { swish *= 0.92 }
    override fun draw(p: Painter, t: Double) {
        p.save()
        if (flipped) { p.translate(x + w, 0.0); p.scale(-1.0, 1.0); p.translate(-x, 0.0) }
        Draw.outlinedRoundRect(p, x, y, 8.0, 32.0, 2.0, Style.WHITE)
        p.strokeRect(x + 2, y + 8, 4.0, 14.0, Style.RED, 1.2)
        // rim
        p.fillRoundRect(x + 4, y + 20, w - 6, 4.0, 2.0, Style.ORANGE)
        p.strokeRoundRect(x + 4, y + 20, w - 6, 4.0, 2.0, Style.OUTLINE, 1.5)
        // net
        val sw = swish * 4
        for (i in 0..4) {
            val nx = x + 8 + i * (w - 16) / 4
            val bx = x + 12 + i * (w - 24) / 4
            p.line(nx, y + 24, bx, y + h - 4 + sw, Style.GREY, 1.5)
        }
        for (j in 1..2) {
            val yy = y + 24 + j * (h - 28) / 3
            p.line(x + 9 + j, yy, x + w - 9 - j, yy, Style.GREY, 1.2)
        }
        p.restore()
    }
}

/** Bell: rings when something hits it. */
class Bell(placement: Placement, index: Int) : Part(placement, index), Activatable {
    var rung = false
    override val activated get() = rung
    private var swing = 0.0
    override fun build(world: World) {
        val b = Body(PolygonShape(listOf(Vec2(x + 6, y + h - 8), Vec2(cx - 6, y + 6), Vec2(cx + 6, y + 6), Vec2(x + w - 6, y + h - 8), Vec2(x + w - 6, y + h - 2), Vec2(x + 6, y + h - 2))), Vec2.ZERO, BodyKind.STATIC, restitution = 0.4, friction = 0.3, owner = this, tag = "bell")
        bodies.add(world.add(b))
    }
    override fun onContact(self: Body, other: Body, ev: ContactEvent) {
        if (other.isDynamic && ev.relativeSpeed > 30) ring()
    }
    fun ring() {
        swing = 1.0
        if (!rung) { rung = true; machine.effects.add(Effect(Effect.Kind.RING, Vec2(cx, cy), 0.6, 1.5)); machine.effects.add(Effect(Effect.Kind.STARBURST, Vec2(cx, y), 0.7, 1.0)) }
        machine.sounds.add("ding")
    }
    override fun postStep() { swing *= 0.9 }
    override fun draw(p: Painter, t: Double) {
        val ang = if (built) swing * StrictMath.sin(machine.time * 25) * 0.2 else 0.0
        p.save(); p.translate(cx, y + 4); p.rotate(ang); p.translate(-cx, -(y + 4))
        p.fillCircle(cx, y + 4, 3.0, Style.OUTLINE)
        val bell = Path()
        bell.moveTo(cx - 5, y + 6); bell.quadTo(cx - 16, y + 12, cx - 16, y + h - 12); bell.lineTo(cx - 19, y + h - 6)
        bell.lineTo(cx + 19, y + h - 6); bell.lineTo(cx + 16, y + h - 12); bell.quadTo(cx + 16, y + 12, cx + 5, y + 6); bell.close()
        p.fillPath(bell, Style.YELLOW)
        p.strokePath(bell, Style.OUTLINE, Style.LINE)
        p.line(cx - 8, y + 14, cx - 9, y + h - 14, Colors.withAlpha(Style.WHITE, 0.5), 2.5)
        p.fillCircle(cx, y + h - 3, 4.0, Style.OUTLINE)
        p.restore()
    }
}

/** Star: a target that lights up when anything touches it. */
class Star(placement: Placement, index: Int) : Part(placement, index), Activatable {
    var touched = false
    override val activated get() = touched
    override fun build(world: World) {
        val b = Body(PolygonShape.box(w / 2 - 2, h / 2 - 2), Vec2(cx, cy), BodyKind.STATIC, owner = this, tag = "star")
        b.isSensor = true
        bodies.add(world.add(b))
    }
    override fun onSensor(self: Body, other: Body) {
        if (!other.isDynamic || touched) return
        touched = true
        machine.effects.add(Effect(Effect.Kind.STARBURST, Vec2(cx, cy), 0.8, 1.4))
        machine.sounds.add("chime")
    }
    override fun draw(p: Painter, t: Double) {
        val r = w / 2 - 1
        val spin = if (touched) t * 2 else 0.0
        p.save(); p.translate(cx, cy); p.rotate(spin)
        val path = Path()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * Math.PI / 5
            val rr = if (i % 2 == 0) r else r * 0.45
            val px = StrictMath.cos(a) * rr; val py = StrictMath.sin(a) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        if (!touched) { p.alpha = 0.75 }
        p.fillPath(path, if (touched) Style.YELLOW else Colors.lighten(Style.YELLOW, 0.3))
        p.strokePath(path, Style.OUTLINE, Style.LINE)
        p.alpha = 1.0
        p.restore()
    }
}
