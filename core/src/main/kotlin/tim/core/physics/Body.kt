package tim.core.physics

enum class BodyKind { STATIC, KINEMATIC, DYNAMIC }

/** Collision category bits: bodies collide when (a.mask and b.category) != 0 && (b.mask and a.category) != 0. */
object Category {
    const val SOLID = 1
    const val SENSOR = 2
    const val ROPE = 4
    const val ALL = -1
}

class Body(
    val shape: Shape,
    var pos: Vec2,
    val kind: BodyKind,
    mass: Double = 1.0,
    var restitution: Double = 0.3,
    var friction: Double = 0.4,
    val owner: Any? = null,
    val tag: String = "",
) {
    var vel: Vec2 = Vec2.ZERO
    /** Rotation of the shape about pos (used by kinematic planks and ramps; also visual spin of balls). */
    var angle: Double = 0.0
    var angVel: Double = 0.0
    /** Tangential speed of the surface for conveyors; positive moves contacting bodies along +x of the body frame. */
    var surfaceSpeed: Double = 0.0
    var gravityScale: Double = 1.0
    var linearDamping: Double = 0.0
    var isSensor: Boolean = false
    var enabled: Boolean = true
    var category: Int = Category.SOLID
    var mask: Int = Category.ALL
    /** Accumulated force for this step, cleared after integration. */
    var force: Vec2 = Vec2.ZERO
    /** Extra restitution applied by this body to contacts (e.g. trampoline). Uses max with partner. */
    var bounceBoost: Double = 0.0
    /** If true, other bodies never gain rest state on this body (moving platforms). */
    val mass: Double = if (kind == BodyKind.DYNAMIC) mass else 0.0
    val invMass: Double = if (kind == BodyKind.DYNAMIC && mass > 0) 1.0 / mass else 0.0
    /** Set by the solver: true when the body touched something supporting it from below this step. */
    var grounded: Boolean = false
    var groundedOn: Body? = null
    /** Number of steps the body has been near-motionless. */
    var restSteps: Int = 0
    /** Cached world-space vertices for polygons (recomputed each step). */
    internal var worldVerts: List<Vec2> = emptyList()
    internal var aabb: AABB = AABB(0.0, 0.0, 0.0, 0.0)

    val isDynamic get() = kind == BodyKind.DYNAMIC
    val radius: Double get() = (shape as? CircleShape)?.radius ?: 0.0

    fun applyForce(f: Vec2) { force = force + f }
    fun applyImpulse(j: Vec2) { if (invMass > 0) vel = vel + j * invMass }

    /** Velocity of the body surface at world point p, including rotation and conveyor motion. */
    fun velocityAt(p: Vec2): Vec2 {
        val r = p - pos
        return vel + Vec2.cross(angVel, r)
    }

    fun updateCache() {
        worldVerts = when (shape) {
            is PolygonShape -> shape.worldVertices(pos, angle)
            else -> emptyList()
        }
        aabb = shape.aabb(pos, angle)
    }

    fun canCollideWith(o: Body): Boolean =
        enabled && o.enabled && (mask and o.category) != 0 && (o.mask and category) != 0

    override fun toString() = "Body($tag@$pos v=$vel)"
}
