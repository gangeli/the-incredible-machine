package tim.android

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import tim.core.render.Align
import tim.core.render.Painter
import tim.core.render.Path

/** [Painter] backed by an Android Canvas. */
class AndroidPainter(var canvas: Canvas, override val width: Double, override val height: Double) : Painter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val textPlain = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val path = android.graphics.Path()
    private val alphaStack = ArrayList<Double>()
    override var alpha: Double = 1.0

    private fun col(c: Int): Int {
        if (alpha >= 1.0) return c
        val a = ((c ushr 24) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (c and 0xFFFFFF)
    }
    private fun f(c: Int): Paint { fill.color = col(c); return fill }
    private fun s(c: Int, w: Double): Paint { stroke.color = col(c); stroke.strokeWidth = w.toFloat(); return stroke }

    override fun save() { canvas.save(); alphaStack.add(alpha) }
    override fun restore() { canvas.restore(); if (alphaStack.isNotEmpty()) alpha = alphaStack.removeAt(alphaStack.size - 1) }
    override fun translate(dx: Double, dy: Double) = canvas.translate(dx.toFloat(), dy.toFloat())
    override fun rotate(radians: Double) = canvas.rotate(Math.toDegrees(radians).toFloat())
    override fun scale(sx: Double, sy: Double) = canvas.scale(sx.toFloat(), sy.toFloat())
    override fun clipRect(x: Double, y: Double, w: Double, h: Double) { canvas.clipRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()) }
    override fun clipRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double) {
        path.reset(); rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        path.addRoundRect(rect, r.toFloat(), r.toFloat(), android.graphics.Path.Direction.CW)
        canvas.clipPath(path)
    }
    override fun clear(color: Int) = canvas.drawColor(color)
    override fun fillRect(x: Double, y: Double, w: Double, h: Double, color: Int) = canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), f(color))
    override fun strokeRect(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double) = canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), s(color, width))
    override fun fillRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int) { rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()); canvas.drawRoundRect(rect, r.toFloat(), r.toFloat(), f(color)) }
    override fun strokeRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int, width: Double) { rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()); canvas.drawRoundRect(rect, r.toFloat(), r.toFloat(), s(color, width)) }
    override fun fillCircle(cx: Double, cy: Double, r: Double, color: Int) = canvas.drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), f(color))
    override fun strokeCircle(cx: Double, cy: Double, r: Double, color: Int, width: Double) = canvas.drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), s(color, width))
    override fun fillOval(x: Double, y: Double, w: Double, h: Double, color: Int) { rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()); canvas.drawOval(rect, f(color)) }
    override fun strokeOval(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double) { rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()); canvas.drawOval(rect, s(color, width)) }
    override fun fillPath(path: Path, color: Int) = canvas.drawPath(convert(path), f(color))
    override fun strokePath(path: Path, color: Int, width: Double) = canvas.drawPath(convert(path), s(color, width))
    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double) = canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), s(color, width))
    override fun gradientRect(x: Double, y: Double, w: Double, h: Double, c0: Int, c1: Int, vertical: Boolean) {
        shaderPaint.shader = if (vertical) LinearGradient(x.toFloat(), y.toFloat(), x.toFloat(), (y + h).toFloat(), col(c0), col(c1), Shader.TileMode.CLAMP)
        else LinearGradient(x.toFloat(), y.toFloat(), (x + w).toFloat(), y.toFloat(), col(c0), col(c1), Shader.TileMode.CLAMP)
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), shaderPaint)
        shaderPaint.shader = null
    }
    override fun gradientCircle(cx: Double, cy: Double, r: Double, inner: Int, outer: Int, hx: Double, hy: Double) {
        // Android radial gradients are concentric; offset the centre towards the highlight and enlarge the radius so the circle stays covered.
        val ox = (cx + hx).toFloat(); val oy = (cy + hy).toFloat()
        val rr = (r + Math.hypot(hx, hy)).toFloat()
        shaderPaint.shader = RadialGradient(ox, oy, rr, col(inner), col(outer), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), shaderPaint)
        shaderPaint.shader = null
    }
    override fun text(s: String, x: Double, y: Double, size: Double, color: Int, align: Align, bold: Boolean) {
        val p = if (bold) text else textPlain
        p.color = col(color); p.textSize = size.toFloat()
        p.textAlign = when (align) { Align.LEFT -> Paint.Align.LEFT; Align.CENTER -> Paint.Align.CENTER; Align.RIGHT -> Paint.Align.RIGHT }
        canvas.drawText(s, x.toFloat(), y.toFloat(), p)
    }
    override fun textWidth(s: String, size: Double, bold: Boolean): Double {
        val p = if (bold) text else textPlain
        p.textSize = size.toFloat()
        return p.measureText(s).toDouble()
    }

    private fun convert(p: Path): android.graphics.Path {
        path.reset()
        for (cmd in p.cmds) when (cmd) {
            is Path.Cmd.Move -> path.moveTo(cmd.x.toFloat(), cmd.y.toFloat())
            is Path.Cmd.Line -> path.lineTo(cmd.x.toFloat(), cmd.y.toFloat())
            is Path.Cmd.Quad -> path.quadTo(cmd.cx.toFloat(), cmd.cy.toFloat(), cmd.x.toFloat(), cmd.y.toFloat())
            is Path.Cmd.Cubic -> path.cubicTo(cmd.c1x.toFloat(), cmd.c1y.toFloat(), cmd.c2x.toFloat(), cmd.c2y.toFloat(), cmd.x.toFloat(), cmd.y.toFloat())
            is Path.Cmd.Arc -> { rect.set(cmd.x.toFloat(), cmd.y.toFloat(), (cmd.x + cmd.w).toFloat(), (cmd.y + cmd.h).toFloat()); path.arcTo(rect, cmd.start.toFloat(), cmd.sweep.toFloat(), false) }
            Path.Cmd.Close -> path.close()
        }
        return path
    }
}
