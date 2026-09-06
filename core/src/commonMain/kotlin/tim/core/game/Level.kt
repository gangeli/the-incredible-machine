package tim.core.game

/** One puzzle: the fixed board, what the player may add, the goal, and a known solution for hints/tests. */
class Level(
    val id: String,
    val title: String,
    /** One short sentence a child can understand, e.g. "Get the ball into the bucket". */
    val goalText: String,
    val goal: Goal,
    val fixed: List<Placement>,
    val fixedLinks: List<Link> = emptyList(),
    /** Parts the player can pull out of the tray, with counts. */
    val tray: List<TrayItem>,
    /** A known solution: player placements (and links) that satisfy the goal. Used for hints and tests. */
    val solution: List<Placement>,
    val solutionLinks: List<Link> = emptyList(),
    val gravity: Double = tim.core.physics.World.DEFAULT_GRAVITY,
    val airPressure: Double = 1.0,
    /** Seconds the machine may run before the game suggests trying again. */
    val timeLimit: Double = 30.0,
    val hint: String = "",
) {
    /** The part that best pictures the goal, shown on the level tile so non-readers know what to aim for. */
    val goalIcon: PartType? get() = iconFor(goal)

    /** Part indices in goals count fixed parts first, then the solution's parts. */
    private fun partType(index: Int): PartType? = fixed.getOrNull(index)?.type ?: solution.getOrNull(index - fixed.size)?.type

    private fun iconFor(g: Goal): PartType? = when (g) {
        is Goal.BallInto -> partType(g.container)
        is Goal.Activate -> partType(g.part)
        is Goal.Touch -> partType(g.target)
        is Goal.TouchType -> partType(g.target)
        is Goal.ActivateAll -> g.type
        is Goal.Reach -> partType(g.part)
        is Goal.Trapped -> PartType.CAGE
        Goal.PopAllBalloons -> PartType.BALLOON
        Goal.MouseEatsCheese -> PartType.CHEESE
        is Goal.All -> g.goals.firstOrNull()?.let { iconFor(it) }
    }

    fun newBoard() = Board(fixed, fixedLinks)
    fun solvedBoard() = Board(fixed, fixedLinks, ArrayList(solution), ArrayList(solutionLinks))
}

data class TrayItem(val type: PartType, val count: Int)
