package tim.core.physics

import kotlin.math.max
import kotlin.math.min

sealed class Shape {
    abstract fun aabb(pos: Vec2, angle: Double): AABB
}

class CircleShape(val radius: Double) : Shape() {
    override fun aabb(pos: Vec2, angle: Double) =
        AABB(pos.x - radius, pos.y - radius, pos.x + radius, pos.y + radius)
}

/**
 * Convex polygon given in body-local coordinates. Vertices must be listed in clockwise order
 * when viewed in y-down screen space (which is counter-clockwise mathematically), so that the
 * outward normal of edge (v[i] -> v[i+1]) is `edge.perp()` negated consistently; we compute
 * normals explicitly and orient them away from the centroid, so winding does not matter.
 */
class PolygonShape(val local: List<Vec2>) : Shape() {
    init { require(local.size >= 3) }
    val centroid: Vec2 = local.fold(Vec2.ZERO) { a, b -> a + b } / local.size.toDouble()

    fun worldVertices(pos: Vec2, angle: Double): List<Vec2> =
        if (angle == 0.0) local.map { it + pos } else local.map { it.rotated(angle) + pos }

    override fun aabb(pos: Vec2, angle: Double): AABB {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (v in worldVertices(pos, angle)) {
            minX = min(minX, v.x); minY = min(minY, v.y); maxX = max(maxX, v.x); maxY = max(maxY, v.y)
        }
        return AABB(minX, minY, maxX, maxY)
    }

    companion object {
        /** Axis-aligned box centred on the body position. */
        fun box(halfW: Double, halfH: Double) = PolygonShape(
            listOf(Vec2(-halfW, -halfH), Vec2(halfW, -halfH), Vec2(halfW, halfH), Vec2(-halfW, halfH))
        )
        /** Box whose local origin is at an arbitrary offset (e.g. a plank pivoting at its centre bottom). */
        fun rect(x0: Double, y0: Double, x1: Double, y1: Double) = PolygonShape(
            listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))
        )
    }
}
