package tim.core.ui

import org.junit.jupiter.api.Test
import tim.core.game.Levels
import tim.core.game.Machine
import tim.core.game.PartType
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.game.parts.PartFactory
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.textCentered
import tim.desktop.Java2DPainter
import tim.desktop.Snap
import tim.desktop.SvgPainter
import java.io.File

/** Exports the artwork used by the web site (docs/) straight from the game's renderer. */
class SiteArtTest {
    private val out = File(Snap.outDir, "site")

    private fun logo(p: Painter, cx: Double, cy: Double, s: Double) {
        p.save(); p.translate(cx, cy); p.scale(s, s); p.rotate(-0.03)
        p.bigText("The", 0.0, -78.0, 40.0, Style.WHITE)
        p.bigText("Incredible", 0.0, -18.0, 84.0, Style.YELLOW)
        p.bigText("Machine", 0.0, 60.0, 84.0, Style.RED)
        p.restore()
    }

    private fun part(p: Painter, type: PartType, x: Double, y: Double, s: Double, flipped: Boolean = false) {
        p.save(); p.translate(x, y); p.scale(s, s)
        PartFactory.create(Placement(type, 0.0, 0.0, flipped), 0).draw(p, 0.0)
        p.restore()
    }

    /** Wordmark with a few parts around it, transparent background. */
    @Test
    fun `logo svg`() {
        val p = SvgPainter(900.0, 330.0)
        logo(p, 450.0, 160.0, 1.6)
        p.save(); p.translate(185.0, 72.0); p.scale(0.75, 0.75); Icons.star(p, 0.0, 0.0, 48.0); p.restore()
        p.save(); p.translate(715.0, 104.0); p.scale(0.5, 0.5); Icons.star(p, 0.0, 0.0, 48.0); p.restore()
        p.save(File(out, "logo.svg"))
    }

    /** A running machine (Rube's big machine mid-flight) as a vector scene. */
    @Test
    fun `hero scene svg`() {
        val lvl = Levels.all.first { it.id == "l43" }
        val m = Machine(lvl.solvedBoard(), gravity = lvl.gravity, airPressure = lvl.airPressure)
        while (m.time < 1.75) m.step()
        val p = SvgPainter(640.0, 400.0)
        p.save(); p.clipRoundRect(0.0, 0.0, 640.0, 400.0, 18.0)
        p.gradientRect(0.0, 0.0, 640.0, 400.0, Colors.rgb(0xF6FAFF), Colors.rgb(0xDDEBF9), true)
        m.draw(p, m.time)
        p.restore()
        p.strokeRoundRect(1.5, 1.5, 637.0, 397.0, 18.0, Style.OUTLINE, 3.0)
        p.save(File(out, "hero.svg"))
    }

    /** One small SVG per part for decoration on the site. */
    @Test
    fun `part svgs`() {
        val types = listOf(PartType.CAT, PartType.MOUSE, PartType.CHEESE, PartType.BALLOON, PartType.CANNON, PartType.ROCKET, PartType.SEESAW, PartType.BUCKET,
            PartType.FAN, PartType.CANDLE, PartType.TRAMPOLINE, PartType.SCISSORS, PartType.DYNAMITE, PartType.HOOP, PartType.BELL, PartType.STAR,
            PartType.BOWLING_BALL, PartType.BASKETBALL, PartType.CONVEYOR, PartType.PULLEY, PartType.BOXING_GLOVE, PartType.BELLOWS, PartType.INCLINE, PartType.MOTOR)
        for (t in types) {
            val w = t.w * 2 + 8; val h = t.h * 2 + 8
            val p = SvgPainter(w, h)
            p.save(); p.translate(4.0, 4.0); p.scale(2.0, 2.0)
            PartFactory.create(Placement(t, 0.0, 0.0), 0).draw(p, 0.0)
            p.restore()
            p.save(File(out, "parts/${t.name.lowercase()}.svg"))
        }
    }

    /** Link preview image (1200x630) with the logo over a machine. */
    @Test
    fun `open graph png`() {
        val p = Java2DPainter.create(1200, 630)
        p.gradientRect(0.0, 0.0, 1200.0, 630.0, Colors.rgb(0x8EC5FC), Colors.rgb(0xE0C3FC), true)
        for (i in 0 until 13) part(p, PartType.BRICK_WALL, i * 96.0, 606.0, 1.0)
        part(p, PartType.BALLOON, 70.0, 60.0, 2.2)
        part(p, PartType.BASKETBALL, 1040.0, 70.0, 2.2)
        part(p, PartType.MOUSE, 60.0, 560.0, 2.6)
        part(p, PartType.CAT, 1010.0, 520.0, 2.6, flipped = true)
        part(p, PartType.ROCKET, 960.0, 330.0, 1.6)
        part(p, PartType.SEESAW, 150.0, 560.0, 1.4)
        p.save(); p.translate(200.0, 330.0); p.scale(1.1, 1.1); Icons.star(p, 0.0, 0.0, 48.0); p.restore()
        logo(p, 600.0, 250.0, 2.1)
        p.textCentered("Build crazy contraptions. Play in your browser or on a tablet.", 600.0, 500.0, 30.0, Style.NAVY)
        Snap.save(p, "site/og")
    }
}
