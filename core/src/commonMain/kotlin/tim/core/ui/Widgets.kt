package tim.core.ui

import tim.core.game.Style
import tim.core.physics.AABB
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.Path
import tim.core.render.textCentered

/** Simple rounded button with an icon drawn by a lambda and an optional label. */
class Button(
    var rect: AABB,
    val color: Int,
    val icon: ((Painter, Double, Double, Double) -> Unit)? = null,
    var label: String = "",
    val onTap: () -> Unit,
) {
    var enabled = true
    var visible = true
    var pressed = false
    var attention = 0.0 // 0..1 pulse for the primary action
    fun hit(x: Double, y: Double) = visible && enabled && rect.expanded(6.0).contains(tim.core.physics.Vec2(x, y))

    fun draw(p: Painter, u: Double, clock: Double = 0.0) {
        if (!visible) return
        val r = rect
        val lift = if (pressed) 0.0 else 4.0 * u
        val pulse = if (attention > 0) 1.0 + 0.04 * kotlin.math.sin(clock * 6) * attention else 1.0
        p.save()
        p.translate(r.center.x, r.center.y)
        p.scale(pulse, pulse)
        p.translate(-r.center.x, -r.center.y)
        val fill = if (enabled) color else Style.GREY_LIGHT
        val rad = minOf(r.width, r.height) * 0.28
        // shadow / base
        p.fillRoundRect(r.minX, r.minY + lift + 2 * u, r.width, r.height, rad, Colors.darken(fill, if (enabled) 0.35 else 0.1))
        p.fillRoundRect(r.minX, r.minY + (if (pressed) lift else 0.0), r.width, r.height, rad, fill)
        p.strokeRoundRect(r.minX, r.minY + (if (pressed) lift else 0.0), r.width, r.height, rad, Style.OUTLINE, 2.5 * u)
        val cy = r.center.y + (if (pressed) lift else 0.0)
        val iconSize = minOf(r.width, r.height) * (if (label.isEmpty()) 0.55 else 0.42)
        val iconColor = if (enabled) Style.WHITE else Style.GREY
        if (icon != null) {
            p.save(); p.translate(r.center.x, cy - (if (label.isEmpty()) 0.0 else iconSize * 0.28)); p.scale(iconSize / 48.0, iconSize / 48.0)
            icon.invoke(p, 0.0, 0.0, 48.0)
            p.restore()
        }
        if (label.isNotEmpty()) {
            val ts = r.height * (if (icon != null) 0.22 else 0.4)
            p.textCentered(label, r.center.x, cy + (if (icon != null) iconSize * 0.62 else 0.0), ts, iconColor)
        }
        p.restore()
    }
}

/** Icons drawn in a 48x48 box centred at the origin, in white. Reusable across backends. */
object Icons {
    private val W = Style.WHITE
    private fun outlined(p: Painter, path: Path, fill: Int = W) { p.fillPath(path, fill); p.strokePath(path, Style.OUTLINE, 2.5) }

    fun play(p: Painter, x: Double, y: Double, s: Double) = outlined(p, Path.polygon(-14.0, -18.0, 18.0, 0.0, -14.0, 18.0))
    fun stop(p: Painter, x: Double, y: Double, s: Double) = outlined(p, Path.roundRect(-15.0, -15.0, 30.0, 30.0, 5.0))
    fun home(p: Painter, x: Double, y: Double, s: Double) {
        outlined(p, Path.polygon(-20.0, 0.0, 0.0, -18.0, 20.0, 0.0, 14.0, 0.0, 14.0, 16.0, -14.0, 16.0, -14.0, 0.0))
        p.fillRoundRect(-5.0, 3.0, 10.0, 13.0, 2.0, Style.OUTLINE)
    }
    fun back(p: Painter, x: Double, y: Double, s: Double) = outlined(p, Path.polygon(-18.0, 0.0, 0.0, -16.0, 0.0, -7.0, 18.0, -7.0, 18.0, 7.0, 0.0, 7.0, 0.0, 16.0))
    fun next(p: Painter, x: Double, y: Double, s: Double) = outlined(p, Path.polygon(18.0, 0.0, 0.0, -16.0, 0.0, -7.0, -18.0, -7.0, -18.0, 7.0, 0.0, 7.0, 0.0, 16.0))
    /**
     * A curved arrow: an arc of radius [r] around ([cx],[cy]) from [startDeg] sweeping [sweepDeg]
     * (clockwise when positive), with an arrowhead on the end pointing along the arc.
     */
    private fun curvedArrow(p: Painter, cx: Double, cy: Double, r: Double, startDeg: Double, sweepDeg: Double) {
        val arc = Path().arcTo(cx - r, cy - r, 2 * r, 2 * r, startDeg, sweepDeg)
        p.strokePath(arc, Style.OUTLINE, 9.5); p.strokePath(arc, W, 5.0)
        val end = ((startDeg + sweepDeg) * kotlin.math.PI / 180.0)
        val ex = cx + r * kotlin.math.cos(end); val ey = cy + r * kotlin.math.sin(end)
        // tangent direction of travel at the end of the arc
        val dir = if (sweepDeg >= 0) 1.0 else -1.0
        val tx = -kotlin.math.sin(end) * dir; val ty = kotlin.math.cos(end) * dir
        val nx = -ty; val ny = tx
        val len = 12.0; val half = 9.0
        val head = Path.polygon(ex + tx * len, ey + ty * len, ex - tx * 2 + nx * half, ey - ty * 2 + ny * half, ex - tx * 2 - nx * half, ey - ty * 2 - ny * half)
        p.fillPath(head, W); p.strokePath(head, Style.OUTLINE, 2.5)
    }
    fun undo(p: Painter, x: Double, y: Double, s: Double) = curvedArrow(p, 1.0, 5.0, 14.0, 10.0, -190.0)
    fun trash(p: Painter, x: Double, y: Double, s: Double) {
        outlined(p, Path.roundRect(-13.0, -10.0, 26.0, 28.0, 3.0))
        outlined(p, Path.roundRect(-17.0, -16.0, 34.0, 7.0, 2.0))
        p.fillRect(-5.0, -20.0, 10.0, 5.0, W); p.strokeRect(-5.0, -20.0, 10.0, 5.0, Style.OUTLINE, 2.0)
        for (lx in listOf(-6.0, 0.0, 6.0)) p.line(lx, -4.0, lx, 12.0, Style.OUTLINE, 2.0)
    }
    fun broom(p: Painter, x: Double, y: Double, s: Double) {
        p.save(); p.rotate(0.6)
        // handle with a metal band where the straw is bound on
        p.line(0.0, -26.0, 0.0, 6.0, Style.OUTLINE, 9.0); p.line(0.0, -26.0, 0.0, 6.0, Style.WOOD, 5.0)
        p.line(1.0, -24.0, 1.0, 4.0, Colors.withAlpha(W, 0.35), 1.5)
        // fanned straw bristles with a ragged bottom edge
        val straw = Path.polygon(-5.0, 4.0, 5.0, 4.0, 13.0, 20.0, 10.0, 24.0, 6.0, 21.0, 2.0, 25.0, -2.0, 21.0, -6.0, 25.0, -10.0, 22.0, -13.0, 20.0)
        p.fillPath(straw, Style.YELLOW); p.strokePath(straw, Style.OUTLINE, 2.5)
        for (lx in listOf(-6.0, -2.0, 2.0, 6.0)) p.line(lx * 0.5, 8.0, lx * 1.6, 20.0, Colors.withAlpha(Style.OUTLINE, 0.55), 1.5)
        p.fillRoundRect(-7.0, 2.0, 14.0, 6.0, 2.0, Style.GREY_LIGHT); p.strokeRoundRect(-7.0, 2.0, 14.0, 6.0, 2.0, Style.OUTLINE, 2.0)
        p.restore()
        // puffs of dust being swept away
        p.fillCircle(-19.0, 14.0, 3.5, W); p.strokeCircle(-19.0, 14.0, 3.5, Style.OUTLINE, 1.5)
        p.fillCircle(-24.0, 5.0, 2.5, W); p.strokeCircle(-24.0, 5.0, 2.5, Style.OUTLINE, 1.5)
        p.line(-16.0, 22.0, -23.0, 24.0, W, 2.0)
    }
    fun scissors(p: Painter, x: Double, y: Double, s: Double) {
        // two blades crossing at a pivot, points to the right, finger rings to the left
        for (side in listOf(1.0, -1.0)) {
            p.save(); p.translate(3.0, 0.0); p.rotate(side * 0.42)
            outlined(p, Path.polygon(-2.0, -3.0, 20.0, -1.5, 24.0, 0.0, 20.0, 1.5, -2.0, 3.0))
            p.strokeCircle(-14.0, 0.0, 6.5, Style.OUTLINE, 6.0)
            p.strokeCircle(-14.0, 0.0, 6.5, W, 3.0)
            p.restore()
        }
        p.fillCircle(3.0, 0.0, 3.5, Style.OUTLINE); p.fillCircle(3.0, 0.0, 1.5, W)
    }
    fun bulb(p: Painter, x: Double, y: Double, s: Double) {
        p.fillCircle(0.0, -6.0, 15.0, Style.YELLOW); p.strokeCircle(0.0, -6.0, 15.0, Style.OUTLINE, 2.5)
        outlined(p, Path.roundRect(-7.0, 8.0, 14.0, 12.0, 3.0), Style.GREY_LIGHT)
        p.line(-4.0, 12.0, 4.0, 12.0, Style.OUTLINE, 2.0); p.line(-4.0, 16.0, 4.0, 16.0, Style.OUTLINE, 2.0)
        p.line(0.0, -6.0, 0.0, 6.0, Style.ORANGE, 3.0)
    }
    fun flip(p: Painter, x: Double, y: Double, s: Double) {
        outlined(p, Path.polygon(-4.0, -14.0, -22.0, 0.0, -4.0, 14.0))
        outlined(p, Path.polygon(4.0, -14.0, 22.0, 0.0, 4.0, 14.0))
        p.line(0.0, -20.0, 0.0, 20.0, Style.OUTLINE, 3.0)
    }
    fun rotate(p: Painter, x: Double, y: Double, s: Double) = curvedArrow(p, 0.0, 1.0, 15.0, 15.0, 255.0)
    fun close(p: Painter, x: Double, y: Double, s: Double) {
        p.line(-14.0, -14.0, 14.0, 14.0, Style.OUTLINE, 10.0); p.line(-14.0, 14.0, 14.0, -14.0, Style.OUTLINE, 10.0)
        p.line(-14.0, -14.0, 14.0, 14.0, W, 5.0); p.line(-14.0, 14.0, 14.0, -14.0, W, 5.0)
    }
    fun check(p: Painter, x: Double, y: Double, s: Double) {
        val path = Path().moveTo(-18.0, 0.0).lineTo(-6.0, 12.0).lineTo(18.0, -12.0)
        p.strokePath(path, Style.OUTLINE, 11.0); p.strokePath(path, W, 6.0)
    }
    fun replay(p: Painter, x: Double, y: Double, s: Double) = curvedArrow(p, 0.0, 1.0, 15.0, 165.0, -255.0)
    fun star(p: Painter, x: Double, y: Double, s: Double, fill: Int = Style.YELLOW) {
        val path = Path()
        for (i in 0 until 10) {
            val a = -kotlin.math.PI / 2 + i * kotlin.math.PI / 5
            val rr = if (i % 2 == 0) 20.0 else 9.0
            val px = kotlin.math.cos(a) * rr; val py = kotlin.math.sin(a) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.fillPath(path, fill); p.strokePath(path, Style.OUTLINE, 2.5)
    }
    fun sound(p: Painter, x: Double, y: Double, s: Double, on: Boolean) {
        outlined(p, Path.polygon(-18.0, -7.0, -10.0, -7.0, 0.0, -16.0, 0.0, 16.0, -10.0, 7.0, -18.0, 7.0))
        if (on) {
            p.strokePath(Path().arcTo(-6.0, -10.0, 20.0, 20.0, -50.0, 100.0), W, 3.0)
            p.strokePath(Path().arcTo(-8.0, -17.0, 34.0, 34.0, -50.0, 100.0), W, 3.0)
        } else {
            p.line(8.0, -8.0, 22.0, 8.0, Style.RED, 4.0); p.line(8.0, 8.0, 22.0, -8.0, Style.RED, 4.0)
        }
    }
    /** Floppy disk: keep this machine. */
    fun save(p: Painter, x: Double, y: Double, s: Double) {
        outlined(p, Path.polygon(-18.0, -18.0, 12.0, -18.0, 18.0, -12.0, 18.0, 18.0, -18.0, 18.0))
        p.fillRoundRect(-11.0, -18.0, 20.0, 11.0, 1.5, Style.OUTLINE)
        p.fillRect(3.0, -15.0, 4.0, 6.0, W)
        p.fillRoundRect(-11.0, 3.0, 22.0, 13.0, 2.0, Style.OUTLINE)
        p.fillRoundRect(-8.0, 6.0, 16.0, 7.0, 1.5, Style.YELLOW)
    }
    /** A toy box with a star on it: the saved machines. */
    fun machines(p: Painter, x: Double, y: Double, s: Double) {
        outlined(p, Path.polygon(-20.0, -6.0, 20.0, -6.0, 16.0, 20.0, -16.0, 20.0))
        outlined(p, Path.roundRect(-22.0, -14.0, 44.0, 10.0, 3.0))
        p.save(); p.translate(0.0, 6.0); p.scale(0.42, 0.42); star(p, 0.0, 0.0, 48.0); p.restore()
    }
    fun plus(p: Painter, x: Double, y: Double, s: Double) {
        p.line(-16.0, 0.0, 16.0, 0.0, Style.OUTLINE, 12.0); p.line(0.0, -16.0, 0.0, 16.0, Style.OUTLINE, 12.0)
        p.line(-16.0, 0.0, 16.0, 0.0, W, 6.0); p.line(0.0, -16.0, 0.0, 16.0, W, 6.0)
    }
    /** Arrow dropping into a tray: "put this on your home screen". */
    fun install(p: Painter, x: Double, y: Double, s: Double) {
        outlined(p, Path.polygon(-6.0, -22.0, 6.0, -22.0, 6.0, -6.0, 15.0, -6.0, 0.0, 10.0, -15.0, -6.0, -6.0, -6.0))
        outlined(p, Path.polygon(-20.0, 8.0, -13.0, 8.0, -13.0, 15.0, 13.0, 15.0, 13.0, 8.0, 20.0, 8.0, 20.0, 22.0, -20.0, 22.0))
    }
    fun wrench(p: Painter, x: Double, y: Double, s: Double) {
        p.save(); p.rotate(-0.78)
        // handle
        p.line(0.0, -2.0, 0.0, 22.0, Style.OUTLINE, 11.0); p.line(0.0, -2.0, 0.0, 22.0, W, 6.0)
        // open-ended head: a disc with a notch cut out of the top
        val head = Path()
        val r = 11.0
        val a0 = -90.0 + 35.0; val a1 = 270.0 - 35.0
        var first = true
        var a = a0
        while (a <= a1) {
            val px = kotlin.math.cos(((a) * kotlin.math.PI / 180.0)) * r; val py = -10.0 + kotlin.math.sin(((a) * kotlin.math.PI / 180.0)) * r
            if (first) { head.moveTo(px, py); first = false } else head.lineTo(px, py)
            a += 15.0
        }
        // the jaws: a slot cut down into the head from the opening in the arc
        head.lineTo(-4.5, -9.0); head.lineTo(4.5, -9.0)
        head.close()
        p.fillPath(head, W); p.strokePath(head, Style.OUTLINE, 2.5)
        p.restore()
    }
}

/** Draws a speech-bubble style panel. */
fun Painter.panel(x: Double, y: Double, w: Double, h: Double, r: Double, fill: Int = Style.WHITE, stroke: Double = 3.0) {
    fillRoundRect(x, y + 4, w, h, r, Colors.withAlpha(Style.OUTLINE, 0.25))
    fillRoundRect(x, y, w, h, r, fill)
    strokeRoundRect(x, y, w, h, r, Style.OUTLINE, stroke)
}

/** Draws outlined text (label style) */
fun Painter.bigText(s: String, cx: Double, cy: Double, size: Double, color: Int = Style.WHITE, outline: Int = Style.OUTLINE) {
    val d = size * 0.06
    for ((ox, oy) in listOf(-d to 0.0, d to 0.0, 0.0 to -d, 0.0 to d, -d to -d, d to d, -d to d, d to -d)) textCentered(s, cx + ox, cy + oy, size, outline)
    textCentered(s, cx, cy, size, color)
}
