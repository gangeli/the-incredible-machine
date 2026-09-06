package tim.core.game

import tim.core.physics.AABB
import tim.core.physics.World

/** Fluent builder for levels. Coordinates are world units on the 8-unit grid. */
class LevelBuilder(val id: String, val title: String, val goalText: String) {
    val fixed = ArrayList<Placement>()
    val fixedLinks = ArrayList<Link>()
    val tray = ArrayList<TrayItem>()
    val solution = ArrayList<Placement>()
    val solutionLinks = ArrayList<Link>()
    var goal: Goal? = null
    var gravity = World.DEFAULT_GRAVITY
    var airPressure = 1.0
    var timeLimit = 30.0
    var hint = ""

    fun fixed(type: PartType, x: Double, y: Double, flipped: Boolean = false, rotation: Int = 0, needsPower: Boolean = false): Int {
        fixed.add(Placement(type, x, y, flipped, rotation, needsPower)); return fixed.size - 1
    }
    fun tray(type: PartType, count: Int = 1) { tray.add(TrayItem(type, count)) }
    /** Adds a solution part and returns its index in the full part list (fixed parts first). */
    fun solve(type: PartType, x: Double, y: Double, flipped: Boolean = false, rotation: Int = 0): Int {
        solution.add(Placement(type, x, y, flipped, rotation)); return fixed.size + solution.size - 1
    }
    fun solveRope(from: Int, to: Int, vararg via: Int) { solutionLinks.add(Link(LinkKind.ROPE, from, to, via.toList())) }
    fun solveBelt(from: Int, to: Int) { solutionLinks.add(Link(LinkKind.BELT, from, to)) }
    fun solveWire(from: Int, to: Int) { solutionLinks.add(Link(LinkKind.WIRE, from, to)) }
    fun rope(from: Int, to: Int, vararg via: Int, slack: Double = 0.0) { fixedLinks.add(Link(LinkKind.ROPE, from, to, via.toList(), slack)) }
    fun belt(from: Int, to: Int) { fixedLinks.add(Link(LinkKind.BELT, from, to)) }
    fun wire(from: Int, to: Int) { fixedLinks.add(Link(LinkKind.WIRE, from, to)) }

    /** Full-width brick floor at [y]. */
    fun floor(y: Double = 384.0) {
        for (i in 0 until 6) fixed(PartType.BRICK_WALL, i * 96.0, y)
        fixed(PartType.SMALL_WALL, 576.0, y); fixed(PartType.SMALL_WALL, 608.0, y)
    }
    /** Brick floor from x0 to x1 (multiples of 32). */
    fun floor(x0: Double, x1: Double, y: Double = 384.0) {
        var x = x0
        while (x1 - x >= 96) { fixed(PartType.BRICK_WALL, x, y); x += 96 }
        while (x1 - x >= 32) { fixed(PartType.SMALL_WALL, x, y); x += 32 }
    }
    fun wallV(x: Double, y0: Double, y1: Double) {
        var y = y0
        while (y1 - y >= 96) { fixed(PartType.BRICK_WALL, x, y, rotation = 1); y += 96 }
        while (y1 - y >= 32) { fixed(PartType.SMALL_WALL, x, y, rotation = 1); y += 32 }
    }

    fun build() = Level(id, title, goalText, goal ?: error("level $id has no goal"), fixed, fixedLinks, tray, solution, solutionLinks, gravity, airPressure, timeLimit, hint)
}

fun level(id: String, title: String, goalText: String, block: LevelBuilder.() -> Unit): Level = LevelBuilder(id, title, goalText).apply(block).build()

/** All puzzles, in play order. */
object Levels {
    val all: List<Level> by lazy { LevelData.levels }

    fun byId(id: String) = all.first { it.id == id }

    /** The machine shown running on the title screen. */
    fun demo(): Level = LevelData.demo
    fun demos(): List<Level> = LevelData.demos

    /** Free-form mode: an empty floor and every part. */
    fun freeform(): Level = level("free", "Free play", "Build anything you like!") {
        floor()
        goal = Goal.All(emptyList())
        timeLimit = 1e9
        for (t in PartType.values()) tray(t, 99)
    }
}
