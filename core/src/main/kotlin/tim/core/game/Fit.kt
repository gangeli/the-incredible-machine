package tim.core.game

import tim.core.game.parts.PartFactory
import tim.core.physics.Category
import tim.core.physics.Collision
import tim.core.physics.Vec2
import tim.core.physics.World

/**
 * Placement overlap rules. Bounding boxes are only a first check: like the original game, two
 * parts may share a bounding box as long as their solid shapes do not intersect, so a ball can
 * sit on a seesaw plank or on the slope of a ramp.
 */
object Fit {
    /** Solid shapes may interpenetrate this much and still count as touching rather than overlapping. */
    const val TOLERANCE = 2.0

    fun overlap(a: Placement, b: Placement): Boolean {
        if (!a.overlaps(b)) return false
        if (a.type.isTool || b.type.isTool) return false
        val world = World(Vec2.ZERO, Machine.WIDTH, Machine.HEIGHT)
        val pa = PartFactory.create(a, 0).also { it.build(world); it.built = true }
        val pb = PartFactory.create(b, 1).also { it.build(world); it.built = true }
        val solidsA = pa.bodies.filter { !it.isSensor && it.category != Category.ROPE }
        val solidsB = pb.bodies.filter { !it.isSensor && it.category != Category.ROPE }
        // parts with no solid body at all (pulleys, hooks) only take their bounding box
        if (solidsA.isEmpty() || solidsB.isEmpty()) return true
        for (x in solidsA) for (y in solidsB) {
            // bodies that never collide in the simulation (a cage's frame and a creature, say) may share space
            if ((x.category and y.mask) == 0 || (y.category and x.mask) == 0) continue
            x.updateCache(); y.updateCache()
            if (!x.aabb.overlaps(y.aabb)) continue
            val m = Collision.collide(x, y) ?: continue
            if (m.penetration > TOLERANCE) return true
        }
        return false
    }

    fun overlapsAny(pl: Placement, others: List<Placement>, ignoreIndex: Int = -1): Boolean {
        others.forEachIndexed { i, o -> if (i != ignoreIndex && overlap(pl, o)) return true }
        return false
    }
}
