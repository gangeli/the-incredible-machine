package tim.desktop

import tim.core.render.Align
import tim.core.render.Painter
import tim.core.render.Path
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * [Painter] that writes an SVG document, so the game's own vector art (logo, parts, whole machines)
 * can be exported crisp for the web site. Transforms and clips become nested groups.
 */
class SvgPainter(override val width: Double, override val height: Double) : Painter {
    private val body = StringBuilder()
    private val defs = StringBuilder()
    private var ids = 0
    private val savedGroups = ArrayList<Int>()
    private var groupsSinceSave = 0
    private val alphaStack = ArrayList<Double>()
    override var alpha: Double = 1.0
    private val metrics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()

    private fun f(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    private fun op(c: Int, attr: String): String { val a = ((c ushr 24) and 0xFF) / 255.0 * alpha; return if (a >= 0.999) "" else " $attr=\"${f(a)}\"" }
    private fun fill(c: Int) = "fill=\"${hex(c)}\"${op(c, "fill-opacity")}"
    private fun stroke(c: Int, w: Double) = "fill=\"none\" stroke=\"${hex(c)}\" stroke-width=\"${f(w)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"${op(c, "stroke-opacity")}"
    private fun group(attrs: String) { body.append("<g $attrs>"); groupsSinceSave++ }
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    override fun save() { alphaStack.add(alpha); savedGroups.add(groupsSinceSave); groupsSinceSave = 0 }
    override fun restore() {
        repeat(groupsSinceSave) { body.append("</g>") }
        groupsSinceSave = if (savedGroups.isEmpty()) 0 else savedGroups.removeAt(savedGroups.size - 1)
        if (alphaStack.isNotEmpty()) alpha = alphaStack.removeAt(alphaStack.size - 1)
    }
    override fun translate(dx: Double, dy: Double) = group("transform=\"translate(${f(dx)} ${f(dy)})\"")
    override fun rotate(radians: Double) = group("transform=\"rotate(${f(radians * 180 / PI)})\"")
    override fun scale(sx: Double, sy: Double) = group("transform=\"scale(${f(sx)} ${f(sy)})\"")
    override fun clipRect(x: Double, y: Double, w: Double, h: Double) {
        val id = "c${ids++}"
        defs.append("<clipPath id=\"$id\"><rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\"/></clipPath>")
        group("clip-path=\"url(#$id)\"")
    }
    override fun clipRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double) {
        val id = "c${ids++}"
        defs.append("<clipPath id=\"$id\"><rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\" rx=\"${f(r)}\"/></clipPath>")
        group("clip-path=\"url(#$id)\"")
    }
    override fun clear(color: Int) { body.append("<rect x=\"0\" y=\"0\" width=\"${f(width)}\" height=\"${f(height)}\" ${fill(color)}/>") }
    override fun fillRect(x: Double, y: Double, w: Double, h: Double, color: Int) { body.append("<rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\" ${fill(color)}/>") }
    override fun strokeRect(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double) { body.append("<rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\" ${stroke(color, width)}/>") }
    override fun fillRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int) { body.append("<rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\" rx=\"${f(minOf(r, w / 2, h / 2))}\" ${fill(color)}/>") }
    override fun strokeRoundRect(x: Double, y: Double, w: Double, h: Double, r: Double, color: Int, width: Double) { body.append("<rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\" rx=\"${f(minOf(r, w / 2, h / 2))}\" ${stroke(color, width)}/>") }
    override fun fillCircle(cx: Double, cy: Double, r: Double, color: Int) { body.append("<circle cx=\"${f(cx)}\" cy=\"${f(cy)}\" r=\"${f(r)}\" ${fill(color)}/>") }
    override fun strokeCircle(cx: Double, cy: Double, r: Double, color: Int, width: Double) { body.append("<circle cx=\"${f(cx)}\" cy=\"${f(cy)}\" r=\"${f(r)}\" ${stroke(color, width)}/>") }
    override fun fillOval(x: Double, y: Double, w: Double, h: Double, color: Int) { body.append("<ellipse cx=\"${f(x + w / 2)}\" cy=\"${f(y + h / 2)}\" rx=\"${f(w / 2)}\" ry=\"${f(h / 2)}\" ${fill(color)}/>") }
    override fun strokeOval(x: Double, y: Double, w: Double, h: Double, color: Int, width: Double) { body.append("<ellipse cx=\"${f(x + w / 2)}\" cy=\"${f(y + h / 2)}\" rx=\"${f(w / 2)}\" ry=\"${f(h / 2)}\" ${stroke(color, width)}/>") }
    override fun fillPath(path: Path, color: Int) { body.append("<path d=\"${d(path)}\" ${fill(color)}/>") }
    override fun strokePath(path: Path, color: Int, width: Double) { body.append("<path d=\"${d(path)}\" ${stroke(color, width)}/>") }
    override fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: Int, width: Double) { body.append("<line x1=\"${f(x1)}\" y1=\"${f(y1)}\" x2=\"${f(x2)}\" y2=\"${f(y2)}\" ${stroke(color, width)}/>") }
    override fun gradientRect(x: Double, y: Double, w: Double, h: Double, c0: Int, c1: Int, vertical: Boolean) {
        val id = "g${ids++}"
        val (x2, y2) = if (vertical) "0" to "1" else "1" to "0"
        defs.append("<linearGradient id=\"$id\" x1=\"0\" y1=\"0\" x2=\"$x2\" y2=\"$y2\"><stop offset=\"0\" stop-color=\"${hex(c0)}\"${op(c0, "stop-opacity")}/><stop offset=\"1\" stop-color=\"${hex(c1)}\"${op(c1, "stop-opacity")}/></linearGradient>")
        body.append("<rect x=\"${f(x)}\" y=\"${f(y)}\" width=\"${f(w)}\" height=\"${f(h)}\" fill=\"url(#$id)\"/>")
    }
    override fun gradientCircle(cx: Double, cy: Double, r: Double, inner: Int, outer: Int, hx: Double, hy: Double) {
        val id = "g${ids++}"
        defs.append("<radialGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" cx=\"${f(cx)}\" cy=\"${f(cy)}\" r=\"${f(r)}\" fx=\"${f(cx + hx)}\" fy=\"${f(cy + hy)}\"><stop offset=\"0\" stop-color=\"${hex(inner)}\"${op(inner, "stop-opacity")}/><stop offset=\"1\" stop-color=\"${hex(outer)}\"${op(outer, "stop-opacity")}/></radialGradient>")
        body.append("<circle cx=\"${f(cx)}\" cy=\"${f(cy)}\" r=\"${f(r)}\" fill=\"url(#$id)\"/>")
    }
    override fun text(s: String, x: Double, y: Double, size: Double, color: Int, align: Align, bold: Boolean) {
        val anchor = when (align) { Align.LEFT -> "start"; Align.CENTER -> "middle"; Align.RIGHT -> "end" }
        body.append("<text x=\"${f(x)}\" y=\"${f(y)}\" font-family=\"system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif\" font-size=\"${f(size)}\" font-weight=\"${if (bold) "bold" else "normal"}\" text-anchor=\"$anchor\" ${fill(color)}>${esc(s)}</text>")
    }
    override fun textWidth(s: String, size: Double, bold: Boolean): Double {
        val font = Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 100)
        return metrics.getFontMetrics(font).stringWidth(s) * size / 100.0
    }

    private fun d(p: Path): String {
        val sb = StringBuilder()
        var open = false
        for (cmd in p.cmds) when (cmd) {
            is Path.Cmd.Move -> { sb.append("M${f(cmd.x)} ${f(cmd.y)} "); open = true }
            is Path.Cmd.Line -> sb.append("L${f(cmd.x)} ${f(cmd.y)} ")
            is Path.Cmd.Quad -> sb.append("Q${f(cmd.cx)} ${f(cmd.cy)} ${f(cmd.x)} ${f(cmd.y)} ")
            is Path.Cmd.Cubic -> sb.append("C${f(cmd.c1x)} ${f(cmd.c1y)} ${f(cmd.c2x)} ${f(cmd.c2y)} ${f(cmd.x)} ${f(cmd.y)} ")
            is Path.Cmd.Arc -> {
                val rx = cmd.w / 2; val ry = cmd.h / 2; val cx = cmd.x + rx; val cy = cmd.y + ry
                fun pt(deg: Double) = Pair(cx + rx * cos(deg * PI / 180), cy + ry * sin(deg * PI / 180))
                val (sx, sy) = pt(cmd.start)
                sb.append(if (open) "L${f(sx)} ${f(sy)} " else "M${f(sx)} ${f(sy)} "); open = true
                // SVG arcs cannot describe a full turn in one go; split long sweeps
                val pieces = if (abs(cmd.sweep) >= 360) 2 else 1
                for (i in 1..pieces) {
                    val a = cmd.start + cmd.sweep * i / pieces
                    val (ex, ey) = pt(a)
                    val large = if (abs(cmd.sweep / pieces) > 180) 1 else 0
                    val sweepFlag = if (cmd.sweep > 0) 1 else 0
                    sb.append("A${f(rx)} ${f(ry)} 0 $large $sweepFlag ${f(ex)} ${f(ey)} ")
                }
            }
            Path.Cmd.Close -> sb.append("Z ")
        }
        return sb.toString().trim()
    }

    fun svg(): String {
        repeat(groupsSinceSave) { body.append("</g>") }; groupsSinceSave = 0
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${f(width)}\" height=\"${f(height)}\" viewBox=\"0 0 ${f(width)} ${f(height)}\"><defs>$defs</defs>$body</svg>"
    }

    fun save(file: File) { file.parentFile?.mkdirs(); file.writeText(svg()) }
}
