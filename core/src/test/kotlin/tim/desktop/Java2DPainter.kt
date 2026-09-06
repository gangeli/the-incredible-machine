package tim.desktop

import tim.core.render.Align
import tim.core.render.Painter
import tim.core.render.Path
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Java2D implementation of [Painter], used for tests, screenshots and the desktop runner. */
class Java2DPainter(val image: BufferedImage) : Painter {
    val g: Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }
    override val width: Double get() = image.width.toDouble()
    override val height: Double get() = image.height.toDouble()
    private val stack = ArrayDeque<Pair<AffineTransform, java.awt.Shape?>>()
    private val alphaStack = ArrayDeque<Double>()
    override var alpha: Double = 1.0
        set(v) { field = v; g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, v.toFloat().coerceIn(0f, 1f)) }

    private fun c(color: Int) = Color(color, true)
    private fun stroke(w: Double) = BasicStroke(w.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    override fun save() { stack.addLast(g.transform to g.clip); alphaStack.addLast(alpha) }
    override fun restore() {
        val (t, clip) = stack.removeLast(); g.transform = t; g.clip = clip
        alpha = alphaStack.removeLast()
    }
    override fun translate(dx: Double, dy: Double) = g.translate(dx, dy)
    override fun rotate(radians: Double) = g.rotate(radians)
    override fun scale(sx: Double, sy: Double) = g.scale(sx, sy)
    override fun clipRect(x: Double, y: Double, w: Double, h: Double) = g.clip(Rectangle2D.Double(x, y, w, h))
    override fun clipRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double) = g.clip(RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2))

    override fun clear(color: Int) { val t = g.transform; g.transform = AffineTransform(); g.color = c(color); g.fillRect(0, 0, image.width, image.height); g.transform = t }
    override fun fillRect(x: Double, y: Double, w: Double, h: Double, color: Int) { g.color = c(color); g.fill(Rectangle2D.Double(x, y, w, h)) }
    override fun strokeRect(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double) { g.color = c(color); g.stroke = stroke(width); g.draw(Rectangle2D.Double(x, y, w, h)) }
    override fun fillRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int) { g.color = c(color); g.fill(RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2)) }
    override fun strokeRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int, width: Double) { g.color = c(color); g.stroke = stroke(width); g.draw(RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2)) }
    override fun fillCircle(cx: Double, cy: Double, r: Double, color: Int) { g.color = c(color); g.fill(Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2)) }
    override fun strokeCircle(cx: Double, cy: Double, r: Double, color: Int, width: Double) { g.color = c(color); g.stroke = stroke(width); g.draw(Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2)) }
    override fun fillOval(x: Double, y: Double, w: Double, h: Double, color: Int) { g.color = c(color); g.fill(Ellipse2D.Double(x, y, w, h)) }
    override fun strokeOval(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double) { g.color = c(color); g.stroke = stroke(width); g.draw(Ellipse2D.Double(x, y, w, h)) }
    override fun fillPath(path: Path, color: Int) { g.color = c(color); g.fill(toPath2D(path)) }
    override fun strokePath(path: Path, color: Int, width: Double) { g.color = c(color); g.stroke = stroke(width); g.draw(toPath2D(path)) }
    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double) { g.color = c(color); g.stroke = stroke(width); g.draw(java.awt.geom.Line2D.Double(x1, y1, x2, y2)) }
    override fun gradientRect(x: Double, y: Double, w: Double, h: Double, c0: Int, c1: Int, vertical: Boolean) {
        g.paint = if (vertical) GradientPaint(x.toFloat(), y.toFloat(), c(c0), x.toFloat(), (y + h).toFloat(), c(c1))
        else GradientPaint(x.toFloat(), y.toFloat(), c(c0), (x + w).toFloat(), y.toFloat(), c(c1))
        g.fill(Rectangle2D.Double(x, y, w, h)); g.paint = null
    }
    override fun gradientCircle(cx: Double, cy: Double, r: Double, inner: Int, outer: Int, hx: Double, hy: Double) {
        g.paint = RadialGradientPaint(java.awt.geom.Point2D.Double(cx, cy), r.toFloat(), java.awt.geom.Point2D.Double(cx + hx, cy + hy), floatArrayOf(0f, 1f), arrayOf(c(inner), c(outer)), java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE)
        g.fill(Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2)); g.paint = null
    }
    private fun font(size: Double, bold: Boolean) = Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 12).deriveFont(size.toFloat())
    override fun text(s: String, x: Double, y: Double, size: Double, color: Int, align: Align, bold: Boolean) {
        g.font = font(size, bold); g.color = c(color)
        val w = g.fontMetrics.stringWidth(s)
        val x0 = when (align) { Align.LEFT -> x; Align.CENTER -> x - w / 2.0; Align.RIGHT -> x - w }
        g.drawString(s, x0.toFloat(), y.toFloat())
    }
    override fun textWidth(s: String, size: Double, bold: Boolean): Double { g.font = font(size, bold); return g.fontMetrics.stringWidth(s).toDouble() }

    private fun toPath2D(p: Path): Path2D.Double {
        val out = Path2D.Double()
        for (cmd in p.cmds) when (cmd) {
            is Path.Cmd.Move -> out.moveTo(cmd.x, cmd.y)
            is Path.Cmd.Line -> out.lineTo(cmd.x, cmd.y)
            is Path.Cmd.Quad -> out.quadTo(cmd.cx, cmd.cy, cmd.x, cmd.y)
            is Path.Cmd.Cubic -> out.curveTo(cmd.c1x, cmd.c1y, cmd.c2x, cmd.c2y, cmd.x, cmd.y)
            is Path.Cmd.Arc -> {
                // Java2D arcs measure counter-clockwise in degrees with y-up; screen space is y-down, so negate.
                val arc = Arc2D.Double(cmd.x, cmd.y, cmd.w, cmd.h, -cmd.start, -cmd.sweep, Arc2D.OPEN)
                out.append(arc, true)
            }
            Path.Cmd.Close -> out.closePath()
        }
        return out
    }

    fun savePng(file: File) { file.parentFile?.mkdirs(); ImageIO.write(image, "png", file) }

    companion object {
        fun create(w: Int, h: Int) = Java2DPainter(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB))
    }
}
