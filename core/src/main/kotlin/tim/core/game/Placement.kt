package tim.core.game

import tim.core.physics.AABB

/** Where a part sits on the board. (x, y) is the top-left corner in world units. */
data class Placement(
    val type: PartType,
    val x: Double,
    val y: Double,
    val flipped: Boolean = false,
    /** Quarter turns clockwise (0..3); only meaningful for rotatable parts. */
    val rotation: Int = 0,
) {
    val w: Double get() = if (rotation % 2 == 1) type.h else type.w
    val h: Double get() = if (rotation % 2 == 1) type.w else type.h
    val aabb: AABB get() = AABB(x, y, x + w, y + h)
    val cx get() = x + w / 2
    val cy get() = y + h / 2
    fun moved(nx: Double, ny: Double) = copy(x = nx, y = ny)
    fun overlaps(o: Placement): Boolean {
        val a = aabb; val b = o.aabb
        return a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY
    }
}

enum class LinkKind { ROPE, BELT, WIRE }

/**
 * A connection between two placed parts. Indices refer to the board's full part list
 * (fixed parts first, then player parts). Ropes may pass over pulleys listed in [via].
 */
data class Link(
    val kind: LinkKind,
    val from: Int,
    val to: Int,
    val via: List<Int> = emptyList(),
    /** Extra slack (world units) added to a rope beyond the initial distance; negative pulls taut. */
    val slack: Double = 0.0,
)

/** The full editable state of a puzzle: the level's fixed parts plus whatever the player placed. */
class Board(
    val fixed: List<Placement>,
    val fixedLinks: List<Link>,
    val playerParts: MutableList<Placement> = ArrayList(),
    val playerLinks: MutableList<Link> = ArrayList(),
) {
    val all: List<Placement> get() = fixed + playerParts
    val links: List<Link> get() = fixedLinks + playerLinks
    fun copy() = Board(fixed, fixedLinks, ArrayList(playerParts), ArrayList(playerLinks))
}
