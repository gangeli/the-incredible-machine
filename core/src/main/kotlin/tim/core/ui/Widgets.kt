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
        val pulse = if (attention > 0) 1.0 + 0.04 * StrictMath.sin(clock * 6) * attention else 1.0
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
    fun undo(p: Painter, x: Double, y: Double, s: Double) {
        val arc = Path().arcTo(-12.0, -12.0, 30.0, 30.0, 200.0, 250.0)
        p.strokePath(arc, Style.OUTLINE, 9.0); p.strokePath(arc, W, 5.0)
        outlined(p, Path.polygon(-20.0, -6.0, -6.0, -16.0, -8.0, 2.0))
    }
    fun trash(p: Painter, x: Double, y: Double, s: Double) {
        outlined(p, Path.roundRect(-13.0, -10.0, 26.0, 28.0, 3.0))
        outlined(p, Path.roundRect(-17.0, -16.0, 34.0, 7.0, 2.0))
        p.fillRect(-5.0, -20.0, 10.0, 5.0, W); p.strokeRect(-5.0, -20.0, 10.0, 5.0, Style.OUTLINE, 2.0)
        for (lx in listOf(-6.0, 0.0, 6.0)) p.line(lx, -4.0, lx, 12.0, Style.OUTLINE, 2.0)
    }
    fun broom(p: Painter, x: Double, y: Double, s: Double) {
        p.line(14.0, -22.0, -2.0, 2.0, Style.OUTLINE, 8.0); p.line(14.0, -22.0, -2.0, 2.0, Style.WOOD, 4.5)
        outlined(p, Path.polygon(-2.0, 0.0, 8.0, 7.0, -4.0, 22.0, -18.0, 12.0), Style.YELLOW)
        // bits being swept away
        p.fillCircle(-22.0, -4.0, 3.0, W); p.strokeCircle(-22.0, -4.0, 3.0, Style.OUTLINE, 1.5)
        p.fillRoundRect(-26.0, 6.0, 7.0, 5.0, 1.5, W); p.strokeRoundRect(-26.0, 6.0, 7.0, 5.0, 1.5, Style.OUTLINE, 1.5)
        p.line(-16.0, -12.0, -22.0, -14.0, W, 2.0); p.line(-14.0, 16.0, -20.0, 20.0, W, 2.0)
    }
    fun scissors(p: Painter, x: Double, y: Double, s: Double) {
        for (side in listOf(1.0, -1.0)) {
            p.save(); p.rotate(side * 0.35)
            outlined(p, Path.polygon(0.0, -3.0 * side, 22.0, -1.0 * side, 22.0, 0.0, 0.0, 3.0 * side))
            p.strokeOval(-20.0, side * 3.0 - 6.0, 14.0, 12.0, Style.OUTLINE, 5.0)
            p.strokeOval(-20.0, side * 3.0 - 6.0, 14.0, 12.0, W, 2.5)
            p.restore()
        }
        p.fillCircle(0.0, 0.0, 3.0, Style.OUTLINE)
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
    fun rotate(p: Painter, x: Double, y: Double, s: Double) {
        val arc = Path().arcTo(-15.0, -15.0, 30.0, 30.0, -90.0, 270.0)
        p.strokePath(arc, Style.OUTLINE, 9.0); p.strokePath(arc, W, 5.0)
        outlined(p, Path.polygon(-8.0, -22.0, 8.0, -15.0, -8.0, -6.0))
    }
    fun close(p: Painter, x: Double, y: Double, s: Double) {
        p.line(-14.0, -14.0, 14.0, 14.0, Style.OUTLINE, 10.0); p.line(-14.0, 14.0, 14.0, -14.0, Style.OUTLINE, 10.0)
        p.line(-14.0, -14.0, 14.0, 14.0, W, 5.0); p.line(-14.0, 14.0, 14.0, -14.0, W, 5.0)
    }
    fun check(p: Painter, x: Double, y: Double, s: Double) {
        val path = Path().moveTo(-18.0, 0.0).lineTo(-6.0, 12.0).lineTo(18.0, -12.0)
        p.strokePath(path, Style.OUTLINE, 11.0); p.strokePath(path, W, 6.0)
    }
    fun replay(p: Painter, x: Double, y: Double, s: Double) = rotate(p, x, y, s)
    fun star(p: Painter, x: Double, y: Double, s: Double, fill: Int = Style.YELLOW) {
        val path = Path()
        for (i in 0 until 10) {
            val a = -Math.PI / 2 + i * Math.PI / 5
            val rr = if (i % 2 == 0) 20.0 else 9.0
            val px = StrictMath.cos(a) * rr; val py = StrictMath.sin(a) * rr
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
    fun wrench(p: Painter, x: Double, y: Double, s: Double) {
        p.line(-12.0, 12.0, 8.0, -8.0, Style.OUTLINE, 11.0); p.line(-12.0, 12.0, 8.0, -8.0, W, 6.0)
        p.fillCircle(11.0, -11.0, 10.0, W); p.strokeCircle(11.0, -11.0, 10.0, Style.OUTLINE, 2.5)
        p.fillCircle(14.0, -14.0, 4.5, Style.OUTLINE)
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
