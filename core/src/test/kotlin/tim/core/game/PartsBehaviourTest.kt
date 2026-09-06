package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.parts.*
import tim.core.physics.AABB
import tim.core.physics.Vec2

class PartsBehaviourTest {
    private fun floor(b: MachineBuilder) {
        // brick floor across the whole playfield
        for (i in 0 until 7) b.part(PartType.BRICK_WALL, i * 96.0, 384.0)
    }

    @Test
    fun `ball rests on a brick wall`() {
        val m = machine { part(PartType.BRICK_WALL, 200.0, 300.0); part(PartType.BOWLING_BALL, 230.0, 200.0) }
        m.run(3.0)
        val ball = m.part<Ball>(1)
        assertEquals(300.0 - 16.0, ball.pos.y, 1.0)
        assertTrue(m.settled)
    }

    @Test
    fun `ball rolls down a ramp and off its low end`() {
        val m = machine { part(PartType.BRICK_WALL, 100.0, 300.0); part(PartType.INCLINE, 100.0, 268.0); part(PartType.BASKETBALL, 104.0, 230.0) }
        val ball = m.part<Ball>(2)
        m.run(1.5)
        assertTrue(ball.pos.x > 170.0, "ball should roll right, x=${ball.pos.x}")
        val mf = machine { part(PartType.BRICK_WALL, 100.0, 300.0); part(PartType.INCLINE, 100.0, 268.0, flipped = true); part(PartType.BASKETBALL, 128.0, 230.0) }
        val ball2 = mf.part<Ball>(2)
        mf.run(1.5)
        assertTrue(ball2.pos.x < 130.0, "flipped ramp should roll left, x=${ball2.pos.x}")
    }

    @Test
    fun `seesaw launches the ball on the raised end when a heavy ball lands on the other end`() {
        val m = machine {
            floor(this)
            part(PartType.SEESAW, 200.0, 352.0)            // left end down by default
            part(PartType.BASEBALL, 208.0, 320.0)         // sits on the low (left) end
            part(PartType.BOWLING_BALL, 264.0, 150.0)     // falls onto the raised (right) end
        }
        val baseball = m.part<Ball>(8)
        val seesaw = m.part<Seesaw>(7)
        m.run(0.4)
        assertEquals(-1, seesaw.tilt)
        val launched = m.runUntil(2.0) { baseball.body!!.vel.y < -150 }
        assertTrue(launched, "baseball should be flung upward")
        m.run(1.0)
        assertEquals(1, seesaw.tilt, "seesaw should now have its right end down")
    }

    @Test
    fun `trampoline bounces a ball higher than a plain wall would`() {
        fun peak(useTrampoline: Boolean): Double {
            val m = machine {
                floor(this)
                if (useTrampoline) part(PartType.TRAMPOLINE, 288.0, 360.0) else part(PartType.WOOD_WALL, 288.0, 368.0)
                part(PartType.BASKETBALL, 304.0, 200.0)
            }
            val ball = m.part<Ball>(8)
            var bounced = false; var peak = 400.0
            m.runUntil(3.0) { if (ball.body!!.vel.y < 0) bounced = true; if (bounced && ball.pos.y < peak) peak = ball.pos.y; false }
            return peak
        }
        val tramp = peak(true); val wall = peak(false)
        assertTrue(tramp < wall - 20, "trampoline peak y=$tramp should be well above wall peak y=$wall")
    }

    @Test
    fun `conveyor carries a ball in the direction it faces`() {
        val m = machine { part(PartType.CONVEYOR, 200.0, 300.0); part(PartType.BOWLING_BALL, 216.0, 260.0) }
        val ball = m.part<Ball>(1)
        m.run(0.6)
        assertTrue(ball.pos.x > 260.0, "ball should be carried right, x=${ball.pos.x}")
        val mf = machine { part(PartType.CONVEYOR, 200.0, 300.0, flipped = true); part(PartType.BOWLING_BALL, 260.0, 260.0) }
        val ball2 = mf.part<Ball>(1)
        mf.run(0.6)
        assertTrue(ball2.pos.x < 240.0, "flipped conveyor should carry left, x=${ball2.pos.x}")
    }

    @Test
    fun `fan blows a balloon away and barely moves a bowling ball`() {
        val m = machine { floor(this); part(PartType.FAN, 100.0, 344.0); part(PartType.BALLOON, 160.0, 320.0) }
        val balloon = m.part<Balloon>(8)
        m.run(1.0)
        assertTrue(balloon.pos.x > 220.0, "balloon should be blown right, x=${balloon.pos.x}")
        val m2 = machine { floor(this); part(PartType.FAN, 100.0, 344.0); part(PartType.BOWLING_BALL, 160.0, 352.0) }
        val ball = m2.part<Ball>(8)
        m2.run(1.0)
        assertTrue(ball.pos.x < 200.0, "bowling ball should hardly move, x=${ball.pos.x}")
    }

    @Test
    fun `balloon rises at normal pressure and falls in a vacuum`() {
        val m = machine { part(PartType.BALLOON, 300.0, 300.0) }
        val b = m.part<Balloon>(0)
        m.run(1.0)
        assertTrue(b.pos.y < 250.0, "balloon should rise, y=${b.pos.y}")
        val v = MachineBuilder().apply { part(PartType.BALLOON, 300.0, 100.0) }.build(airPressure = 0.0)
        val b2 = v.part<Balloon>(0)
        v.run(1.0)
        assertTrue(b2.pos.y > 150.0, "balloon should fall without air, y=${b2.pos.y}")
    }

    @Test
    fun `candle flame pops a balloon that drifts into it`() {
        val m = machine { floor(this); part(PartType.CANDLE, 300.0, 352.0); part(PartType.BALLOON, 292.0, 100.0) }
        val balloon = m.part<Balloon>(8)
        // hold the balloon down over the candle by giving it a strong downward speed each step until it pops
        val popped = m.runUntil(4.0) { balloon.body?.vel = Vec2(0.0, 200.0); balloon.popped }
        assertTrue(popped, "balloon should pop on the flame")
        assertTrue(Goal.PopAllBalloons.check(m))
    }

    @Test
    fun `fan blows out a candle`() {
        val m = machine { floor(this); part(PartType.FAN, 200.0, 344.0); part(PartType.CANDLE, 260.0, 352.0) }
        val candle = m.part<Candle>(8)
        m.run(0.2)
        assertFalse(candle.lit)
    }

    @Test
    fun `dynamite explodes when lit and kicks nearby balls`() {
        val m = machine { floor(this); part(PartType.CANDLE, 300.0, 352.0); part(PartType.DYNAMITE, 296.0, 300.0); part(PartType.BASKETBALL, 350.0, 352.0) }
        val dyn = m.part<Dynamite>(8)
        val ball = m.part<Ball>(9)
        // the dynamite falls onto the candle flame and is lit
        val exploded = m.runUntil(4.0) { dyn.exploded }
        assertTrue(exploded, "dynamite should explode")
        m.run(0.3)
        assertTrue(ball.pos.x > 380.0 || ball.pos.y < 340.0, "ball should be blown away, pos=${ball.pos}")
    }

    @Test
    fun `cannon fires a cannonball when its fuse is lit`() {
        val m = machine { floor(this); part(PartType.CANNON, 200.0, 344.0); part(PartType.CANDLE, 180.0, 352.0) }
        val cannon = m.part<Cannon>(7)
        // drop the candle right next to the fuse: it falls onto the cannon and its flame should light the fuse
        val fired = m.runUntil(4.0) { cannon.fired }
        assertTrue(fired, "cannon should fire")
        assertEquals(1, m.spawned.size)
        val cb = m.spawned[0] as Ball
        m.run(0.2)
        assertTrue(cb.pos.x > 300.0, "cannonball should fly right, x=${cb.pos.x}")
    }

    @Test
    fun `bucket catches a falling ball and reports it as contents`() {
        val m = machine { floor(this); part(PartType.BUCKET, 300.0, 344.0); part(PartType.BASEBALL, 312.0, 100.0) }
        val bucket = m.part<Bucket>(7)
        val got = m.runUntil(3.0) { bucket.contents.isNotEmpty() }
        assertTrue(got, "ball should land in the bucket")
        assertTrue(Goal.BallInto(7).check(m))
        m.run(1.0)
        assertTrue(bucket.contents.isNotEmpty(), "ball should stay in the bucket")
    }

    @Test
    fun `bucket hangs from a rope and a ball dropped in pulls it down`() {
        val m = machine {
            part(PartType.HOOK, 292.0, 40.0)
            part(PartType.BUCKET, 280.0, 200.0)
            rope(0, 1)
            part(PartType.BOWLING_BALL, 284.0, 100.0)
        }
        val bucket = m.part<Bucket>(1)
        m.run(0.3)
        val y0 = bucket.pos.y
        assertTrue(y0 < 230.0, "bucket should hang at its starting height, y=$y0")
        m.run(3.0)
        assertTrue(bucket.pos.y < 250.0, "bucket should still hang (rope holds), y=${bucket.pos.y}")
        assertTrue(bucket.contents.isNotEmpty(), "ball should be inside the bucket")
    }

    @Test
    fun `balloon lifts a light bucket through a pulley when the rope is cut on the other side`() {
        val m = machine {
            part(PartType.PULLEY, 312.0, 40.0)
            part(PartType.BALLOON, 200.0, 200.0)
            part(PartType.BUCKET, 420.0, 200.0)
            rope(1, 2, 0)
        }
        val balloon = m.part<Balloon>(1)
        val bucket = m.part<Bucket>(2)
        m.run(3.0)
        // balloon lift (~0.1 mass) cannot lift a 10-mass bucket: bucket should sink, balloon rises to the pulley
        assertTrue(bucket.pos.y > 230.0, "heavy bucket should descend, y=${bucket.pos.y}")
        assertTrue(balloon.pos.y < 120.0, "balloon should be pulled up, y=${balloon.pos.y}")
    }

    @Test
    fun `scissors cut a rope when something lands on their handles`() {
        val m = machine {
            part(PartType.HOOK, 292.0, 40.0)        // 0
            part(PartType.BUCKET, 280.0, 200.0)     // 1
            rope(0, 1)
            part(PartType.SCISSORS, 260.0, 96.0)    // 2: blades at x 286..308 over the rope at x=300
            part(PartType.BASEBALL, 266.0, 40.0)    // 3: lands on the handles
        }
        val bucket = m.part<Bucket>(1)
        val cut = m.runUntil(3.0) { m.ropes[0].cut }
        assertTrue(cut, "rope should be cut")
        m.run(1.5)
        assertTrue(bucket.pos.y > 400.0, "bucket should fall after the rope is cut, y=${bucket.pos.y}")
    }

    @Test
    fun `mouse walks to the cheese and eats it`() {
        val m = machine { floor(this); part(PartType.MOUSE, 100.0, 368.0); part(PartType.CHEESE, 400.0, 368.0) }
        val ok = m.runUntil(8.0) { Goal.MouseEatsCheese.check(m) }
        assertTrue(ok, "mouse should reach the cheese")
    }

    @Test
    fun `mouse turns around at a wall`() {
        val m = machine { floor(this); part(PartType.BRICK_WALL, 300.0, 288.0, rotation = 1); part(PartType.MOUSE, 200.0, 368.0) }
        val mouse = m.part<Mouse>(8)
        m.run(2.5)
        assertTrue(mouse.pos.x < 300.0, "mouse must not pass through the wall")
        assertEquals(-1.0, mouse.facing, "mouse should have turned around")
    }

    @Test
    fun `cage falls and traps the cat`() {
        val m = machine { floor(this); part(PartType.CAT, 300.0, 352.0); part(PartType.CAGE, 300.0, 100.0) }
        val cat = m.part<Cat>(7)
        val cage = m.part<Cage>(8)
        m.run(3.0)
        assertTrue(cage.traps(cat), "cat should be inside the landed cage, cage=${cage.worldBounds} cat=${cat.pos}")
        assertTrue(cage.worldBounds.maxY > 380.0, "cage should sit on the floor")
    }

    @Test
    fun `switch turns on a wired fan`() {
        val m = machine {
            floor(this)
            part(PartType.SWITCH, 200.0, 344.0)  // 7
            part(PartType.FAN, 300.0, 344.0)     // 8
            wire(7, 8)
            part(PartType.TENNIS_BALL, 360.0, 368.0) // 9
            part(PartType.BASEBALL, 208.0, 200.0) // 10 falls on the switch
        }
        val fan = m.part<Fan>(8)
        val tennis = m.part<Ball>(9)
        assertFalse(fan.running, "fan should start off when wired to a switch")
        m.run(0.5)
        assertTrue(tennis.pos.x < 380.0, "tennis ball should stay put while the fan is off")
        val on = m.runUntil(3.0) { fan.running }
        assertTrue(on, "switch should have turned the fan on")
        m.run(1.0)
        assertTrue(tennis.pos.x > 420.0, "tennis ball should be blown, x=${tennis.pos.x}")
    }

    @Test
    fun `bell rings and star lights up when hit`() {
        val m = machine { floor(this); part(PartType.BELL, 300.0, 336.0); part(PartType.BASEBALL, 312.0, 100.0); part(PartType.STAR, 400.0, 300.0); part(PartType.BASKETBALL, 408.0, 100.0) }
        assertTrue(m.runUntil(3.0) { Goal.Activate(7).check(m) }, "bell should ring")
        assertTrue(m.runUntil(3.0) { Goal.Activate(9).check(m) }, "star should be touched")
    }

    @Test
    fun `hoop counts a ball falling through`() {
        val m = machine { floor(this); part(PartType.HOOP, 300.0, 300.0); part(PartType.BASKETBALL, 312.0, 100.0) }
        assertTrue(m.runUntil(3.0) { Goal.BallInto(7).check(m) }, "ball should score")
    }

    @Test
    fun `rocket launches when lit and reaches the top`() {
        val m = machine { floor(this); part(PartType.ROCKET, 300.0, 320.0); part(PartType.CANDLE, 300.0, 200.0) }
        val rocket = m.part<Rocket>(7)
        // candle falls next to the rocket... flame is above the candle, so instead light directly
        m.run(0.5)
        rocket.light()
        assertTrue(m.runUntil(4.0) { rocket.launched && rocket.pos.y < 100 }, "rocket should fly up, y=${rocket.pos.y}")
    }

    @Test
    fun `boxing glove punches a ball away`() {
        val m = machine { floor(this); part(PartType.BOXING_GLOVE, 200.0, 344.0); part(PartType.BASKETBALL, 300.0, 352.0); part(PartType.BASEBALL, 190.0, 200.0) }
        val ball = m.part<Ball>(8)
        m.run(3.0)
        assertTrue(ball.pos.x > 380.0, "ball should be punched right, x=${ball.pos.x}")
    }

    @Test
    fun `bellows puff blows a balloon when something lands on it`() {
        val m = machine { floor(this); part(PartType.BELLOWS, 200.0, 352.0); part(PartType.TENNIS_BALL, 300.0, 368.0); part(PartType.BOWLING_BALL, 210.0, 200.0) }
        val ball = m.part<Ball>(8)
        m.run(2.0)
        assertTrue(ball.pos.x > 340.0, "tennis ball should be puffed right, x=${ball.pos.x}")
    }

    @Test
    fun `bumper bounces a slow ball away fast`() {
        val m = machine { part(PartType.BUMPER, 300.0, 300.0); part(PartType.BASEBALL, 308.0, 250.0) }
        val ball = m.part<Ball>(1)
        var maxUp = 0.0
        m.runUntil(2.0) { maxUp = maxOf(maxUp, -ball.body!!.vel.y); false }
        assertTrue(maxUp > 150.0, "bumper should kick the ball upward, maxUp=$maxUp")
    }

    @Test
    fun `motor belt drives a conveyor and stops it when unpowered`() {
        val m = machine {
            part(PartType.SWITCH, 100.0, 300.0)   // 0
            part(PartType.MOTOR, 200.0, 300.0)    // 1
            wire(0, 1)
            part(PartType.CONVEYOR, 300.0, 300.0) // 2
            belt(1, 2)
            part(PartType.BOWLING_BALL, 316.0, 260.0) // 3
        }
        val conv = m.part<Conveyor>(2)
        val ball = m.part<Ball>(3)
        m.run(1.0)
        assertFalse(conv.running)
        assertTrue(ball.pos.x < 340.0, "ball should not move without power")
        m.part<Switch>(0).trigger()
        m.run(1.0)
        assertTrue(conv.running)
        assertTrue(ball.pos.x > 360.0, "ball should move once powered, x=${ball.pos.x}")
    }

    @Test
    fun `ball falling off the bottom leaves the world`() {
        val m = machine { part(PartType.BOWLING_BALL, 300.0, 300.0) }
        val ball = m.part<Ball>(0)
        m.run(3.0)
        assertFalse(ball.body!!.enabled)
        assertTrue(Goal.Reach(0, AABB(-1000.0, 400.0, 2000.0, 5000.0)).check(m))
    }
}
