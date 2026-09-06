package tim.core.physics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class WorldTest {
    private fun world() = World(width = 800.0, height = 500.0).also { it.addBounds() }

    private fun ball(w: World, x: Double, y: Double, r: Double = 15.0, rest: Double = 0.3, mass: Double = 1.0) =
        w.add(Body(CircleShape(r), Vec2(x, y), BodyKind.DYNAMIC, mass = mass, restitution = rest, tag = "ball"))

    private fun run(w: World, seconds: Double) { repeat((seconds / World.STEP).toInt()) { w.step() } }

    @Test
    fun `ball falls under gravity and comes to rest on the floor`() {
        val w = world()
        val b = ball(w, 400.0, 100.0)
        run(w, 4.0)
        assertEquals(500.0 - 15.0, b.pos.y, 1.0, "ball should rest on the floor")
        assertTrue(b.vel.length < 1.0, "ball should be still, v=${b.vel}")
        assertTrue(b.grounded)
        assertTrue(w.allAtRest())
    }

    @Test
    fun `restitution controls bounce height`() {
        fun peakAfterBounce(rest: Double): Double {
            val w = world()
            val b = ball(w, 400.0, 100.0, rest = rest)
            var bounced = false
            var peak = 500.0
            repeat(300) {
                w.step()
                if (b.vel.y < 0) bounced = true
                if (bounced && b.pos.y < peak) peak = b.pos.y
            }
            return 500.0 - 15.0 - peak
        }
        val low = peakAfterBounce(0.2)
        val high = peakAfterBounce(0.8)
        assertTrue(high > low * 3, "bouncier ball should rebound higher: $high vs $low")
        assertTrue(high < 400.0 - 15.0, "cannot bounce above the drop height: $high")
    }

    @Test
    fun `ball rolls down a ramp to the right`() {
        val w = world()
        // right triangle ramp: high on the left, low on the right
        w.add(Body(PolygonShape(listOf(Vec2(200.0, 300.0), Vec2(400.0, 400.0), Vec2(200.0, 400.0))), Vec2.ZERO, BodyKind.STATIC, tag = "ramp"))
        val b = ball(w, 215.0, 270.0)
        run(w, 2.0)
        assertTrue(b.pos.x > 420.0, "ball should have rolled off to the right, x=${b.pos.x}")
    }

    @Test
    fun `conveyor surface speed moves a resting ball`() {
        val w = world()
        val belt = w.add(Body(PolygonShape.rect(100.0, 300.0, 700.0, 330.0), Vec2.ZERO, BodyKind.STATIC, tag = "belt", friction = 0.9))
        belt.surfaceSpeed = 80.0
        val b = ball(w, 250.0, 270.0)
        run(w, 1.5)
        assertTrue(b.pos.x > 300.0, "ball should be carried right by the belt, x=${b.pos.x}")
        assertEquals(80.0, b.vel.x, 5.0, "ball should travel at belt speed")
        val xBefore = b.pos.x
        belt.surfaceSpeed = -80.0
        run(w, 1.5)
        assertTrue(b.pos.x < xBefore - 50.0, "ball should be carried left after reversing, x=${b.pos.x}")
    }

    @Test
    fun `dynamic box rests on a static box`() {
        val w = world()
        w.add(Body(PolygonShape.rect(300.0, 400.0, 500.0, 430.0), Vec2.ZERO, BodyKind.STATIC, tag = "shelf"))
        val box = w.add(Body(PolygonShape.box(20.0, 15.0), Vec2(400.0, 300.0), BodyKind.DYNAMIC, tag = "box"))
        run(w, 2.0)
        assertEquals(400.0 - 15.0, box.pos.y, 1.0)
        assertTrue(box.grounded)
        assertTrue(abs(box.pos.x - 400.0) < 0.5)
    }

    @Test
    fun `rope holds a hanging ball like a pendulum`() {
        val w = world()
        val hook = w.add(Body(PolygonShape.box(5.0, 5.0), Vec2(400.0, 100.0), BodyKind.STATIC, tag = "hook"))
        val b = ball(w, 480.0, 100.0)
        w.add(Rope(hook, Vec2.ZERO, b, Vec2.ZERO, maxLength = 100.0))
        run(w, 3.0)
        val d = b.pos.distanceTo(Vec2(400.0, 100.0))
        assertTrue(d <= 102.0, "rope should keep ball within length, d=$d")
        assertTrue(b.pos.y < 400.0, "ball should hang, not fall to the floor, y=${b.pos.y}")
    }

    @Test
    fun `rope over a pulley lifts a lighter weight when a heavier one drops`() {
        val w = world()
        val heavy = ball(w, 300.0, 200.0, mass = 5.0)
        val light = ball(w, 500.0, 200.0, mass = 1.0)
        // pulley at (400, 50); rope length: 300->pulley (~180) + pulley->500 (~180)
        val len = heavy.pos.distanceTo(Vec2(400.0, 50.0)) + light.pos.distanceTo(Vec2(400.0, 50.0))
        w.add(Rope(heavy, Vec2.ZERO, light, Vec2.ZERO, via = listOf(Vec2(400.0, 50.0)), maxLength = len))
        run(w, 1.0)
        assertTrue(heavy.pos.y > 250.0, "heavy ball should descend, y=${heavy.pos.y}")
        assertTrue(light.pos.y < 180.0, "light ball should be hoisted, y=${light.pos.y}")
    }

    @Test
    fun `kinematic rotating plank launches a ball`() {
        val w = world()
        val plank = w.add(Body(PolygonShape.rect(-80.0, -8.0, 80.0, 8.0), Vec2(400.0, 300.0), BodyKind.KINEMATIC, tag = "plank"))
        val b = ball(w, 470.0, 270.0)
        run(w, 0.5) // settle on plank
        assertTrue(b.grounded)
        plank.angVel = -6.0 // rotate counter-clockwise (right end goes up in y-down coords)
        run(w, 0.15)
        plank.angVel = 0.0
        assertTrue(b.vel.y < -100.0, "ball should be flung upward, vy=${b.vel.y}")
    }

    @Test
    fun `simulation is deterministic`() {
        fun trace(): List<Vec2> {
            val w = world()
            w.add(Body(PolygonShape(listOf(Vec2(200.0, 300.0), Vec2(400.0, 400.0), Vec2(200.0, 400.0))), Vec2.ZERO, BodyKind.STATIC))
            val balls = (0 until 5).map { ball(w, 220.0 + it * 3, 100.0 + it * 40, rest = 0.5 + it * 0.05) }
            run(w, 4.0)
            return balls.map { it.pos }
        }
        assertEquals(trace(), trace())
    }

    @Test
    fun `sensor reports overlaps without pushing`() {
        val w = world()
        val sensor = w.add(Body(PolygonShape.rect(350.0, 200.0, 450.0, 300.0), Vec2.ZERO, BodyKind.STATIC, tag = "sensor").also { it.isSensor = true })
        val b = ball(w, 400.0, 100.0)
        var seen = false
        run(w, 0.4)
        repeat(30) { w.step(); if (w.sensorEvents.any { it.sensor === sensor && it.other === b }) seen = true }
        assertTrue(seen, "sensor should have reported the ball passing through")
        run(w, 2.0)
        assertEquals(485.0, b.pos.y, 1.0, "sensor must not block the ball")
    }

    @Test
    fun `contact events carry impact speed`() {
        val w = world()
        val b = ball(w, 400.0, 100.0)
        var maxRel = 0.0
        run(w, 0.5)
        repeat(60) { w.step(); for (e in w.contactEvents) if (e.a === b || e.b === b) maxRel = maxOf(maxRel, e.relativeSpeed) }
        assertTrue(maxRel > 500.0, "impact speed should be large, was $maxRel")
    }
}
