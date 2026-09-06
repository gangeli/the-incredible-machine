package tim.core.ui

import tim.core.game.Board
import tim.core.game.Level
import tim.core.game.Machine
import tim.core.game.Part
import tim.core.game.PartType
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.game.parts.PartFactory
import tim.core.physics.AABB
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Align
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.textCentered
import kotlin.math.abs
import kotlin.math.roundToInt

/** Screen-space layout of the play screen, recomputed on resize. */
class PlayLayout(val w: Double, val h: Double, val u: Double) {
    val topBar = AABB(0.0, 0.0, w, 92.0 * u)
    val trayWidth = maxOf(w * 0.19, 150.0 * u)
    val tray = AABB(w - trayWidth, topBar.maxY, w, h)
    /** Playfield rectangle preserving the 640x400 world aspect ratio inside the remaining area. */
    val field: AABB
    val scale: Double
    init {
        val margin = 14.0 * u
        val availW = tray.minX - 2 * margin
        val availH = h - topBar.maxY - 2 * margin
        val s = minOf(availW / Machine.WIDTH, availH / Machine.HEIGHT)
        scale = s
        val fw = Machine.WIDTH * s; val fh = Machine.HEIGHT * s
        val fx = margin + (availW - fw) / 2
        val fy = topBar.maxY + margin + (availH - fh) / 2
        field = AABB(fx, fy, fx + fw, fy + fh)
    }
    fun toScreen(v: Vec2) = Vec2(field.minX + v.x * scale, field.minY + v.y * scale)
    fun toWorld(x: Double, y: Double) = Vec2((x - field.minX) / scale, (y - field.minY) / scale)
}

/** A tile in the parts tray. */
class TrayTile(val type: PartType, val rect: AABB, val icon: Part)

private class Drag(
    val type: PartType,
    /** Index into board.playerParts being moved, or -1 for a brand-new part from the tray. */
    val existing: Int,
    val original: Placement?,
    var flipped: Boolean,
    var rotation: Int,
    var worldPos: Vec2,
    var valid: Boolean = false,
    var overTray: Boolean = false,
)

/**
 * The puzzle screen: build mode (drag parts from the tray) and run mode (watch the machine).
 * Designed for small children: huge buttons, snapping, forgiving drops, no text to read except
 * the goal sentence.
 */
class PlayScreen(game: Game, val level: Level, val levelIndex: Int) : Screen(game) {
    val isFreeform = levelIndex < 0
    var board: Board = level.newBoard()
        private set
    private val history = ArrayList<List<Placement>>()
    var machine: Machine? = null
        private set
    val running get() = machine != null
    var won = false
        private set
    var runTime = 0.0
        private set
    private var accumulator = 0.0
    private var settledFor = 0.0
    private var failed = false
    private var failShown = 0.0
    private var hintUntil = -1.0
    private var hintsUsed = 0
    private var selected = -1
    private var drag: Drag? = null
    private var touchStart = Vec2.ZERO
    private var touchMoved = false
    private var trayScroll = 0.0
    private var trayDragStartY = -1.0
    private var trayDragStartScroll = 0.0
    private var trayScrolling = false
    private var trayPressIndex = -1
    private var confetti: Confetti? = null
    private var celebrationTime = 0.0
    private var wobble = HashMap<Int, Double>()
    private var lastSounds = ArrayList<String>()
    private var toast = ""
    private var toastUntil = 0.0

    lateinit var layout: PlayLayout
    private val buttons = ArrayList<Button>()
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var homeButton: Button
    private lateinit var undoButton: Button
    private lateinit var resetButton: Button
    private lateinit var hintButton: Button
    private lateinit var flipButton: Button
    private lateinit var rotateButton: Button
    private lateinit var deleteButton: Button
    private lateinit var nextButton: Button
    private lateinit var replayButton: Button
    private lateinit var tiles: List<TrayTile>
    private val iconCache = HashMap<PartType, Part>()

    override fun onEnter() { relayout() }

    private fun relayout() {
        layout = PlayLayout(game.width, game.height, game.u)
        val u = game.u
        buttons.clear()
        val tb = layout.topBar
        val bh = tb.height - 20 * u
        val by = tb.minY + 10 * u
        var bx = 12 * u
        fun add(wd: Double, color: Int, icon: ((Painter, Double, Double, Double) -> Unit)?, label: String = "", onTap: () -> Unit): Button {
            val b = Button(AABB(bx, by, bx + wd, by + bh), color, icon, label, onTap)
            bx += wd + 10 * u
            buttons.add(b)
            return b
        }
        homeButton = add(bh, Style.BLUE, Icons::home) { game.play("tap"); if (isFreeform) game.toTitle() else game.toLevelSelect() }
        undoButton = add(bh, Style.PURPLE, Icons::undo) { undo() }
        resetButton = add(bh, Style.ORANGE, Icons::broom) { clearAll() }
        hintButton = add(bh, Style.YELLOW, Icons::bulb) { showHint() }
        hintButton.visible = !isFreeform
        // giant play/stop button at the bottom of the tray column
        val tr = layout.tray
        val ph = 118 * u
        playButton = Button(AABB(tr.minX + 10 * u, tr.maxY - ph - 12 * u, tr.maxX - 10 * u, tr.maxY - 12 * u), Style.GREEN, { p, x, y, sz -> if (running) Icons.stop(p, x, y, sz) else Icons.play(p, x, y, sz) }, "", ::togglePlay)
        playButton.attention = 1.0
        buttons.add(playButton)
        stopButton = Button(playButton.rect, Style.RED, Icons::stop, "", ::togglePlay)
        stopButton.visible = false
        buttons.add(stopButton)
        // floating part actions (positioned when a part is selected)
        val fs = 64 * u
        flipButton = Button(AABB(0.0, 0.0, fs, fs), Style.TEAL, Icons::flip) { flipSelected() }
        rotateButton = Button(AABB(0.0, 0.0, fs, fs), Style.TEAL, Icons::rotate) { rotateSelected() }
        deleteButton = Button(AABB(0.0, 0.0, fs, fs), Style.RED, Icons::trash) { deleteSelected() }
        listOf(flipButton, rotateButton, deleteButton).forEach { it.visible = false; buttons.add(it) }
        // win overlay buttons
        val ww = 150 * u; val wh = 90 * u
        nextButton = Button(AABB(game.width / 2 + 20 * u, game.height / 2 + 60 * u, game.width / 2 + 20 * u + ww, game.height / 2 + 60 * u + wh), Style.GREEN, Icons::next, "Next") { nextLevel() }
        replayButton = Button(AABB(game.width / 2 - 20 * u - ww, game.height / 2 + 60 * u, game.width / 2 - 20 * u, game.height / 2 + 60 * u + wh), Style.BLUE, Icons::replay, "Again") { stopRun(); game.play("tap") }
        nextButton.visible = false; replayButton.visible = false
        buttons.add(nextButton); buttons.add(replayButton)
        buildTiles()
    }

    private fun buildTiles() {
        val u = game.u
        val tr = layout.tray
        val size = tr.width - 24 * u
        val list = ArrayList<TrayTile>()
        val types = if (isFreeform) PartType.values().toList() else level.tray.map { it.type }
        var ty = tr.minY + 12 * u - trayScroll
        for (t in types) {
            val icon = iconCache.getOrPut(t) { PartFactory.create(Placement(t, 0.0, 0.0), 0) }
            list.add(TrayTile(t, AABB(tr.minX + 12 * u, ty, tr.minX + 12 * u + size, ty + size * 0.8), icon))
            ty += size * 0.8 + 10 * u
        }
        tiles = list
    }

    private fun trayContentHeight(): Double {
        val u = game.u
        val size = layout.tray.width - 24 * u
        return tiles.size * (size * 0.8 + 10 * u) + 24 * u
    }

    /** Tray area available for tiles (above the play button). */
    private fun trayTilesArea(): AABB = AABB(layout.tray.minX, layout.tray.minY, layout.tray.maxX, playButton.rect.minY - 10 * game.u)

    /** Remaining count for a tray type. */
    fun remaining(t: PartType): Int {
        if (isFreeform) return 99
        val total = level.tray.filter { it.type == t }.sumOf { it.count }
        val used = board.playerParts.count { it.type == t } + (if (drag?.type == t && drag?.existing == -1) 1 else 0)
        return total - used
    }

    // ---------------------------------------------------------------- editing

    private fun pushHistory() { history.add(ArrayList(board.playerParts)); if (history.size > 50) history.removeAt(0) }

    private fun undo() {
        if (running) return
        if (history.isEmpty()) { game.play("nope"); return }
        val prev = history.removeAt(history.size - 1)
        board.playerParts.clear(); board.playerParts.addAll(prev)
        selected = -1
        game.play("undo")
    }

    private fun clearAll() {
        if (running) return
        if (board.playerParts.isEmpty()) { game.play("nope"); return }
        pushHistory()
        board.playerParts.clear()
        selected = -1
        game.play("whoosh")
    }

    private fun showHint() {
        if (running || level.solution.isEmpty()) return
        hintsUsed++
        hintUntil = game.clock + 4.0
        game.play("hint")
    }

    private fun snap(v: Double) = (v / Machine.GRID).roundToInt() * Machine.GRID

    private fun fits(pl: Placement, ignoreIndex: Int): Boolean {
        if (pl.x < 0 || pl.y < 0 || pl.x + pl.w > Machine.WIDTH || pl.y + pl.h > Machine.HEIGHT) return false
        for (f in board.fixed) if (f.overlaps(pl)) return false
        board.playerParts.forEachIndexed { i, p -> if (i != ignoreIndex && p.overlaps(pl)) return false }
        return true
    }

    /** Find the nearest valid grid spot near the requested placement (forgiving drops for kids). */
    private fun findSpot(pl: Placement, ignoreIndex: Int): Placement? {
        if (fits(pl, ignoreIndex)) return pl
        val g = Machine.GRID
        for (ring in 1..3) {
            var best: Placement? = null
            var bestD = Double.MAX_VALUE
            for (dy in -ring..ring) for (dx in -ring..ring) {
                if (maxOf(abs(dx), abs(dy)) != ring) continue
                val c = pl.moved(pl.x + dx * g, pl.y + dy * g)
                if (fits(c, ignoreIndex)) {
                    val d = (dx * dx + dy * dy).toDouble()
                    if (d < bestD) { bestD = d; best = c }
                }
            }
            if (best != null) return best
        }
        return null
    }

    private fun flipSelected() {
        val i = selected; if (i < 0 || running) return
        val p = board.playerParts[i]
        if (!p.type.flippable) return
        pushHistory()
        board.playerParts[i] = p.copy(flipped = !p.flipped)
        wobble[i] = 1.0
        game.play("flip")
    }

    private fun rotateSelected() {
        val i = selected; if (i < 0 || running) return
        val p = board.playerParts[i]
        if (!p.type.rotatable) return
        val r = p.copy(rotation = (p.rotation + 1) % 4)
        // keep the centre in place
        val moved = r.moved(snap(p.cx - r.w / 2), snap(p.cy - r.h / 2))
        val spot = findSpot(moved, i) ?: run { game.play("nope"); return }
        pushHistory()
        board.playerParts[i] = spot
        wobble[i] = 1.0
        game.play("flip")
    }

    private fun deleteSelected() {
        val i = selected; if (i < 0 || running) return
        pushHistory()
        board.playerParts.removeAt(i)
        selected = -1
        game.play("whoosh")
    }

    // ---------------------------------------------------------------- running

    private fun togglePlay() { if (running) stopRun() else startRun() }

    private fun syncPlayButtons() { playButton.visible = !running; stopButton.visible = running }

    fun startRun() {
        if (running) return
        selected = -1
        drag = null
        machine = Machine(board.copy(), gravity = level.gravity, airPressure = level.airPressure)
        won = false; failed = false; runTime = 0.0; accumulator = 0.0; settledFor = 0.0
        confetti = null
        syncPlayButtons()
        game.play("start")
    }

    fun stopRun() {
        machine = null
        won = false; failed = false
        confetti = null
        nextButton.visible = false; replayButton.visible = false
        playButton.attention = 1.0
        syncPlayButtons()
    }

    private fun nextLevel() {
        game.play("tap")
        if (isFreeform) { stopRun(); return }
        if (levelIndex + 1 < tim.core.game.Levels.all.size) game.startLevel(levelIndex + 1) else game.toLevelSelect()
    }

    private fun onWin() {
        won = true
        celebrationTime = 0.0
        val stars = if (hintsUsed == 0) 3 else 2
        if (!isFreeform) game.progress.setStars(level.id, stars)
        confetti = Confetti(game.width, game.height).also { it.burst(game.width * 0.5, game.height * 0.45, 140) }
        game.play("win")
        nextButton.visible = true; replayButton.visible = true
    }

    override fun update(dt: Double) {
        val m = machine
        for (b in buttons) b.attention = 0.0
        if (m == null) {
            playButton.attention = if (board.playerParts.isNotEmpty() || isFreeform) 1.0 else 0.0
            for (k in wobble.keys.toList()) { wobble[k] = wobble[k]!! * 0.88; if (wobble[k]!! < 0.02) wobble.remove(k) }
            return
        }
        accumulator += dt
        var steps = 0
        while (accumulator >= World.STEP && steps < 4) {
            m.step()
            for (s in m.sounds) game.play(s)
            accumulator -= World.STEP
            runTime += World.STEP
            steps++
            if (!won && !isFreeform && level.goal.check(m)) onWin()
        }
        if (won) {
            celebrationTime += dt
            confetti?.update(dt)
            if (celebrationTime > 1.2 && confetti?.active != true) { /* keep machine running quietly */ }
        } else if (!isFreeform) {
            if (m.settled && runTime > 1.5) settledFor += dt else settledFor = 0.0
            if (!failed && (settledFor > 1.2 || runTime > level.timeLimit)) {
                failed = true
                failShown = 0.0
                game.play("nope")
            }
            if (failed) {
                failShown += dt
                if (failShown > 2.2) { stopRun(); showToast("Not yet... try again!") }
            }
        }
    }

    private fun showToast(s: String) { toast = s; toastUntil = game.clock + 2.5 }

    // ---------------------------------------------------------------- input

    override fun touch(ev: TouchEvent) {
        if (ev.pointer != 0) return
        val x = ev.x; val y = ev.y
        when (ev.action) {
            TouchAction.DOWN -> onDown(x, y)
            TouchAction.MOVE -> onMove(x, y)
            TouchAction.UP -> onUp(x, y)
            TouchAction.CANCEL -> { drag = null; trayScrolling = false; buttons.forEach { it.pressed = false } }
        }
    }

    private var pressedButton: Button? = null

    private fun onDown(x: Double, y: Double) {
        touchStart = Vec2(x, y); touchMoved = false
        pressedButton = buttons.lastOrNull { it.hit(x, y) }
        pressedButton?.let { it.pressed = true; return }
        if (running) return
        // tray
        if (layout.tray.contains(Vec2(x, y))) {
            trayPressIndex = tiles.indexOfFirst { it.rect.contains(Vec2(x, y)) }
            trayDragStartY = y; trayDragStartScroll = trayScroll; trayScrolling = false
            return
        }
        // playfield: pick up a player part
        if (layout.field.expanded(20 * game.u).contains(Vec2(x, y))) {
            val wpos = layout.toWorld(x, y)
            val hitIndex = hitPlayerPart(wpos)
            if (hitIndex >= 0) {
                val p = board.playerParts[hitIndex]
                selected = hitIndex
                drag = Drag(p.type, hitIndex, p, p.flipped, p.rotation, Vec2(p.x, p.y))
                updateDrag(x, y)
                game.play("pick")
                return
            }
            // tap on empty: deselect (a fixed part wiggles to say it's fixed)
            selected = -1
            val fixedHit = board.fixed.indexOfFirst { it.aabb.expanded(4.0).contains(wpos) }
            if (fixedHit >= 0) { wobble[-1 - fixedHit] = 1.0; game.play("nope") }
        }
    }

    private fun hitPlayerPart(w: Vec2): Int {
        var best = -1; var bestD = Double.MAX_VALUE
        board.playerParts.forEachIndexed { i, p ->
            val slop = 10.0
            if (p.aabb.expanded(slop).contains(w)) {
                val d = (p.aabb.center - w).lengthSq
                if (d < bestD) { bestD = d; best = i }
            }
        }
        return best
    }

    private fun onMove(x: Double, y: Double) {
        if ((Vec2(x, y) - touchStart).length > 8 * game.u) touchMoved = true
        pressedButton?.let { b -> b.pressed = b.hit(x, y); return }
        if (running) return
        val d = drag
        if (d != null) { updateDrag(x, y); return }
        if (trayDragStartY >= 0) {
            val dy = y - trayDragStartY
            val idx = trayPressIndex
            // horizontal pull out of the tray starts a drag of a new part; vertical drag scrolls
            if (!trayScrolling && idx >= 0 && remaining(tiles[idx].type) > 0 && (x < layout.tray.minX - 4 * game.u || abs(x - touchStart.x) > 30 * game.u)) {
                val t = tiles[idx].type
                trayDragStartY = -1.0
                drag = Drag(t, -1, null, false, 0, Vec2.ZERO)
                selected = -1
                updateDrag(x, y)
                game.play("pick")
                return
            }
            if (abs(dy) > 12 * game.u || trayScrolling) {
                trayScrolling = true
                val maxScroll = maxOf(0.0, trayContentHeight() - trayTilesArea().height)
                trayScroll = (trayDragStartScroll - dy).coerceIn(0.0, maxScroll)
                buildTiles()
            }
        }
    }

    private fun updateDrag(x: Double, y: Double) {
        val d = drag ?: return
        val u = game.u
        // lift the part above the finger so small hands can see it
        val lift = 48.0 * u
        val w = layout.toWorld(x, y - lift)
        val pw = if (d.rotation % 2 == 1) d.type.h else d.type.w
        val ph = if (d.rotation % 2 == 1) d.type.w else d.type.h
        val px = snap(w.x - pw / 2); val py = snap(w.y - ph / 2)
        d.worldPos = Vec2(px, py)
        d.overTray = layout.tray.contains(Vec2(x, y))
        d.valid = !d.overTray && fits(Placement(d.type, px, py, d.flipped, d.rotation), d.existing)
    }

    private fun onUp(x: Double, y: Double) {
        pressedButton?.let { b ->
            b.pressed = false
            pressedButton = null
            if (b.hit(x, y)) b.onTap()
            return
        }
        trayDragStartY = -1.0
        val wasScrolling = trayScrolling
        trayScrolling = false
        if (running) return
        val d = drag
        if (d != null) {
            drag = null
            val pl = Placement(d.type, d.worldPos.x, d.worldPos.y, d.flipped, d.rotation)
            if (d.overTray) {
                if (d.existing >= 0) { pushHistory(); board.playerParts.removeAt(d.existing); selected = -1; game.play("whoosh") }
                return
            }
            if (!touchMoved && d.existing >= 0) {
                // a tap on an existing part: keep it selected, no move
                selected = d.existing
                game.play("tap")
                return
            }
            val spot = findSpot(pl, d.existing)
            if (spot == null) {
                // could not place: return to where it was
                if (d.existing >= 0) selected = d.existing
                game.play("nope")
                showToast("No room there!")
                return
            }
            pushHistory()
            if (d.existing >= 0) { board.playerParts[d.existing] = spot; selected = d.existing }
            else { board.playerParts.add(spot); selected = board.playerParts.size - 1 }
            wobble[selected] = 1.0
            game.play("drop")
            return
        }
        // tap on a tray tile without dragging: place the part in a free spot near the tray (helps kids who tap)
        if (!wasScrolling && !touchMoved && trayPressIndex >= 0 && layout.tray.contains(Vec2(x, y))) {
            val t = tiles[trayPressIndex].type
            trayPressIndex = -1
            if (remaining(t) <= 0) { game.play("nope"); return }
            val start = Placement(t, snap(Machine.WIDTH - t.w - 40), snap(60.0))
            val spot = findSpot(start, -1) ?: findAnySpot(t) ?: run { game.play("nope"); return }
            pushHistory()
            board.playerParts.add(spot)
            selected = board.playerParts.size - 1
            wobble[selected] = 1.0
            game.play("drop")
        }
        trayPressIndex = -1
    }

    private fun findAnySpot(t: PartType): Placement? {
        var y = 24.0
        while (y + t.h < Machine.HEIGHT) {
            var x = Machine.WIDTH - t.w - 24
            while (x >= 0) { val pl = Placement(t, x, y); if (fits(pl, -1)) return pl; x -= 32 }
            y += 32
        }
        return null
    }

    // ---- test hooks (also handy for accessibility tooling)
    fun visibleTiles(): List<TrayTile> = tiles.filter { it.rect.minY >= trayTilesArea().minY - 1 && it.rect.maxY <= trayTilesArea().maxY + 1 }
    fun playButtonRect(): AABB = playButton.rect
    /** Screen rectangle of a floating part-action button ("flip", "rotate", "delete"), if visible. */
    fun actionButton(name: String): AABB? {
        drawSelectionButtons(null)
        val b = when (name) { "flip" -> flipButton; "rotate" -> rotateButton; else -> deleteButton }
        return if (b.visible) b.rect else null
    }

    override fun back(): Boolean {
        if (running) { stopRun(); return true }
        if (isFreeform) game.toTitle() else game.toLevelSelect()
        return true
    }

    // ---------------------------------------------------------------- rendering

    override fun render(p: Painter) {
        val u = game.u
        val t = game.clock
        p.clear(Colors.rgb(0xDCE9F5))
        drawField(p, t)
        drawTray(p, t)
        drawTopBar(p, t)
        drawSelectionButtons(p)
        syncPlayButtons()
        for (b in buttons) if (b !== nextButton && b !== replayButton) b.draw(p, u, t)
        drawDragGhost(p, t)
        if (won) drawWin(p)
        else if (failed) drawFail(p)
        if (toastUntil > t) {
            val tw = p.textWidth(toast, 26 * u) + 50 * u
            p.panel(game.width / 2 - tw / 2, layout.field.minY + 12 * u, tw, 54 * u, 16 * u, Style.CREAM)
            p.textCentered(toast, game.width / 2, layout.field.minY + 39 * u, 26 * u, Style.OUTLINE)
        }
    }

    private fun drawField(p: Painter, t: Double) {
        val f = layout.field
        val u = game.u
        p.fillRoundRect(f.minX, f.minY + 6 * u, f.width, f.height, 18 * u, Colors.withAlpha(Style.OUTLINE, 0.25))
        p.save()
        p.clipRoundRect(f.minX, f.minY, f.width, f.height, 18 * u)
        p.gradientRect(f.minX, f.minY, f.width, f.height, Colors.rgb(0xF6FAFF), Colors.rgb(0xDDEBF9), true)
        // faint dots on the placement grid
        p.save()
        p.translate(f.minX, f.minY); p.scale(layout.scale, layout.scale)
        val dot = Colors.withAlpha(Style.OUTLINE, 0.08)
        var gy = Machine.GRID * 4
        while (gy < Machine.HEIGHT) { var gx = Machine.GRID * 4; while (gx < Machine.WIDTH) { p.fillCircle(gx, gy, 1.2, dot); gx += Machine.GRID * 4 }; gy += Machine.GRID * 4 }
        val m = machine
        if (m != null) {
            m.draw(p, m.time)
        } else {
            // build mode: draw fixed parts, then player parts with wobble/selection
            val preview = Machine(board.copy(), gravity = level.gravity, airPressure = level.airPressure)
            preview.parts.forEachIndexed { i, part ->
                val key = if (i < board.fixed.size) -1 - i else i - board.fixed.size
                val wb = wobble[key] ?: 0.0
                p.save()
                if (wb > 0.02) { val c = part.center; p.translate(c.x, c.y); p.rotate(StrictMath.sin(t * 40) * 0.08 * wb); p.translate(-c.x, -c.y) }
                part.draw(p, t)
                p.restore()
            }
            // ropes and belts of the level
            previewLinks(p, preview)
            if (selected >= 0 && selected < board.playerParts.size) {
                val s = board.playerParts[selected]
                p.strokeRoundRect(s.x - 4, s.y - 4, s.w + 8, s.h + 8, 6.0, Style.BLUE, 2.5)
            }
            if (hintUntil > t) drawHint(p, t)
        }
        p.restore()
        p.restore()
        p.strokeRoundRect(f.minX, f.minY, f.width, f.height, 18 * u, Style.OUTLINE, 3 * u)
    }

    private fun previewLinks(p: Painter, preview: Machine) {
        // Machine.draw draws parts too; we only want links, so draw them via a temporary painter call.
        preview.drawLinksOnly(p)
    }

    private fun drawHint(p: Painter, t: Double) {
        val fade = ((hintUntil - t) / 0.5).coerceIn(0.0, 1.0)
        p.alpha = 0.45 * fade
        level.solution.forEachIndexed { i, pl ->
            val part = PartFactory.create(pl, 1000 + i)
            part.draw(p, t)
        }
        p.alpha = 0.9 * fade
        for (pl in level.solution) p.strokeRoundRect(pl.x - 3, pl.y - 3, pl.w + 6, pl.h + 6, 5.0, Style.YELLOW, 3.0)
        p.alpha = 1.0
    }

    private fun drawTray(p: Painter, t: Double) {
        val tr = layout.tray
        val u = game.u
        p.fillRect(tr.minX, tr.minY, tr.width, tr.height, Colors.rgb(0xC8D8EA))
        val area = trayTilesArea()
        p.save()
        p.clipRect(area.minX, area.minY, area.width, area.height)
        for (tile in tiles) {
            val r = tile.rect
            if (r.maxY < area.minY || r.minY > area.maxY) continue
            val n = remaining(tile.type)
            val enabled = n > 0 && !running
            p.fillRoundRect(r.minX, r.minY + 3 * u, r.width, r.height, 14 * u, Colors.withAlpha(Style.OUTLINE, 0.2))
            p.fillRoundRect(r.minX, r.minY, r.width, r.height, 14 * u, if (enabled) Style.WHITE else Colors.rgb(0xE6ECF2))
            p.strokeRoundRect(r.minX, r.minY, r.width, r.height, 14 * u, Style.OUTLINE, 2 * u)
            p.save()
            if (!enabled) p.alpha = 0.35
            val iconSize = r.height * 0.86
            p.translate(r.minX + (r.width - iconSize) / 2 - 8 * u, r.minY + (r.height - iconSize) / 2)
            tile.icon.drawIcon(p, iconSize)
            p.restore()
            // count badge
            if (!isFreeform) {
                val br = r.height * 0.2
                val bx = r.maxX - br - 4 * u; val by = r.minY + br + 4 * u
                p.fillCircle(bx, by, br, if (n > 0) Style.RED else Style.GREY)
                p.strokeCircle(bx, by, br, Style.OUTLINE, 2 * u)
                p.textCentered(n.toString(), bx, by, br * 1.3, Style.WHITE)
            }
        }
        p.restore()
        p.line(tr.minX, tr.minY, tr.minX, tr.maxY, Style.OUTLINE, 3 * u)
    }

    private fun drawTopBar(p: Painter, t: Double) {
        val tb = layout.topBar
        val u = game.u
        p.fillRect(tb.minX, tb.minY, tb.width, tb.height, Style.NAVY)
        // goal panel between the small buttons and the play button
        val left = hintButton.rect.maxX + 14 * u
        val right = tb.maxX - 14 * u
        val gw = right - left
        if (gw > 100 * u) {
            p.fillRoundRect(left, tb.minY + 10 * u, gw, tb.height - 20 * u, 14 * u, Style.CREAM)
            p.strokeRoundRect(left, tb.minY + 10 * u, gw, tb.height - 20 * u, 14 * u, Style.OUTLINE, 2 * u)
            val title = if (isFreeform) "Free play: build anything!" else level.goalText
            var size = 28 * u
            while (size > 14 * u && p.textWidth(title, size) > gw - 30 * u) size -= 2 * u
            p.textCentered(title, left + gw / 2, tb.center.y, size, Style.OUTLINE)
            if (!isFreeform) {
                val badge = 46 * u
                p.fillCircle(left + badge * 0.7, tb.center.y, badge * 0.45, Style.NAVY)
                p.textCentered("${levelIndex + 1}", left + badge * 0.7, tb.center.y, badge * 0.5, Style.WHITE)
            }
        }
    }

    private fun drawSelectionButtons(@Suppress("UNUSED_PARAMETER") p: Painter?) {
        val u = game.u
        val show = !running && selected >= 0 && selected < board.playerParts.size && drag == null
        flipButton.visible = false; rotateButton.visible = false; deleteButton.visible = false
        if (!show) return
        val s = board.playerParts[selected]
        val sc = layout.toScreen(Vec2(s.x + s.w / 2, s.y))
        val fs = 64 * u
        val items = ArrayList<Button>()
        if (s.type.flippable) items.add(flipButton)
        if (s.type.rotatable) items.add(rotateButton)
        items.add(deleteButton)
        val total = items.size * fs + (items.size - 1) * 10 * u
        var bx = (sc.x - total / 2).coerceIn(layout.field.minX, layout.field.maxX - total)
        var by = sc.y - fs - 16 * u
        if (by < layout.field.minY + 4 * u) by = layout.toScreen(Vec2(0.0, s.y + s.h)).y + 16 * u
        for (b in items) { b.rect = AABB(bx, by, bx + fs, by + fs); b.visible = true; bx += fs + 10 * u }
    }

    private fun drawDragGhost(p: Painter, t: Double) {
        val d = drag ?: return
        val f = layout.field
        p.save()
        p.translate(f.minX, f.minY); p.scale(layout.scale, layout.scale)
        val pl = Placement(d.type, d.worldPos.x, d.worldPos.y, d.flipped, d.rotation)
        val part = PartFactory.create(pl, 999)
        p.alpha = if (d.overTray) 0.4 else 0.85
        part.draw(p, t)
        p.alpha = 1.0
        val col = if (d.valid) Style.GREEN else Style.RED
        p.strokeRoundRect(pl.x - 4, pl.y - 4, pl.w + 8, pl.h + 8, 6.0, col, 3.0)
        if (!d.valid && !d.overTray) {
            p.line(pl.x - 4, pl.y - 4, pl.x + pl.w + 4, pl.y + pl.h + 4, col, 3.0)
            p.line(pl.x + pl.w + 4, pl.y - 4, pl.x - 4, pl.y + pl.h + 4, col, 3.0)
        }
        p.restore()
    }

    private fun drawWin(p: Painter) {
        val u = game.u
        val a = (celebrationTime / 0.4).coerceIn(0.0, 1.0)
        p.alpha = 0.55 * a
        p.fillRect(0.0, 0.0, game.width, game.height, Style.NAVY)
        p.alpha = 1.0
        confetti?.draw(p)
        val pw = 520 * u; val ph = 300 * u
        val px = game.width / 2 - pw / 2; val py = game.height / 2 - ph / 2 - 20 * u
        val pop = 1.0 + 0.15 * (1 - a) 
        p.save(); p.translate(game.width / 2, game.height / 2); p.scale(pop, pop); p.translate(-game.width / 2, -game.height / 2)
        p.panel(px, py, pw, ph, 28 * u, Style.CREAM, 4 * u)
        p.bigText("You did it!", game.width / 2, py + 70 * u, 56 * u, Style.YELLOW)
        val stars = if (hintsUsed == 0) 3 else 2
        for (i in 0 until 3) {
            p.save(); p.translate(game.width / 2 + (i - 1) * 90 * u, py + 150 * u); p.scale(1.6 * u, 1.6 * u)
            Icons.star(p, 0.0, 0.0, 48.0, if (i < stars) Style.YELLOW else Style.GREY_LIGHT)
            p.restore()
        }
        p.restore()
        nextButton.draw(p, u, game.clock)
        replayButton.draw(p, u, game.clock)
    }

    private fun drawFail(p: Painter) {
        val u = game.u
        val a = (failShown / 0.3).coerceIn(0.0, 1.0)
        val pw = 420 * u; val ph = 110 * u
        val px = game.width / 2 - pw / 2; val py = layout.field.minY + 20 * u
        p.alpha = a
        p.panel(px, py, pw, ph, 24 * u, Style.CREAM, 4 * u)
        p.textCentered("Hmm, not yet!", game.width / 2, py + 40 * u, 34 * u, Style.OUTLINE)
        p.textCentered("Let's try again", game.width / 2, py + 82 * u, 24 * u, Style.GREY_DARK)
        p.alpha = 1.0
    }
}
