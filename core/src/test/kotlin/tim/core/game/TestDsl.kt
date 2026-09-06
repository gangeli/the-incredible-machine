package tim.core.game

import tim.core.physics.World

/** Small helpers for building machines in tests. */
class MachineBuilder {
    val fixed = ArrayList<Placement>()
    val links = ArrayList<Link>()
    fun part(type: PartType, x: Double, y: Double, flipped: Boolean = false, rotation: Int = 0): Int {
        fixed.add(Placement(type, x, y, flipped, rotation)); return fixed.size - 1
    }
    fun rope(from: Int, to: Int, vararg via: Int, slack: Double = 0.0) { links.add(Link(LinkKind.ROPE, from, to, via.toList(), slack)) }
    fun belt(from: Int, to: Int) { links.add(Link(LinkKind.BELT, from, to)) }
    fun wire(from: Int, to: Int) { links.add(Link(LinkKind.WIRE, from, to)) }
    fun build(gravity: Double = World.DEFAULT_GRAVITY, airPressure: Double = 1.0) = Machine(Board(fixed, links), gravity = gravity, airPressure = airPressure)
}

fun machine(block: MachineBuilder.() -> Unit): Machine = MachineBuilder().apply(block).build()

fun Machine.run(seconds: Double) { repeat((seconds / World.STEP).toInt()) { step() } }

/** Steps until [cond] is true or [seconds] elapse; returns true if the condition was met. */
fun Machine.runUntil(seconds: Double, cond: (Machine) -> Boolean): Boolean {
    repeat((seconds / World.STEP).toInt()) { if (cond(this)) return true; step() }
    return cond(this)
}

inline fun <reified T : Part> Machine.part(index: Int): T = parts[index] as T
