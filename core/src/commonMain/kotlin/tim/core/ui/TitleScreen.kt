package tim.core.ui

import tim.core.game.Levels
import tim.core.game.Machine
import tim.core.game.Style
import tim.core.physics.AABB
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.textCentered

/** Title screen with a machine running in the background and two giant buttons. */
class TitleScreen(game: Game) : Screen(game) {
    private var demo: Machine? = null
    private var demoTime = 0.0
    private var acc = 0.0
    private val buttons = ArrayList<Button>()
    private lateinit var playButton: Button
    private lateinit var freeButton: Button
    private lateinit var soundButton: Button
    private var pressed: Button? = null

    override fun onEnter() {
        val u = game.u
        buttons.clear()
        val bw = 300 * u; val bh = 110 * u
        val cx = game.width / 2
        playButton = Button(AABB(cx - bw / 2, game.height * 0.56, cx + bw / 2, game.height * 0.56 + bh), Style.GREEN, Icons::play, "Play") {
            game.play("tap"); game.toLevelSelect()
        }
        playButton.attention = 1.0
        freeButton = Button(AABB(cx - bw / 2, game.height * 0.56 + bh + 24 * u, cx + bw / 2, game.height * 0.56 + 2 * bh + 24 * u), Style.ORANGE, Icons::wrench, "Free play") {
            game.play("tap"); game.startFreeform()
        }
        soundButton = Button(AABB(game.width - 90 * u, 20 * u, game.width - 20 * u, 90 * u), Style.BLUE, { p, x, y, s -> Icons.sound(p, x, y, s, game.progress.soundOn) }) {
            game.progress.soundOn = !game.progress.soundOn; game.play("tap")
        }
        buttons.add(playButton); buttons.add(freeButton); buttons.add(soundButton)
        restartDemo()
    }

    private var demoIndex = 0
    private fun restartDemo() {
        val list = Levels.demos()
        val lvl = list[demoIndex % list.size]
        demoIndex++
        demo = Machine(lvl.solvedBoard(), gravity = lvl.gravity, airPressure = lvl.airPressure)
        demoTime = 0.0
    }

    override fun update(dt: Double) {
        acc += dt
        val m = demo ?: return
        var n = 0
        while (acc >= tim.core.physics.World.STEP && n < 4) { m.step(); acc -= tim.core.physics.World.STEP; demoTime += tim.core.physics.World.STEP; n++ }
        if (demoTime > 14.0 || (demoTime > 4.0 && m.settled)) restartDemo()
    }

    override fun render(p: Painter) {
        val u = game.u
        p.gradientRect(0.0, 0.0, game.width, game.height, Colors.rgb(0x8EC5FC), Colors.rgb(0xE0C3FC), true)
        // demo machine, faded, centred
        val m = demo
        if (m != null) {
            val s = minOf(game.width / Machine.WIDTH, game.height / Machine.HEIGHT) * 0.9
            p.save()
            p.translate((game.width - Machine.WIDTH * s) / 2, game.height * 0.08)
            p.scale(s, s)
            p.alpha = 0.55
            m.draw(p, m.time)
            p.alpha = 1.0
            p.restore()
        }
        // logo
        val cx = game.width / 2
        p.save()
        p.translate(cx, game.height * 0.26)
        p.rotate(kotlin.math.sin(game.clock * 1.5) * 0.02)
        p.bigText("The", 0.0, -78 * u, 40 * u, Style.WHITE)
        p.bigText("Incredible", 0.0, -18 * u, 84 * u, Style.YELLOW)
        p.bigText("Machine", 0.0, 60 * u, 84 * u, Style.RED)
        p.restore()
        for (b in buttons) b.draw(p, u, game.clock)
        val solved = game.progress.solvedCount()
        if (solved > 0) p.textCentered("$solved of ${Levels.all.size} puzzles solved", cx, game.height - 28 * u, 20 * u, Style.NAVY)
    }

    override fun touch(ev: TouchEvent) {
        when (ev.action) {
            TouchAction.DOWN -> { pressed = buttons.lastOrNull { it.hit(ev.x, ev.y) }; pressed?.pressed = true }
            TouchAction.MOVE -> pressed?.let { it.pressed = it.hit(ev.x, ev.y) }
            TouchAction.UP -> { val b = pressed; pressed = null; if (b != null) { b.pressed = false; if (b.hit(ev.x, ev.y)) b.onTap() } }
            TouchAction.CANCEL -> { pressed?.pressed = false; pressed = null }
        }
    }
}
