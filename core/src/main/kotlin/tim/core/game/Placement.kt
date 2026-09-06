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
    /**
     * Electric parts and conveyors normally run on their own so young players need no wiring.
     * A level may mark one as needing power: it then only works once a wire (or a belt, for a
     * conveyor) connects it to a live source.
     */
    val needsPower: Boolean = false,
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
) {
    fun touches(index: Int) = from == index || to == index || index in via
    /** Re-points indices after the part at [removed] was deleted from the board's part list. */
    fun shifted(removed: Int): Link {
        fun f(i: Int) = if (i > removed) i - 1 else i
        return Link(kind, f(from), f(to), via.map { f(it) }, slack)
    }
}

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

    /** Index into [all] of the i-th player part. */
    fun allIndexOf(playerIndex: Int) = fixed.size + playerIndex

    /** Removes a player part together with every link that touched it, keeping other links valid. */
    fun removePlayerPart(playerIndex: Int) {
        val idx = allIndexOf(playerIndex)
        playerParts.removeAt(playerIndex)
        val kept = playerLinks.filter { !it.touches(idx) }.map { it.shifted(idx) }
        playerLinks.clear(); playerLinks.addAll(kept)
    }

    fun linksTouching(allIndex: Int): List<Link> = links.filter { it.touches(allIndex) }
    fun playerLinksTouching(allIndex: Int): List<Link> = playerLinks.filter { it.touches(allIndex) }
}
