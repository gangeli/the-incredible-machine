package tim.core.ui

import tim.core.game.Style
import tim.core.render.Painter

/** Deterministic pseudo-random generator so celebrations look the same in tests. */
class Rng(seed: Long) {
    private var s = seed
    fun next(): Double { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 11).toDouble() / (1L shl 53).toDouble()) }
    fun range(a: Double, b: Double) = a + (b - a) * next()
}

class Confetti(private val w: Double, private val h: Double, seed: Long = 7) {
    private class P(var x: Double, var y: Double, var vx: Double, var vy: Double, val size: Double, val color: Int, var rot: Double, val spin: Double)
    private val parts = ArrayList<P>()
    private val rng = Rng(seed)
    private val colors = listOf(Style.RED, Style.YELLOW, Style.GREEN, Style.BLUE, Style.PINK, Style.TEAL, Style.PURPLE, Style.ORANGE)
    var time = 0.0

    fun burst(cx: Double, cy: Double, n: Int = 90) {
        repeat(n) {
            val a = rng.range(-kotlin.math.PI, 0.0)
            val sp = rng.range(h * 0.5, h * 1.4)
            parts.add(P(cx, cy, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, rng.range(h * 0.008, h * 0.018), colors[(rng.next() * colors.size).toInt()], rng.range(0.0, 6.28), rng.range(-6.0, 6.0)))
        }
    }

    fun update(dt: Double) {
        time += dt
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += h * 1.6 * dt
            p.vx *= (1 - 0.8 * dt)
            p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.spin * dt
            if (p.y > h + 40) it.remove()
        }
    }

    val active get() = parts.isNotEmpty()

    fun draw(p: Painter) {
        for (c in parts) {
            p.save(); p.translate(c.x, c.y); p.rotate(c.rot)
            p.fillRoundRect(-c.size / 2, -c.size * 0.3, c.size, c.size * 0.6, c.size * 0.15, c.color)
            p.restore()
        }
    }
}
