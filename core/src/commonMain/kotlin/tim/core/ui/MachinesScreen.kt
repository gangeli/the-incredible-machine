package tim.core.ui

import tim.core.game.BoardCodec
import tim.core.game.Levels
import tim.core.game.Machine
import tim.core.game.Style
import tim.core.physics.AABB
import tim.core.physics.Vec2
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.textCentered
import kotlin.math.abs

/** "My machines": saved free-play creations as picture tiles. Tap one to keep building it. */
class MachinesScreen(game: Game) : Screen(game) {
    private class Tile(val id: Int?, val name: String, val rect: AABB, val preview: Machine?) {
        lateinit var trash: AABB
        lateinit var pencil: AABB
    }
    private val tiles = ArrayList<Tile>()
    private lateinit var backButton: Button
    private lateinit var yesButton: Button
    private lateinit var noButton: Button
    private var scroll = 0.0
    private var maxScroll = 0.0
    private var downY = 0.0
    private var downScroll = 0.0
    private var moved = false
    private var pressedTile: Tile? = null
    private var pressedButton: Button? = null
    private var confirmDelete: Tile? = null

    override fun onEnter() {
        val u = game.u
        backButton = Button(AABB(16 * u, 16 * u, 88 * u, 88 * u), Style.BLUE, Icons::back) { game.play("tap"); game.startFreeform() }
        val cw = 190 * u; val ch = 96 * u
        val dy = game.height / 2 + 30 * u
        yesButton = Button(AABB(game.width / 2 + 16 * u, dy, game.width / 2 + 16 * u + cw, dy + ch), Style.RED, Icons::trash, "Yes, bin it") { deleteConfirmed() }
        noButton = Button(AABB(game.width / 2 - 16 * u - cw, dy, game.width / 2 - 16 * u, dy + ch), Style.BLUE, Icons::close, "No, keep") { confirmDelete = null; game.play("tap") }
        rebuild()
    }

    private fun rebuild() {
        val u = game.u
        tiles.clear()
        val cols = if (game.width > game.height * 1.3) 3 else 2
        val gap = 20 * u
        val side = 40 * u
        val tw = (game.width - 2 * side - (cols - 1) * gap) / cols
        val th = tw * 0.625 + 34 * u
        val top = 110 * u
        val entries = game.machines.list()
        val free = Levels.freeform()
        val all = listOf<Triple<Int?, String, String?>>(Triple(null, "New machine", null)) + entries.map { Triple(it.id, it.name, it.encoded) }
        all.forEachIndexed { i, (id, name, encoded) ->
            val r = i / cols; val c = i % cols
            val rect = AABB(side + c * (tw + gap), top + r * (th + gap), side + c * (tw + gap) + tw, top + r * (th + gap) + th)
            val preview = encoded?.let { Machine(BoardCodec.decode(it, free.fixed, free.fixedLinks)) }
            val t = Tile(id, name, rect, preview)
            t.trash = AABB(rect.maxX - 52 * u, rect.minY + 8 * u, rect.maxX - 8 * u, rect.minY + 52 * u)
            t.pencil = AABB(rect.minX + 8 * u, rect.minY + 8 * u, rect.minX + 52 * u, rect.minY + 52 * u)
            tiles.add(t)
        }
        val rows = (all.size + cols - 1) / cols
        maxScroll = maxOf(0.0, top + rows * (th + gap) + 30 * u - game.height)
        scroll = scroll.coerceIn(0.0, maxScroll)
    }

    private fun rename(t: Tile) {
        val ask = game.textInput ?: return
        val id = t.id ?: return
        game.play("tap")
        ask("Name your machine", t.name) { text ->
            if (!text.isNullOrBlank()) { game.machines.rename(id, text); game.play("drop"); rebuild() }
        }
    }

    private fun deleteConfirmed() {
        val t = confirmDelete ?: return
        confirmDelete = null
        t.id?.let { game.machines.delete(it) }
        game.play("whoosh")
        rebuild()
    }

    override fun update(dt: Double) {}

    override fun render(p: Painter) {
        val u = game.u
        p.gradientRect(0.0, 0.0, game.width, game.height, Colors.rgb(0xFDE2C4), Colors.rgb(0xE0C3FC), true)
        for (t in tiles) {
            val r = AABB(t.rect.minX, t.rect.minY - scroll, t.rect.maxX, t.rect.maxY - scroll)
            if (r.maxY < 0 || r.minY > game.height) continue
            val pr = if (pressedTile === t) 3 * u else 0.0
            p.fillRoundRect(r.minX, r.minY + 5 * u, r.width, r.height, 20 * u, Colors.withAlpha(Style.OUTLINE, 0.3))
            p.fillRoundRect(r.minX, r.minY + pr, r.width, r.height, 20 * u, if (t.id == null) Style.YELLOW else Style.CREAM)
            p.strokeRoundRect(r.minX, r.minY + pr, r.width, r.height, 20 * u, Style.OUTLINE, 3 * u)
            val pic = AABB(r.minX + 10 * u, r.minY + pr + 10 * u, r.maxX - 10 * u, r.maxY + pr - 34 * u)
            val m = t.preview
            if (m != null) {
                p.save()
                p.clipRoundRect(pic.minX, pic.minY, pic.width, pic.height, 12 * u)
                p.gradientRect(pic.minX, pic.minY, pic.width, pic.height, Colors.rgb(0xF6FAFF), Colors.rgb(0xDDEBF9), true)
                val s = minOf(pic.width / Machine.WIDTH, pic.height / Machine.HEIGHT)
                p.translate(pic.minX + (pic.width - Machine.WIDTH * s) / 2, pic.minY + (pic.height - Machine.HEIGHT * s) / 2)
                p.scale(s, s)
                m.draw(p, 0.0)
                p.restore()
                p.strokeRoundRect(pic.minX, pic.minY, pic.width, pic.height, 12 * u, Colors.withAlpha(Style.OUTLINE, 0.5), 2 * u)
                // bin button
                val tr = AABB(t.trash.minX, t.trash.minY - scroll + pr, t.trash.maxX, t.trash.maxY - scroll + pr)
                p.fillCircle(tr.center.x, tr.center.y, tr.width / 2, Style.RED)
                p.strokeCircle(tr.center.x, tr.center.y, tr.width / 2, Style.OUTLINE, 2 * u)
                p.save(); p.translate(tr.center.x, tr.center.y); p.scale(tr.width / 48.0 * 0.55, tr.width / 48.0 * 0.55); Icons.trash(p, 0.0, 0.0, 48.0); p.restore()
                // rename button, only where the platform can ask for text
                if (game.textInput != null) {
                    val pr2 = AABB(t.pencil.minX, t.pencil.minY - scroll + pr, t.pencil.maxX, t.pencil.maxY - scroll + pr)
                    p.fillCircle(pr2.center.x, pr2.center.y, pr2.width / 2, Style.BLUE)
                    p.strokeCircle(pr2.center.x, pr2.center.y, pr2.width / 2, Style.OUTLINE, 2 * u)
                    p.save(); p.translate(pr2.center.x, pr2.center.y); p.scale(pr2.width / 48.0 * 0.55, pr2.width / 48.0 * 0.55); Icons.pencil(p, 0.0, 0.0, 48.0); p.restore()
                }
            } else {
                p.save(); p.translate(pic.center.x, pic.center.y); p.scale(pic.height / 48.0 * 0.5, pic.height / 48.0 * 0.5); Icons.plus(p, 0.0, 0.0, 48.0); p.restore()
            }
            p.textCentered(t.name, r.center.x, r.maxY + pr - 17 * u, 20 * u, Style.OUTLINE)
        }
        p.fillRect(0.0, 0.0, game.width, 100 * u, Colors.withAlpha(Style.NAVY, 0.92))
        var ts = 44 * u
        while (ts > 22 * u && p.textWidth("My machines", ts) > game.width - 2 * 104 * u) ts -= 2 * u
        p.bigText("My machines", game.width / 2, 52 * u, ts, Style.YELLOW)
        backButton.draw(p, u, game.clock)
        if (tiles.size == 1) p.textCentered("Build something in free play and press Save to keep it here.", game.width / 2, tiles[0].rect.maxY - scroll + 50 * u, 22 * u, Style.NAVY)
        confirmDelete?.let { t ->
            p.alpha = 0.5; p.fillRect(0.0, 0.0, game.width, game.height, Style.NAVY); p.alpha = 1.0
            val pw = 560 * u; val ph = 240 * u
            val px = game.width / 2 - pw / 2; val py = game.height / 2 - ph / 2 + 10 * u
            p.panel(px, py, pw, ph, 28 * u, Style.CREAM, 4 * u)
            val ask = "Throw ${t.name} away?"
            var ts2 = 34 * u
            while (ts2 > 20 * u && p.textWidth(ask, ts2) > pw - 40 * u) ts2 -= 2 * u
            p.textCentered(ask, game.width / 2, py + 62 * u, ts2, Style.OUTLINE)
            p.textCentered("It will be gone for good.", game.width / 2, py + 104 * u, 20 * u, Style.GREY_DARK)
            noButton.draw(p, u, game.clock); yesButton.draw(p, u, game.clock)
        }
    }

    override fun touch(ev: TouchEvent) {
        val u = game.u
        when (ev.action) {
            TouchAction.DOWN -> {
                moved = false; downY = ev.y; downScroll = scroll
                if (confirmDelete != null) {
                    pressedButton = listOf(yesButton, noButton).firstOrNull { it.hit(ev.x, ev.y) }
                    pressedButton?.pressed = true
                    if (pressedButton == null) { confirmDelete = null; game.play("tap") }
                    return
                }
                if (backButton.hit(ev.x, ev.y)) { pressedButton = backButton; backButton.pressed = true; return }
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
                if (t == null || moved) return
                val trash = AABB(t.trash.minX, t.trash.minY - scroll, t.trash.maxX, t.trash.maxY - scroll).expanded(6 * u)
                if (t.id != null && trash.contains(Vec2(ev.x, ev.y))) { confirmDelete = t; game.play("tap"); return }
                val pencil = AABB(t.pencil.minX, t.pencil.minY - scroll, t.pencil.maxX, t.pencil.maxY - scroll).expanded(6 * u)
                if (t.id != null && pencil.contains(Vec2(ev.x, ev.y))) { rename(t); return }
                game.play("tap")
                if (t.id == null) game.startFreeform(fresh = true) else game.startFreeform(savedId = t.id)
            }
            TouchAction.CANCEL -> { pressedTile = null; pressedButton?.pressed = false; pressedButton = null }
        }
    }

    /** Test hooks. */
    fun tileRects(): List<Pair<Int?, AABB>> = tiles.map { it.id to AABB(it.rect.minX, it.rect.minY - scroll, it.rect.maxX, it.rect.maxY - scroll) }
    fun trashRect(id: Int): AABB? = tiles.firstOrNull { it.id == id }?.let { AABB(it.trash.minX, it.trash.minY - scroll, it.trash.maxX, it.trash.maxY - scroll) }
    fun renameRect(id: Int): AABB? = tiles.firstOrNull { it.id == id }?.let { AABB(it.pencil.minX, it.pencil.minY - scroll, it.pencil.maxX, it.pencil.maxY - scroll) }
    fun tileName(id: Int): String? = tiles.firstOrNull { it.id == id }?.name
    val confirming: Boolean get() = confirmDelete != null
    fun confirmButtons(): Pair<AABB, AABB> = yesButton.rect to noButton.rect

    override fun back(): Boolean { if (confirmDelete != null) { confirmDelete = null; return true }; game.startFreeform(); return true }
}
