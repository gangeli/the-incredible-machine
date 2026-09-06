package tim.web

import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.CanvasTextAlign
import org.w3c.dom.CanvasTextBaseline
import org.w3c.dom.LEFT
import org.w3c.dom.CENTER
import org.w3c.dom.RIGHT
import org.w3c.dom.ALPHABETIC
import org.w3c.dom.ROUND
import org.w3c.dom.CanvasLineCap
import org.w3c.dom.CanvasLineJoin
import tim.core.render.Align
import tim.core.render.Painter
import tim.core.render.Path
import kotlin.math.PI
import kotlin.math.hypot

/** [Painter] backed by an HTML canvas 2D context. */
class CanvasPainter(val ctx: CanvasRenderingContext2D, override var width: Double, override var height: Double) : Painter {
    private val alphaStack = ArrayList<Double>()
    override var alpha: Double = 1.0
    private val font = "system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"

    init {
        ctx.lineCap = CanvasLineCap.ROUND
        ctx.lineJoin = CanvasLineJoin.ROUND
        ctx.textBaseline = CanvasTextBaseline.ALPHABETIC
    }

    private fun col(c: Int): String {
        val a = ((c ushr 24) and 0xFF) / 255.0 * alpha
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return "rgba($r,$g,$b,${a.coerceIn(0.0, 1.0)})"
    }

    override fun save() { ctx.save(); alphaStack.add(alpha) }
    override fun restore() { ctx.restore(); if (alphaStack.isNotEmpty()) alpha = alphaStack.removeAt(alphaStack.size - 1) }
    override fun translate(dx: Double, dy: Double) = ctx.translate(dx, dy)
    override fun rotate(radians: Double) = ctx.rotate(radians)
    override fun scale(sx: Double, sy: Double) = ctx.scale(sx, sy)
    override fun clipRect(x: Double, y: Double, w: Double, h: Double) { ctx.beginPath(); ctx.rect(x, y, w, h); ctx.clip() }
    override fun clipRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double) { trace(Path.roundRect(x, y, w, h, r)); ctx.clip() }

    override fun clear(color: Int) {
        ctx.save()
        ctx.setTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        ctx.fillStyle = col(color)
        ctx.fillRect(0.0, 0.0, width, height)
        ctx.restore()
    }
    override fun fillRect(x: Double, y: Double, w: Double, h: Double, color: Int) { ctx.fillStyle = col(color); ctx.fillRect(x, y, w, h) }
    override fun strokeRect(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double) { ctx.strokeStyle = col(color); ctx.lineWidth = width; ctx.strokeRect(x, y, w, h) }
    override fun fillRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int) { trace(Path.roundRect(x, y, w, h, r)); ctx.fillStyle = col(color); ctx.fill() }
    override fun strokeRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int, width: Double) { trace(Path.roundRect(x, y, w, h, r)); ctx.strokeStyle = col(color); ctx.lineWidth = width; ctx.stroke() }
    override fun fillCircle(cx: Double, cy: Double, r: Double, color: Int) { ctx.beginPath(); ctx.arc(cx, cy, r, 0.0, 2 * PI); ctx.fillStyle = col(color); ctx.fill() }
    override fun strokeCircle(cx: Double, cy: Double, r: Double, color: Int, width: Double) { ctx.beginPath(); ctx.arc(cx, cy, r, 0.0, 2 * PI); ctx.strokeStyle = col(color); ctx.lineWidth = width; ctx.stroke() }
    override fun fillOval(x: Double, y: Double, w: Double, h: Double, color: Int) { ctx.beginPath(); ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0.0, 2 * PI, false); ctx.fillStyle = col(color); ctx.fill() }
    override fun strokeOval(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double) { ctx.beginPath(); ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0.0, 2 * PI, false); ctx.strokeStyle = col(color); ctx.lineWidth = width; ctx.stroke() }
    override fun fillPath(path: Path, color: Int) { trace(path); ctx.fillStyle = col(color); ctx.fill() }
    override fun strokePath(path: Path, color: Int, width: Double) { trace(path); ctx.strokeStyle = col(color); ctx.lineWidth = width; ctx.stroke() }
    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double) {
        ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.strokeStyle = col(color); ctx.lineWidth = width; ctx.stroke()
    }
    override fun gradientRect(x: Double, y: Double, w: Double, h: Double, c0: Int, c1: Int, vertical: Boolean) {
        val g = if (vertical) ctx.createLinearGradient(x, y, x, y + h) else ctx.createLinearGradient(x, y, x + w, y)
        g.addColorStop(0.0, col(c0)); g.addColorStop(1.0, col(c1))
        ctx.fillStyle = g
        ctx.fillRect(x, y, w, h)
    }
    override fun gradientCircle(cx: Double, cy: Double, r: Double, inner: Int, outer: Int, hx: Double, hy: Double) {
        val ox = cx + hx; val oy = cy + hy
        val g = ctx.createRadialGradient(ox, oy, 0.0, ox, oy, r + hypot(hx, hy))
        g.addColorStop(0.0, col(inner)); g.addColorStop(1.0, col(outer))
        ctx.beginPath(); ctx.arc(cx, cy, r, 0.0, 2 * PI)
        ctx.fillStyle = g
        ctx.fill()
    }
    override fun text(s: String, x: Double, y: Double, size: Double, color: Int, align: Align, bold: Boolean) {
        ctx.font = "${if (bold) "bold " else ""}${size}px $font"
        ctx.textAlign = when (align) { Align.LEFT -> CanvasTextAlign.LEFT; Align.CENTER -> CanvasTextAlign.CENTER; Align.RIGHT -> CanvasTextAlign.RIGHT }
        ctx.fillStyle = col(color)
        ctx.fillText(s, x, y)
    }
    override fun textWidth(s: String, size: Double, bold: Boolean): Double {
        ctx.font = "${if (bold) "bold " else ""}${size}px $font"
        return ctx.measureText(s).width
    }

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, start: Double, end: Double, anticlockwise: Boolean) {
        ctx.asDynamic().ellipse(cx, cy, rx, ry, 0.0, start, end, anticlockwise)
    }

    private fun trace(p: Path) {
        ctx.beginPath()
        for (cmd in p.cmds) when (cmd) {
            is Path.Cmd.Move -> ctx.moveTo(cmd.x, cmd.y)
            is Path.Cmd.Line -> ctx.lineTo(cmd.x, cmd.y)
            is Path.Cmd.Quad -> ctx.quadraticCurveTo(cmd.cx, cmd.cy, cmd.x, cmd.y)
            is Path.Cmd.Cubic -> ctx.bezierCurveTo(cmd.c1x, cmd.c1y, cmd.c2x, cmd.c2y, cmd.x, cmd.y)
            is Path.Cmd.Arc -> {
                // degrees clockwise on screen, like Android: canvas angles also grow clockwise with y down
                val s = cmd.start * PI / 180.0
                val e = (cmd.start + cmd.sweep) * PI / 180.0
                ellipse(cmd.x + cmd.w / 2, cmd.y + cmd.h / 2, cmd.w / 2, cmd.h / 2, s, e, cmd.sweep < 0)
            }
            Path.Cmd.Close -> ctx.closePath()
        }
    }
}
