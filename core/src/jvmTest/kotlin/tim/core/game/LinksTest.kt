package tim.core.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tim.core.game.parts.*

class LinksTest {
    private fun floor(b: MachineBuilder) { for (i in 0 until 7) b.part(PartType.BRICK_WALL, i * 96.0, 384.0) }

    @Test
    fun `link rules orient wires and belts from source to consumer and reject nonsense`() {
        val w = LinkRules.connect(LinkKind.WIRE, 3, PartType.FAN, 1, PartType.SWITCH)
        assertEquals(Link(LinkKind.WIRE, 1, 3), w)
        assertEquals(Link(LinkKind.WIRE, 0, 2), LinkRules.connect(LinkKind.WIRE, 0, PartType.OUTLET, 2, PartType.SWITCH))
        assertEquals(Link(LinkKind.WIRE, 2, 0), LinkRules.connect(LinkKind.WIRE, 0, PartType.SWITCH, 2, PartType.OUTLET), "an outlet feeds a switch whichever is tapped first")
        assertNull(LinkRules.connect(LinkKind.WIRE, 0, PartType.OUTLET, 2, PartType.OUTLET), "nothing powers an outlet")
        assertNull(LinkRules.connect(LinkKind.WIRE, 0, PartType.FAN, 2, PartType.FAN))
        assertEquals(Link(LinkKind.BELT, 5, 4), LinkRules.connect(LinkKind.BELT, 4, PartType.CONVEYOR, 5, PartType.MOTOR))
        assertNull(LinkRules.connect(LinkKind.BELT, 4, PartType.CONVEYOR, 5, PartType.CONVEYOR))
        assertEquals(Link(LinkKind.ROPE, 0, 1, listOf(2)), LinkRules.connect(LinkKind.ROPE, 0, PartType.HOOK, 1, PartType.BUCKET, listOf(2)))
        assertNull(LinkRules.connect(LinkKind.ROPE, 0, PartType.HOOK, 1, PartType.BOWLING_BALL))
        assertNull(LinkRules.connect(LinkKind.ROPE, 0, PartType.HOOK, 0, PartType.HOOK))
        assertTrue(LinkRules.candidate(LinkKind.ROPE, PartType.PULLEY))
        assertFalse(LinkRules.canStart(LinkKind.ROPE, PartType.PULLEY))
    }

    @Test
    fun `removing a player part drops its links and renumbers the rest`() {
        val b = Board(listOf(Placement(PartType.HOOK, 0.0, 0.0)), emptyList())
        b.playerParts.add(Placement(PartType.BUCKET, 100.0, 100.0))   // all index 1
        b.playerParts.add(Placement(PartType.PULLEY, 200.0, 0.0))     // 2
        b.playerParts.add(Placement(PartType.CAGE, 300.0, 100.0))     // 3
        b.playerLinks.add(Link(LinkKind.ROPE, 0, 1))
        b.playerLinks.add(Link(LinkKind.ROPE, 3, 0, listOf(2)))
        b.removePlayerPart(0) // the bucket
        assertEquals(listOf(Link(LinkKind.ROPE, 2, 0, listOf(1))), b.playerLinks)
        b.removePlayerPart(0) // the pulley: the remaining rope used it as a via point
        assertTrue(b.playerLinks.isEmpty())
        assertEquals(1, b.playerParts.size)
    }

    @Test
    fun `a heavy load on a rope tips the seesaw and launches what sat on the low end`() {
        val m = machine {
            floor(this)
            val seesaw = part(PartType.SEESAW, 200.0, 352.0)      // 7: left end low
            part(PartType.BASEBALL, 208.0, 320.0)                  // 8 on the low end
            val p1 = part(PartType.PULLEY, 200.0, 24.0)            // 9
            val p2 = part(PartType.PULLEY, 424.0, 24.0)            // 10
            val bucket = part(PartType.BUCKET, 408.0, 200.0)       // 11
            rope(seesaw, bucket, p1, p2)
            part(PartType.BOWLING_BALL, 416.0, 56.0)               // 12
        }
        val seesaw = m.part<Seesaw>(7)
        val ball = m.part<Ball>(8)
        val bucket = m.part<Bucket>(11)
        m.run(0.4)
        assertEquals(-1, seesaw.tilt)
        assertTrue(bucket.pos.y < 260, "bucket should hang from the rope, y=${bucket.pos.y}")
        val tipped = m.runUntil(4.0) { seesaw.tilt == 1 }
        assertTrue(tipped, "the loaded bucket should pull the seesaw over")
        m.run(0.3)
        assertTrue(ball.pos.y < 300, "baseball should have been launched, y=${ball.pos.y}")
    }

    @Test
    fun `a flipped seesaw starts with its right end down`() {
        val m = machine { floor(this); part(PartType.SEESAW, 200.0, 352.0, flipped = true) }
        assertEquals(1, m.part<Seesaw>(7).tilt)
    }

    @Test
    fun `parts marked as needing power stay off until wired or belted`() {
        val m = machine {
            floor(this)
            part(PartType.FAN, 300.0, 344.0)                                   // 7 plain
            fixed.add(Placement(PartType.FAN, 400.0, 344.0, needsPower = true))  // 8
            fixed.add(Placement(PartType.CONVEYOR, 100.0, 300.0, needsPower = true)) // 9
            part(PartType.OUTLET, 500.0, 352.0)                                // 10
            part(PartType.MOTOR, 200.0, 300.0)                                 // 11
        }
        assertTrue(m.part<Fan>(7).running)
        assertFalse(m.part<Fan>(8).running)
        assertFalse(m.part<Conveyor>(9).running)
        val m2 = machine {
            floor(this)
            val fan = fixed.let { it.add(Placement(PartType.FAN, 400.0, 344.0, needsPower = true)); it.size - 1 }  // 7
            val conv = fixed.let { it.add(Placement(PartType.CONVEYOR, 100.0, 300.0, needsPower = true)); it.size - 1 } // 8
            val outlet = part(PartType.OUTLET, 500.0, 352.0)   // 9
            val motor = part(PartType.MOTOR, 200.0, 300.0)     // 10
            wire(outlet, fan); wire(outlet, motor); belt(motor, conv)
        }
        m2.step()
        assertTrue(m2.part<Fan>(7).running)
        assertTrue(m2.part<Conveyor>(8).running)
    }

    @Test
    fun `belted conveyors run the way the motor faces`() {
        val m = machine {
            floor(this)
            val motor = part(PartType.MOTOR, 100.0, 344.0, flipped = true)          // 7 faces left
            val conv = fixed.let { it.add(Placement(PartType.CONVEYOR, 240.0, 300.0, flipped = false, needsPower = true)); it.size - 1 } // 8 would face right
            belt(motor, conv)
            part(PartType.BOWLING_BALL, 272.0, 260.0) // 9
        }
        m.run(0.8)
        assertTrue(m.part<Ball>(9).pos.x < 280, "ball should be carried left by the motor's direction, x=${m.part<Ball>(9).pos.x}")
    }
}
