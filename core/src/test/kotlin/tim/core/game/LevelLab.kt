package tim.core.game

import tim.core.physics.World

/** Prints the trajectory of every moving part of a level's solution; handy while tuning levels. */
object LevelLab {
    fun trace(level: Level, seconds: Double = 6.0, every: Double = 0.25, board: Board = level.solvedBoard()): String {
        val m = Machine(board, gravity = level.gravity, airPressure = level.airPressure)
        val sb = StringBuilder()
        var next = 0.0
        while (m.time <= seconds) {
            if (m.time >= next) {
                sb.append("t=%.2f ".format(m.time))
                for (p in m.allParts) {
                    val b = p.bodies.firstOrNull { it.isDynamic } ?: continue
                    sb.append("${p.type.name.lowercase()}#${p.index}=(%.0f,%.0f) ".format(b.pos.x, b.pos.y))
                }
                if (level.goal.check(m)) sb.append(" GOAL")
                sb.append('\n')
                next += every
            }
            m.step()
        }
        return sb.toString()
    }
}
