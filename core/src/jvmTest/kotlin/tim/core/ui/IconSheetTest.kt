package tim.core.ui

import org.junit.jupiter.api.Test
import tim.core.game.Style
import tim.core.physics.AABB
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.textCentered
import tim.desktop.Java2DPainter
import tim.desktop.Snap

/** Renders every UI icon at high zoom, alone and on the buttons that use it, for visual review. */
class IconSheetTest {
    private val icons: List<Pair<String, (Painter, Double, Double, Double) -> Unit>> = listOf(
        "play" to Icons::play, "stop" to Icons::stop, "home" to Icons::home, "back" to Icons::back, "next" to Icons::next,
        "undo" to Icons::undo, "trash" to Icons::trash, "broom" to Icons::broom, "scissors" to Icons::scissors, "bulb" to Icons::bulb,
        "flip" to Icons::flip, "rotate" to Icons::rotate, "close" to Icons::close, "check" to Icons::check, "replay" to Icons::replay,
        "wrench" to Icons::wrench, "install" to Icons::install, "star" to { p, x, y, s -> Icons.star(p, x, y, s) },
        "sound on" to { p, x, y, s -> Icons.sound(p, x, y, s, true) }, "sound off" to { p, x, y, s -> Icons.sound(p, x, y, s, false) },
    )

    @Test
    fun `render icon sheet`() {
        val cell = 240.0
        val cols = 5
        val rows = (icons.size + cols - 1) / cols
        val p = Java2DPainter.create((cell * cols).toInt(), (cell * rows * 2).toInt())
        p.clear(Colors.rgb(0xF3F7FB))
        icons.forEachIndexed { i, (name, fn) ->
            val cx = (i % cols) * cell + cell / 2
            val cy = (i / cols) * cell + cell / 2
            // big, on a grey disc so white shapes are visible
            p.fillCircle(cx, cy - 10, 90.0, Colors.rgb(0x9AA8B8))
            p.save(); p.translate(cx, cy - 10); p.scale(3.0, 3.0); fn(p, 0.0, 0.0, 48.0); p.restore()
            p.textCentered(name, cx, cy + 105, 20.0, Style.OUTLINE)
        }
        // the same icons on real buttons, at the sizes the game uses
        val colours = listOf(Style.BLUE, Style.PURPLE, Style.ORANGE, Style.YELLOW, Style.GREEN, Style.RED, Style.TEAL)
        icons.forEachIndexed { i, (name, fn) ->
            val cx = (i % cols) * cell + cell / 2
            val cy = rows * cell + (i / cols) * cell + cell / 2
            val big = Button(AABB(cx - 100, cy - 60, cx - 4, cy + 36), colours[i % colours.size], fn, "") {}
            big.draw(p, 1.5)
            val labelled = Button(AABB(cx + 4, cy - 60, cx + 110, cy + 36), colours[(i + 3) % colours.size], fn, "Label") {}
            labelled.draw(p, 1.5)
            val small = Button(AABB(cx - 30, cy + 44, cx + 30, cy + 104), colours[(i + 5) % colours.size], fn, "") {}
            small.draw(p, 1.0)
        }
        Snap.save(p, "icons")
    }
}
