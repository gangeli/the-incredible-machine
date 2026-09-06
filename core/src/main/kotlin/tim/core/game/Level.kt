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
    fun newBoard() = Board(fixed, fixedLinks)
    fun solvedBoard() = Board(fixed, fixedLinks, ArrayList(solution), ArrayList(solutionLinks))
}

data class TrayItem(val type: PartType, val count: Int)
