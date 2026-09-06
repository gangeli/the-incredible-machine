package tim.core.physics

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A single contact point between two bodies. [normal] points from [a] towards [b]. */
class Manifold(
    val a: Body,
    val b: Body,
    val normal: Vec2,
    val penetration: Double,
    val point: Vec2,
) {
    var normalImpulse = 0.0
    var tangentImpulse = 0.0
    var preVn = 0.0
    var restitution = 0.0
    var friction = 0.0
}

object Collision {
    fun collide(a: Body, b: Body): Manifold? {
        val sa = a.shape
        val sb = b.shape
        return when {
            sa is CircleShape && sb is CircleShape -> circleCircle(a, sa, b, sb)
            sa is CircleShape && sb is PolygonShape -> circlePolygon(a, sa, b)
            sa is PolygonShape && sb is CircleShape -> circlePolygon(b, sb, a)?.flip()
            sa is PolygonShape && sb is PolygonShape -> polygonPolygon(a, b)
            else -> null
        }
    }

    private fun Manifold.flip() = Manifold(b, a, -normal, penetration, point)

    private fun circleCircle(a: Body, sa: CircleShape, b: Body, sb: CircleShape): Manifold? {
        val d = b.pos - a.pos
        val rs = sa.radius + sb.radius
        val distSq = d.lengthSq
        if (distSq >= rs * rs) return null
        val dist = sqrt(distSq)
        val n = if (dist < 1e-9) Vec2(0.0, 1.0) else d / dist
        val point = a.pos + n * sa.radius
        return Manifold(a, b, n, rs - dist, point)
    }

    /** Circle [a] versus polygon [b]. Returned normal points from circle to polygon (a -> b). */
    private fun circlePolygon(a: Body, sa: CircleShape, b: Body): Manifold? {
        val verts = b.worldVerts
        val c = a.pos
        val r = sa.radius
        // Find the closest point on the polygon boundary, and whether the centre is inside.
        var bestDistSq = Double.MAX_VALUE
        var bestPoint = Vec2.ZERO
        var bestEdgeNormal = Vec2.ZERO
        var bestEdgeDist = -Double.MAX_VALUE // max signed distance to any edge line (>0 means outside)
        var bestEdgeN = Vec2.ZERO
        val n = verts.size
        for (i in 0 until n) {
            val p = verts[i]
            val q = verts[(i + 1) % n]
            val e = q - p
            val len = e.length
            if (len < 1e-9) continue
            val t = ((c - p) dot e) / (len * len)
            val tc = min(1.0, max(0.0, t))
            val closest = p + e * tc
            val dSq = (c - closest).lengthSq
            // outward normal for this edge: perpendicular pointing away from centroid
            var en = Vec2(e.y, -e.x) / len
            val centroid = b.pos + (b.shape as PolygonShape).centroid.rotated(b.angle)
            if (((p - centroid) dot en) < 0) en = -en
            val signed = (c - p) dot en
            if (signed > bestEdgeDist) { bestEdgeDist = signed; bestEdgeN = en }
            if (dSq < bestDistSq) { bestDistSq = dSq; bestPoint = closest; bestEdgeNormal = en }
        }
        val inside = bestEdgeDist <= 0
        if (inside) {
            // centre inside polygon: push out along the nearest edge normal
            val pen = r - bestEdgeDist // bestEdgeDist <= 0, so pen >= r
            val normalToPoly = -bestEdgeN // from circle towards polygon interior
            return Manifold(a, b, normalToPoly, pen, c)
        }
        if (bestDistSq >= r * r) return null
        val dist = sqrt(bestDistSq)
        val normal = if (dist < 1e-9) -bestEdgeNormal else (bestPoint - c) / dist
        return Manifold(a, b, normal, r - dist, bestPoint)
    }

    private fun project(verts: List<Vec2>, axis: Vec2): Pair<Double, Double> {
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (v in verts) { val d = v dot axis; if (d < lo) lo = d; if (d > hi) hi = d }
        return lo to hi
    }

    private fun polygonPolygon(a: Body, b: Body): Manifold? {
        val va = a.worldVerts
        val vb = b.worldVerts
        var minOverlap = Double.MAX_VALUE
        var bestAxis = Vec2.ZERO
        fun testAxes(verts: List<Vec2>): Boolean {
            val n = verts.size
            for (i in 0 until n) {
                val e = verts[(i + 1) % n] - verts[i]
                val len = e.length
                if (len < 1e-9) continue
                val axis = Vec2(-e.y / len, e.x / len)
                val (a0, a1) = project(va, axis)
                val (b0, b1) = project(vb, axis)
                val overlap = min(a1, b1) - max(a0, b0)
                if (overlap <= 0) return false
                if (overlap < minOverlap) {
                    minOverlap = overlap
                    bestAxis = axis
                }
            }
            return true
        }
        if (!testAxes(va)) return null
        if (!testAxes(vb)) return null
        // orient axis from a to b
        val ca = a.pos + (a.shape as PolygonShape).centroid.rotated(a.angle)
        val cb = b.pos + (b.shape as PolygonShape).centroid.rotated(b.angle)
        var normal = bestAxis
        if (((cb - ca) dot normal) < 0) normal = -normal
        // contact point: support vertex of b in direction -normal (deepest into a), averaged with
        // support vertex of a in direction +normal, to keep the point on the overlap region.
        var deepestB = vb[0]; var dB = Double.MAX_VALUE
        for (v in vb) { val d = v dot normal; if (d < dB) { dB = d; deepestB = v } }
        var deepestA = va[0]; var dA = -Double.MAX_VALUE
        for (v in va) { val d = v dot normal; if (d > dA) { dA = d; deepestA = v } }
        val point = (deepestA + deepestB) / 2.0
        return Manifold(a, b, normal, minOverlap, point)
    }

    /** Point containment test for convex polygon bodies (used by sensors and picking). */
    fun contains(body: Body, p: Vec2): Boolean {
        return when (val s = body.shape) {
            is CircleShape -> (p - body.pos).lengthSq <= s.radius * s.radius
            is PolygonShape -> {
                val verts = body.worldVerts
                var sign = 0
                for (i in verts.indices) {
                    val a = verts[i]; val b = verts[(i + 1) % verts.size]
                    val c = (b - a) cross (p - a)
                    val s2 = if (c > 0) 1 else if (c < 0) -1 else 0
                    if (s2 != 0) { if (sign == 0) sign = s2 else if (sign != s2) return false }
                }
                true
            }
        }
    }
}
