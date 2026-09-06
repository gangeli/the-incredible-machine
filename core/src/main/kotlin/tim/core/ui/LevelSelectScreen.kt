package tim.core.ui

import tim.core.game.Levels
import tim.core.game.Style
import tim.core.physics.AABB
import tim.core.physics.Vec2
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.textCentered
import kotlin.math.abs

/** Grid of big numbered tiles; solved levels show stars, the next one to try pulses. */
class LevelSelectScreen(game: Game) : Screen(game) {
    private class Tile(val index: Int, var rect: AABB)
    private val tiles = ArrayList<Tile>()
    private var scroll = 0.0
    private var maxScroll = 0.0
    private var downY = 0.0
    private var downScroll = 0.0
    private var moved = false
    private var pressedTile: Tile? = null
    private lateinit var homeButton: Button
    private var pressedButton: Button? = null
    private var cols = 5
    private var tileSize = 0.0
    private var gap = 0.0
    private var gridTop = 0.0
    private val iconCache = HashMap<tim.core.game.PartType, tim.core.game.Part>()

    override fun onEnter() {
        val u = game.u
        homeButton = Button(AABB(16 * u, 16 * u, 88 * u, 88 * u), Style.BLUE, Icons::home) { game.play("tap"); game.toTitle() }
        cols = if (game.width > game.height * 1.4) 6 else 5
        gap = 18 * u
        tileSize = minOf((game.width - 2 * 40 * u - (cols - 1) * gap) / cols, 150 * u)
        gridTop = 110 * u
        val gridW = cols * tileSize + (cols - 1) * gap
        val left = (game.width - gridW) / 2
        tiles.clear()
        Levels.all.forEachIndexed { i, _ ->
            val r = i / cols; val c = i % cols
            tiles.add(Tile(i, AABB(left + c * (tileSize + gap), gridTop + r * (tileSize + gap), left + c * (tileSize + gap) + tileSize, gridTop + r * (tileSize + gap) + tileSize)))
        }
        val rows = (Levels.all.size + cols - 1) / cols
        maxScroll = maxOf(0.0, gridTop + rows * (tileSize + gap) + 40 * u - game.height)
        // start scrolled so the next level is visible
        val next = game.progress.nextLevelIndex()
        val nextRow = next / cols
        scroll = (nextRow * (tileSize + gap) - (game.height - gridTop) / 2 + tileSize / 2).coerceIn(0.0, maxScroll)
    }

    override fun update(dt: Double) {}

    override fun render(p: Painter) {
        val u = game.u
        p.gradientRect(0.0, 0.0, game.width, game.height, Colors.rgb(0xC2E9FB), Colors.rgb(0xA1C4FD), true)
        val next = game.progress.nextLevelIndex()
        for (t in tiles) {
            val r = AABB(t.rect.minX, t.rect.minY - scroll, t.rect.maxX, t.rect.maxY - scroll)
            if (r.maxY < 0 || r.minY > game.height) continue
            val stars = game.progress.stars(Levels.all[t.index].id)
            val isNext = t.index == next
            val fill = when { stars > 0 -> Style.GREEN; isNext -> Style.YELLOW; else -> Style.WHITE }
            val pulse = if (isNext) 1.0 + 0.04 * StrictMath.sin(game.clock * 5) else 1.0
            val pr = if (pressedTile === t) 3 * u else 0.0
            p.save(); p.translate(r.center.x, r.center.y); p.scale(pulse, pulse); p.translate(-r.center.x, -r.center.y)
            p.fillRoundRect(r.minX, r.minY + 5 * u, r.width, r.height, 22 * u, Colors.withAlpha(Style.OUTLINE, 0.3))
            p.fillRoundRect(r.minX, r.minY + pr, r.width, r.height, 22 * u, fill)
            p.strokeRoundRect(r.minX, r.minY + pr, r.width, r.height, 22 * u, Style.OUTLINE, 3 * u)
            p.textCentered("${t.index + 1}", r.center.x - tileSize * 0.14, r.center.y - (if (stars > 0) 12 * u else 0.0) + pr, tileSize * 0.42, if (stars > 0) Style.WHITE else Style.OUTLINE)
            // picture of the goal part in the corner
            Levels.all[t.index].goalIcon?.let { gt ->
                val icon = iconCache.getOrPut(gt) { tim.core.game.parts.PartFactory.create(tim.core.game.Placement(gt, 0.0, 0.0), 0) }
                val sz = tileSize * 0.36
                p.save(); p.translate(r.maxX - sz - 8 * u, r.minY + 8 * u + pr)
                // a soft shaded badge that works on white, yellow and green tiles alike
                p.fillRoundRect(0.0, 0.0, sz, sz, sz * 0.25, Colors.withAlpha(Style.OUTLINE, if (stars > 0) 0.22 else 0.10))
                icon.drawIcon(p, sz)
                p.restore()
            }
            if (stars > 0) {
                for (i in 0 until 3) {
                    p.save(); p.translate(r.center.x + (i - 1) * tileSize * 0.26, r.maxY - tileSize * 0.22 + pr); p.scale(tileSize / 200.0, tileSize / 200.0)
                    Icons.star(p, 0.0, 0.0, 48.0, if (i < stars) Style.YELLOW else Colors.withAlpha(Style.WHITE, 0.5))
                    p.restore()
                }
            }
            p.restore()
        }
        // header
        p.fillRect(0.0, 0.0, game.width, 100 * u, Colors.withAlpha(Style.NAVY, 0.92))
        p.bigText("Pick a puzzle", game.width / 2, 52 * u, 44 * u, Style.YELLOW)
        homeButton.draw(p, u, game.clock)
    }

    override fun touch(ev: TouchEvent) {
        val u = game.u
        when (ev.action) {
            TouchAction.DOWN -> {
                moved = false; downY = ev.y; downScroll = scroll
                if (homeButton.hit(ev.x, ev.y)) { pressedButton = homeButton; homeButton.pressed = true; return }
                pressedTile = tiles.firstOrNull { AABB(it.rect.minX, it.rect.minY - scroll, it.rect.maxX, it.rect.maxY - scroll).contains(Vec2(ev.x, ev.y)) && ev.y > 100 * u }
            }
            TouchAction.MOVE -> {
                if (pressedButton != null) { pressedButton!!.pressed = pressedButton!!.hit(ev.x, ev.y); return }
                if (abs(ev.y - downY) > 10 * u) { moved = true; pressedTile = null }
                if (moved) scroll = (downScroll - (ev.y - downY)).coerceIn(0.0, maxScroll)
            }
            TouchAction.UP -> {
                val b = pressedButton
                if (b != null) { b.pressed = false; pressedButton = null; if (b.hit(ev.x, ev.y)) b.onTap(); return }
                val t = pressedTile
                pressedTile = null
                if (t != null && !moved) { game.play("tap"); game.startLevel(t.index) }
            }
            TouchAction.CANCEL -> { pressedTile = null; pressedButton?.pressed = false; pressedButton = null }
        }
    }

    override fun back(): Boolean { game.toTitle(); return true }
}
