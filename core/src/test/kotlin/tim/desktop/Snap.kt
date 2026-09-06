package tim.desktop

import tim.core.game.Machine
import tim.core.game.Part
import tim.core.game.PartType
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.game.parts.PartFactory
import tim.core.render.Align
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.textCentered
import java.io.File

/** Helpers to render the game to PNG files for tests and for eyeballing during development. */
object Snap {
    val outDir = File(System.getProperty("tim.snapDir") ?: "build/snaps")

    fun paintMachine(m: Machine, scale: Double = 2.0, t: Double = 0.0, bg: Int = Colors.rgb(0xF3F7FB)): Java2DPainter {
        val p = Java2DPainter.create((m.width * scale).toInt(), (m.height * scale).toInt())
        p.clear(bg)
        p.save(); p.scale(scale, scale)
        m.draw(p, t)
        p.restore()
        return p
    }

    fun save(p: Java2DPainter, name: String): File {
        val f = File(outDir, "$name.png")
        p.savePng(f)
        return f
    }

    /** Renders every part type as an icon grid with labels. */
    fun partsGallery(cell: Int = 120, cols: Int = 6): Java2DPainter {
        val types = PartType.values()
        val rows = (types.size + cols - 1) / cols
        val p = Java2DPainter.create(cell * cols, (cell + 24) * rows)
        p.clear(Colors.rgb(0xF3F7FB))
        types.forEachIndexed { i, t ->
            val part: Part = PartFactory.create(Placement(t, 0.0, 0.0), i)
            val gx = (i % cols) * cell.toDouble()
            val gy = (i / cols) * (cell + 24.0)
            p.save()
            p.translate(gx + 4, gy + 4)
            p.fillRoundRect(0.0, 0.0, cell - 8.0, cell - 8.0, 12.0, Style.WHITE)
            part.drawIcon(p, cell - 8.0)
            p.restore()
            p.textCentered(t.label, gx + cell / 2.0, gy + cell + 8.0, 13.0, Style.OUTLINE)
        }
        return p
    }
}
