package tim.core.physics

import kotlin.math.sqrt

/** Immutable 2D vector. World coordinates are y-down (screen-like); gravity points to +y. */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    operator fun div(s: Double) = Vec2(x / s, y / s)
    operator fun unaryMinus() = Vec2(-x, -y)
    infix fun dot(o: Vec2) = x * o.x + y * o.y
    /** 2D cross product (z component). */
    infix fun cross(o: Vec2) = x * o.y - y * o.x
    fun perp() = Vec2(-y, x)
    val length: Double get() = sqrt(x * x + y * y)
    val lengthSq: Double get() = x * x + y * y
    fun normalized(): Vec2 {
        val l = length
        return if (l < 1e-12) ZERO else Vec2(x / l, y / l)
    }
    fun rotated(angle: Double): Vec2 {
        val c = StrictMath.cos(angle)
        val s = StrictMath.sin(angle)
        return Vec2(x * c - y * s, x * s + y * c)
    }
    fun distanceTo(o: Vec2) = (this - o).length
    override fun toString() = "(%.2f, %.2f)".format(x, y)

    companion object {
        val ZERO = Vec2(0.0, 0.0)
        fun cross(s: Double, v: Vec2) = Vec2(-s * v.y, s * v.x)
    }
}

/** Axis-aligned bounding box. */
data class AABB(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    fun overlaps(o: AABB) = minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY
    fun contains(p: Vec2) = p.x in minX..maxX && p.y in minY..maxY
    val width get() = maxX - minX
    val height get() = maxY - minY
    val center get() = Vec2((minX + maxX) / 2, (minY + maxY) / 2)
    fun expanded(m: Double) = AABB(minX - m, minY - m, maxX + m, maxY + m)
    companion object {
        fun of(x: Double, y: Double, w: Double, h: Double) = AABB(x, y, x + w, y + h)
    }
}
