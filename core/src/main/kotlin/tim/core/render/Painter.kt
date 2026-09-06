package tim.core.render

enum class Align { LEFT, CENTER, RIGHT }

/** A vector path in local coordinates, converted by each backend. */
class Path {
    sealed class Cmd {
        class Move(val x: Double, val y: Double) : Cmd()
        class Line(val x: Double, val y: Double) : Cmd()
        class Quad(val cx: Double, val cy: Double, val x: Double, val y: Double) : Cmd()
        class Cubic(val c1x: Double, val c1y: Double, val c2x: Double, val c2y: Double, val x: Double, val y: Double) : Cmd()
        /** Arc of an ellipse bounding box (x,y,w,h), start angle and sweep in degrees (clockwise on screen). */
        class Arc(val x: Double, val y: Double, val w: Double, val h: Double, val start: Double, val sweep: Double) : Cmd()
        object Close : Cmd()
    }
    val cmds = ArrayList<Cmd>()
    fun moveTo(x: Double, y: Double) = apply { cmds.add(Cmd.Move(x, y)) }
    fun lineTo(x: Double, y: Double) = apply { cmds.add(Cmd.Line(x, y)) }
    fun quadTo(cx: Double, cy: Double, x: Double, y: Double) = apply { cmds.add(Cmd.Quad(cx, cy, x, y)) }
    fun cubicTo(c1x: Double, c1y: Double, c2x: Double, c2y: Double, x: Double, y: Double) = apply { cmds.add(Cmd.Cubic(c1x, c1y, c2x, c2y, x, y)) }
    fun arcTo(x: Double, y: Double, w: Double, h: Double, startDeg: Double, sweepDeg: Double) = apply { cmds.add(Cmd.Arc(x, y, w, h, startDeg, sweepDeg)) }
    fun close() = apply { cmds.add(Cmd.Close) }

    companion object {
        fun polygon(vararg xy: Double): Path {
            val p = Path()
            for (i in xy.indices step 2) if (i == 0) p.moveTo(xy[0], xy[1]) else p.lineTo(xy[i], xy[i + 1])
            return p.close()
        }
        fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double): Path {
            val rr = minOf(r, w / 2, h / 2)
            val p = Path()
            p.moveTo(x + rr, y)
            p.lineTo(x + w - rr, y); p.quadTo(x + w, y, x + w, y + rr)
            p.lineTo(x + w, y + h - rr); p.quadTo(x + w, y + h, x + w - rr, y + h)
            p.lineTo(x + rr, y + h); p.quadTo(x, y + h, x, y + h - rr)
            p.lineTo(x, y + rr); p.quadTo(x, y, x + rr, y)
            return p.close()
        }
    }
}

/**
 * Minimal immediate-mode 2D drawing surface implemented by Android Canvas (app) and Java2D (tests/desktop).
 * Colours are packed ARGB ints. Coordinates are in pixels of the target surface.
 */
interface Painter {
    val width: Double
    val height: Double

    fun save()
    fun restore()
    fun translate(dx: Double, dy: Double)
    fun rotate(radians: Double)
    fun scale(sx: Double, sy: Double)
    fun clipRect(x: Double, y: Double, w: Double, h: Double)
    fun clipRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double)

    fun clear(color: Int)
    fun fillRect(x: Double, y: Double, w: Double, h: Double, color: Int)
    fun strokeRect(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double)
    fun fillRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int)
    fun strokeRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int, width: Double)
    fun fillCircle(cx: Double, cy: Double, r: Double, color: Int)
    fun strokeCircle(cx: Double, cy: Double, r: Double, color: Int, width: Double)
    fun fillOval(x: Double, y: Double, w: Double, h: Double, color: Int)
    fun strokeOval(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double)
    fun fillPath(path: Path, color: Int)
    fun strokePath(path: Path, color: Int, width: Double)
    fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double)
    /** Linear gradient filling a rectangle; vertical goes top->bottom, otherwise left->right. */
    fun gradientRect(x: Double, y: Double, w: Double, h: Double, c0: Int, c1: Int, vertical: Boolean)
    /** Radial gradient filling a circle, with the highlight centre offset from the circle centre. */
    fun gradientCircle(cx: Double, cy: Double, r: Double, inner: Int, outer: Int, hx: Double, hy: Double)
    /** Draws text with its baseline at y. */
    fun text(s: String, x: Double, y: Double, size: Double, color: Int, align: Align = Align.LEFT, bold: Boolean = true)
    fun textWidth(s: String, size: Double, bold: Boolean = true): Double
    /** Global alpha multiplier (0..1) applied to subsequent drawing. */
    var alpha: Double
}

/** Draws text vertically centred on cy. */
fun Painter.textCentered(s: String, cx: Double, cy: Double, size: Double, color: Int, bold: Boolean = true) =
    text(s, cx, cy + size * 0.36, size, color, Align.CENTER, bold)

object Colors {
    fun rgb(rgb: Int): Int = (0xFF shl 24) or (rgb and 0xFFFFFF)
    fun argb(a: Int, rgb: Int): Int = ((a and 0xFF) shl 24) or (rgb and 0xFFFFFF)
    fun withAlpha(color: Int, a: Double): Int = argb((a.coerceIn(0.0, 1.0) * 255).toInt(), color)
    fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    /** Mix two colours; t=0 gives a, t=1 gives b. */
    fun mix(a: Int, b: Int, t: Double): Int {
        val tt = t.coerceIn(0.0, 1.0)
        fun ch(x: Int, y: Int) = (x + (y - x) * tt).toInt().coerceIn(0, 255)
        return (ch(alphaOf(a), alphaOf(b)) shl 24) or (ch(red(a), red(b)) shl 16) or (ch(green(a), green(b)) shl 8) or ch(blue(a), blue(b))
    }
    fun darken(c: Int, amount: Double) = mix(c, argb(alphaOf(c), 0x000000), amount)
    fun lighten(c: Int, amount: Double) = mix(c, argb(alphaOf(c), 0xFFFFFF), amount)

    const val TRANSPARENT = 0
    val BLACK = rgb(0x000000)
    val WHITE = rgb(0xFFFFFF)
}
