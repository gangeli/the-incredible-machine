package tim.core.game

enum class PartCategory { BALL, STRUCTURE, MACHINE, CREATURE, TRIGGER, GOAL }

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
    INCLINE("Ramp", 64.0, 32.0, PartCategory.STRUCTURE, flippable = true),
    STEEP_INCLINE("Steep ramp", 48.0, 48.0, PartCategory.STRUCTURE, flippable = true),

    SEESAW("Seesaw", 96.0, 32.0, PartCategory.MACHINE),
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

    HOOP("Basketball hoop", 48.0, 48.0, PartCategory.GOAL, flippable = true),
    BELL("Bell", 40.0, 48.0, PartCategory.GOAL),
    STAR("Star", 32.0, 32.0, PartCategory.GOAL);

    val isBall get() = category == PartCategory.BALL
}
