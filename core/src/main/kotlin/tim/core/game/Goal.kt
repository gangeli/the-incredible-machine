package tim.core.game

import tim.core.physics.AABB

/**
 * Win conditions, evaluated against a running machine. Part references are indices into the
 * board's fixed part list (goals only ever refer to fixed parts).
 */
sealed class Goal {
    abstract fun check(m: Machine): Boolean
    /** Short kid-friendly description. */
    abstract val text: String

    /** Any ball-like moving part must sit inside the container part (bucket, hoop). */
    data class BallInto(val container: Int, val ballType: PartType? = null) : Goal() {
        override fun check(m: Machine): Boolean {
            val c = m.parts.getOrNull(container) as? Container ?: return false
            return c.contents.any { ballType == null || it.type == ballType }
        }
        override val text get() = "Get the ${ballType?.label?.lowercase() ?: "ball"} into the ${label()}"
        private fun label() = "container"
    }

    /** All balloons on the board must be popped. */
    object PopAllBalloons : Goal() {
        override fun check(m: Machine) = m.parts.filter { it.type == PartType.BALLOON }.let { bs -> bs.isNotEmpty() && bs.all { (it as Activatable).activated } }
        override val text get() = "Pop all the balloons"
    }

    /** A specific part must reach its activated state (bell rung, star touched, rocket launched, candle lit...). */
    data class Activate(val part: Int) : Goal() {
        override fun check(m: Machine) = (m.parts.getOrNull(part) as? Activatable)?.activated == true
        override val text get() = "Activate"
    }

    /** All parts of a given type must be activated. */
    data class ActivateAll(val type: PartType) : Goal() {
        override fun check(m: Machine) = m.parts.filter { it.type == type }.let { ps -> ps.isNotEmpty() && ps.all { (it as? Activatable)?.activated == true } }
        override val text get() = "Activate all"
    }

    /** A part's centre must end up inside a rectangle of the playfield. */
    data class Reach(val part: Int, val area: AABB) : Goal() {
        override fun check(m: Machine): Boolean {
            val p = m.parts.getOrNull(part) ?: return false
            return area.contains(p.center)
        }
        override val text get() = "Reach the area"
    }

    /** Mort must reach the cheese. */
    object MouseEatsCheese : Goal() {
        override fun check(m: Machine) = m.parts.any { it.type == PartType.CHEESE && (it as Activatable).activated }
        override val text get() = "Get Mort to the cheese"
    }

    /** All listed goals must be satisfied at the same time. */
    data class All(val goals: List<Goal>) : Goal() {
        override fun check(m: Machine) = goals.all { it.check(m) }
        override val text get() = goals.joinToString(" and ") { it.text }
    }
}

/** Parts that can hold moving parts (bucket, hoop). */
interface Container { val contents: List<Part> }

/** Parts with a one-way "done" state (popped, rung, lit, launched, eaten, switched on). */
interface Activatable { val activated: Boolean }
