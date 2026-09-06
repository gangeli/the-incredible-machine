package tim.core.ui

import org.junit.jupiter.api.Test
import tim.core.game.Placement
import tim.core.game.PartType
import tim.core.game.Style
import tim.core.game.parts.PartFactory
import tim.core.render.Colors
import tim.desktop.Java2DPainter
import tim.desktop.Snap

/** Renders the app icon (used by the web app manifest) at the sizes browsers want. */
class AppIconTest {
    private fun icon(size: Int, maskable: Boolean): Java2DPainter {
        val p = Java2DPainter.create(size, size)
        val s = size.toDouble()
        p.clear(Colors.TRANSPARENT)
        // navy tile; maskable icons keep everything inside the central 80% safe zone
        val r = if (maskable) 0.0 else s * 0.2
        p.fillRoundRect(0.0, 0.0, s, s, r, Style.NAVY)
        p.gradientRect(0.0, 0.0, s, s * 0.55, Colors.withAlpha(Style.WHITE, 0.12), Colors.withAlpha(Style.WHITE, 0.0), true)
        val k = if (maskable) 0.62 else 0.78
        val inset = s * (1 - k) / 2
        p.save(); p.translate(inset, inset); p.scale(s * k / 96.0, s * k / 96.0)
        // a ramp, a basketball rolling down it and a star to reach: the whole game in one picture
        PartFactory.create(Placement(PartType.INCLINE, 4.0, 52.0), 0).draw(p, 0.0)
        PartFactory.create(Placement(PartType.BASKETBALL, 14.0, 20.0), 1).draw(p, 0.0)
        p.save(); p.translate(76.0, 30.0); p.scale(0.7, 0.7); Icons.star(p, 0.0, 0.0, 48.0); p.restore()
        p.restore()
        return p
    }

    @Test
    fun `render web app icons`() {
        Snap.save(icon(192, false), "icon-192")
        Snap.save(icon(512, false), "icon-512")
        Snap.save(icon(512, true), "icon-512-maskable")
    }
}
