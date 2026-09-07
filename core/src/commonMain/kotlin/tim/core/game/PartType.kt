package tim.core.game

enum class PartCategory { BALL, STRUCTURE, MACHINE, CREATURE, TRIGGER, GOAL, LINK }

/**
 * Every kind of part in the game. Sizes are in world units (the playfield is 640 x 400 units,
 * placement snaps to a grid of 8). [flippable] parts can be mirrored horizontally; [rotatable] parts
 * can be turned in 90 degree steps (their size swaps).
 */
enum class PartType(
    val label: String,
    val w: Double,
    val h: Double,
    val category: PartCategory,
    val flippable: Boolean = false,
    val rotatable: Boolean = false,
) {
    BOWLING_BALL("Bowling ball", 32.0, 32.0, PartCategory.BALL),
    BASKETBALL("Basketball", 32.0, 32.0, PartCategory.BALL),
    BASEBALL("Baseball", 16.0, 16.0, PartCategory.BALL),
    TENNIS_BALL("Tennis ball", 16.0, 16.0, PartCategory.BALL),
    SUPER_BALL("Super ball", 16.0, 16.0, PartCategory.BALL),
    CANNONBALL("Cannonball", 24.0, 24.0, PartCategory.BALL),
    BALLOON("Balloon", 32.0, 40.0, PartCategory.BALL),

    BRICK_WALL("Brick wall", 96.0, 16.0, PartCategory.STRUCTURE, rotatable = true),
    WOOD_WALL("Wood plank", 64.0, 16.0, PartCategory.STRUCTURE, rotatable = true),
    SMALL_WALL("Small brick", 32.0, 16.0, PartCategory.STRUCTURE, rotatable = true),
    INCLINE("Ramp", 64.0, 32.0, PartCategory.STRUCTURE, flippable = true, rotatable = true),
    STEEP_INCLINE("Steep ramp", 48.0, 48.0, PartCategory.STRUCTURE, flippable = true, rotatable = true),

    SEESAW("Seesaw", 96.0, 32.0, PartCategory.MACHINE, flippable = true),
    TRAMPOLINE("Trampoline", 64.0, 24.0, PartCategory.MACHINE),
    CONVEYOR("Conveyor belt", 96.0, 24.0, PartCategory.MACHINE, flippable = true),
    FAN("Fan", 32.0, 40.0, PartCategory.MACHINE, flippable = true),
    BUCKET("Bucket", 48.0, 44.0, PartCategory.MACHINE),
    CAGE("Cage", 48.0, 56.0, PartCategory.MACHINE),
    CANDLE("Candle", 16.0, 32.0, PartCategory.MACHINE),
    CANNON("Cannon", 64.0, 40.0, PartCategory.MACHINE, flippable = true),
    DYNAMITE("Dynamite", 32.0, 32.0, PartCategory.MACHINE),
    ROCKET("Rocket", 24.0, 64.0, PartCategory.MACHINE),
    BUMPER("Pinball bumper", 32.0, 32.0, PartCategory.MACHINE),
    BOXING_GLOVE("Boxing glove", 64.0, 40.0, PartCategory.MACHINE, flippable = true),
    SCISSORS("Scissors", 48.0, 32.0, PartCategory.MACHINE, flippable = true),
    BELLOWS("Bellows", 64.0, 32.0, PartCategory.MACHINE, flippable = true),
    PULLEY("Pulley", 16.0, 16.0, PartCategory.MACHINE),
    HOOK("Hook", 16.0, 16.0, PartCategory.MACHINE),
    MOTOR("Electric motor", 48.0, 40.0, PartCategory.MACHINE),

    MOUSE("Mort the mouse", 24.0, 16.0, PartCategory.CREATURE, flippable = true),
    CAT("Pokey the cat", 48.0, 32.0, PartCategory.CREATURE, flippable = true),
    CHEESE("Cheese", 24.0, 16.0, PartCategory.CREATURE),

    SWITCH("Light switch", 24.0, 40.0, PartCategory.TRIGGER),
    OUTLET("Outlet", 24.0, 32.0, PartCategory.TRIGGER),
    FLASHLIGHT("Flashlight", 48.0, 24.0, PartCategory.TRIGGER, flippable = true),

    HOOP("Basketball hoop", 64.0, 48.0, PartCategory.GOAL, flippable = true),
    BELL("Bell", 40.0, 48.0, PartCategory.GOAL),
    STAR("Star", 32.0, 32.0, PartCategory.GOAL),

    /** Tools rather than parts: they create links between two placed parts. */
    ROPE("Rope", 32.0, 24.0, PartCategory.LINK),
    BELT("Belt", 32.0, 24.0, PartCategory.LINK),
    WIRE("Wire", 32.0, 24.0, PartCategory.LINK);

    val isBall get() = category == PartCategory.BALL
    val isTool get() = category == PartCategory.LINK
}

/** Which parts can be tied, belted or wired together. */
object LinkRules {
    val ropeEnds = setOf(PartType.BALLOON, PartType.BUCKET, PartType.CAGE, PartType.HOOK, PartType.SEESAW)
    fun isPulley(t: PartType) = t == PartType.PULLEY
    fun ropeEnd(t: PartType) = t in ropeEnds
    fun beltSource(t: PartType) = t == PartType.MOTOR
    fun beltConsumer(t: PartType) = t == PartType.CONVEYOR
    fun wireSource(t: PartType) = t == PartType.SWITCH || t == PartType.OUTLET
    fun wireConsumer(t: PartType) = t == PartType.FAN || t == PartType.MOTOR || t == PartType.SWITCH
    /** Appliances that only run when plugged in (next to an outlet or switch, or wired to one). */
    fun appliance(t: PartType) = t == PartType.FAN || t == PartType.MOTOR
    /** How close (world units) an appliance must sit to an outlet to plug itself in, like the original. */
    const val PLUG_REACH = 12.0

    fun kindOf(tool: PartType): LinkKind? = when (tool) { PartType.ROPE -> LinkKind.ROPE; PartType.BELT -> LinkKind.BELT; PartType.WIRE -> LinkKind.WIRE; else -> null }
    fun toolFor(kind: LinkKind): PartType = when (kind) { LinkKind.ROPE -> PartType.ROPE; LinkKind.BELT -> PartType.BELT; LinkKind.WIRE -> PartType.WIRE }

    /** Can this part be the first thing tapped with the tool? */
    fun canStart(kind: LinkKind, t: PartType): Boolean = when (kind) {
        LinkKind.ROPE -> ropeEnd(t)
        LinkKind.BELT -> beltSource(t) || beltConsumer(t)
        LinkKind.WIRE -> wireSource(t) || wireConsumer(t)
    }

    /** Any part the tool may touch at all (used for highlighting candidates). */
    fun candidate(kind: LinkKind, t: PartType): Boolean = canStart(kind, t) || (kind == LinkKind.ROPE && isPulley(t))

    /**
     * Build a properly oriented link between two parts, or null if they cannot be joined.
     * Wires and belts run from the source to the consumer whichever was tapped first.
     */
    fun connect(kind: LinkKind, ai: Int, at: PartType, bi: Int, bt: PartType, via: List<Int> = emptyList()): Link? {
        if (ai == bi) return null
        return when (kind) {
            LinkKind.ROPE -> if (ropeEnd(at) && ropeEnd(bt)) Link(LinkKind.ROPE, ai, bi, via) else null
            LinkKind.BELT -> when {
                beltSource(at) && beltConsumer(bt) -> Link(LinkKind.BELT, ai, bi)
                beltSource(bt) && beltConsumer(at) -> Link(LinkKind.BELT, bi, ai)
                else -> null
            }
            LinkKind.WIRE -> when {
                wireSource(at) && wireConsumer(bt) && bt != PartType.OUTLET -> Link(LinkKind.WIRE, ai, bi)
                wireSource(bt) && wireConsumer(at) && at != PartType.OUTLET -> Link(LinkKind.WIRE, bi, ai)
                else -> null
            }
        }
    }
}
