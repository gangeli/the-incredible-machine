package tim.core.ui

import tim.core.game.Board
import tim.core.game.BoardCodec
import tim.core.game.Level
import tim.core.game.Link
import tim.core.game.LinkKind
import tim.core.game.LinkRules
import tim.core.game.Machine
import tim.core.game.Part
import tim.core.game.PartCategory
import tim.core.game.PartType
import tim.core.game.Placement
import tim.core.game.Style
import tim.core.game.parts.PartFactory
import tim.core.physics.AABB
import tim.core.physics.Vec2
import tim.core.physics.World
import tim.core.render.Colors
import tim.core.render.Painter
import tim.core.render.Path
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

/** A category tab above the tray tiles (only shown for big trays such as free play). */
class TrayTab(val category: PartCategory, val rect: AABB, val icon: Part)

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

/** State of the rope/belt/wire tool while the player is tapping the things to join. */
private class LinkMode(val kind: LinkKind, val tool: PartType) {
    var from = -1
    val via = ArrayList<Int>()
}

private class Snapshot(val parts: List<Placement>, val links: List<Link>)

/**
 * The puzzle screen: build mode (drag parts from the tray, tie ropes) and run mode (watch the
 * machine). Designed for small children: huge buttons, snapping, forgiving drops, no text to read
 * except the goal sentence.
 */
class PlayScreen(game: Game, val level: Level, val levelIndex: Int, initialBoard: Board? = null, savedId: Int? = null) : Screen(game) {
    companion object {
        const val TUTORIAL_LEVELS = 3
        /** Trays with more kinds of parts than this get category tabs and two columns. */
        const val BIG_TRAY = 8
    }
    val isFreeform = levelIndex < 0
    /** The first few puzzles act as a tutorial: an idle child gets shown where the first part goes. */
    val isTutorial = levelIndex in 0 until TUTORIAL_LEVELS
    var board: Board = initialBoard ?: level.newBoard()
        private set
    /** Free play: the gallery entry this build came from (null until first saved). */
    var savedId: Int? = savedId
        private set
    private val history = ArrayList<Snapshot>()
    var machine: Machine? = null
        private set
    /** Machine built from the current board, used to draw links and hit-test them while editing. */
    private var preview: Machine = Machine(board.copy(), gravity = level.gravity, airPressure = level.airPressure)
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
    private var hintMachine: Machine? = null
    private var selected = -1
    private var selectedLink = -1
    private var drag: Drag? = null
    private var linkMode: LinkMode? = null
    private var lastTouch = Vec2.ZERO
    private var touchStart = Vec2.ZERO
    private var touchMoved = false
    private var trayScroll = 0.0
    private var trayDragStartY = -1.0
    private var trayDragStartScroll = 0.0
    private var trayScrolling = false
    private var trayPressIndex = -1
    private var category: PartCategory? = null
    private var confirmClear = false
    private var confetti: Confetti? = null
    private var celebrationTime = 0.0
    private var wobble = HashMap<Int, Double>()
    private var toast = ""
    private var toastUntil = 0.0
    /** Seconds since the last touch; used to nudge a child who has not started. */
    private var idleTime = 0.0
    private var nudges = 0

    lateinit var layout: PlayLayout
    private val buttons = ArrayList<Button>()
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var homeButton: Button
    private lateinit var undoButton: Button
    private var saveButton: Button? = null
    private var machinesButton: Button? = null
    private var topBarEnd = 0.0
    private lateinit var resetButton: Button
    private var hintButton: Button? = null
    private lateinit var flipButton: Button
    private lateinit var rotateButton: Button
    private lateinit var deleteButton: Button
    private lateinit var unlinkButton: Button
    private lateinit var nextButton: Button
    private lateinit var replayButton: Button
    private lateinit var clearYesButton: Button
    private lateinit var clearNoButton: Button
    private var tiles: List<TrayTile> = emptyList()
    private var tabs: List<TrayTab> = emptyList()
    private val iconCache = HashMap<PartType, Part>()

    override fun onEnter() { relayout() }

    private fun icon(t: PartType): Part = iconCache.getOrPut(t) { PartFactory.create(Placement(t, 0.0, 0.0), 0) }

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
        resetButton = add(bh, Style.ORANGE, Icons::broom) { askClearAll() }
        if (isFreeform) {
            hintButton = null
            saveButton = add(bh * 1.5, Style.GREEN, Icons::save, "Save") { saveMachine() }
            machinesButton = add(bh * 1.5, Style.YELLOW, Icons::machines, "Mine") { game.play("tap"); game.toMachines() }
        } else {
            hintButton = add(bh, Style.YELLOW, Icons::bulb) { showHint() }
            saveButton = null; machinesButton = null
        }
        topBarEnd = bx - 10 * u
        // giant play/stop button at the bottom of the tray column
        val tr = layout.tray
        val ph = 118 * u
        playButton = Button(AABB(tr.minX + 10 * u, tr.maxY - ph - 12 * u, tr.maxX - 10 * u, tr.maxY - 12 * u), Style.GREEN, Icons::play, "", ::togglePlay)
        playButton.attention = 1.0
        buttons.add(playButton)
        stopButton = Button(playButton.rect, Style.RED, Icons::stop, "", ::togglePlay)
        stopButton.visible = false
        buttons.add(stopButton)
        // floating part actions (positioned when a part or link is selected)
        val fs = 64 * u
        flipButton = Button(AABB(0.0, 0.0, fs, fs), Style.TEAL, Icons::flip) { flipSelected() }
        rotateButton = Button(AABB(0.0, 0.0, fs, fs), Style.TEAL, Icons::rotate) { rotateSelected() }
        deleteButton = Button(AABB(0.0, 0.0, fs, fs), Style.RED, Icons::trash) { deleteSelected() }
        unlinkButton = Button(AABB(0.0, 0.0, fs, fs), Style.RED, Icons::scissors) { deleteSelectedLink() }
        listOf(flipButton, rotateButton, deleteButton, unlinkButton).forEach { it.visible = false; buttons.add(it) }
        // win overlay buttons
        val ww = 150 * u; val wh = 90 * u
        val wy = winPanel().minY + 225 * u
        nextButton = Button(AABB(game.width / 2 + 20 * u, wy, game.width / 2 + 20 * u + ww, wy + wh), Style.GREEN, Icons::next, "Next") { nextLevel() }
        replayButton = Button(AABB(game.width / 2 - 20 * u - ww, wy, game.width / 2 - 20 * u, wy + wh), Style.BLUE, Icons::replay, "Again") { stopRun(); game.play("tap") }
        buttons.add(nextButton); buttons.add(replayButton)
        // clear-all confirmation buttons
        val cw = 190 * u; val ch = 96 * u
        val cy = clearPanel().minY + 196 * u
        clearYesButton = Button(AABB(game.width / 2 + 16 * u, cy, game.width / 2 + 16 * u + cw, cy + ch), Style.RED, Icons::trash, "Yes, clear") { confirmClear = false; clearAll() }
        clearNoButton = Button(AABB(game.width / 2 - 16 * u - cw, cy, game.width / 2 - 16 * u, cy + ch), Style.BLUE, Icons::close, "No, keep") { confirmClear = false; game.play("tap") }
        buttons.add(clearYesButton); buttons.add(clearNoButton)
        // a relayout (screen resize) must not lose the current state of the overlays
        nextButton.visible = won; replayButton.visible = won
        clearYesButton.visible = confirmClear; clearNoButton.visible = confirmClear
        syncPlayButtons()
        buildTabs()
        buildTiles()
    }

    /** The celebration panel. */
    private fun winPanel(): AABB {
        val u = game.u
        val pw = 520 * u; val ph = 330 * u
        return AABB(game.width / 2 - pw / 2, game.height / 2 - ph / 2 - 10 * u, game.width / 2 + pw / 2, game.height / 2 + ph / 2 - 10 * u)
    }

    /** The "sweep everything away?" panel. */
    private fun clearPanel(): AABB {
        val u = game.u
        val pw = 560 * u; val ph = 310 * u
        return AABB(game.width / 2 - pw / 2, game.height / 2 - ph / 2, game.width / 2 + pw / 2, game.height / 2 + ph / 2)
    }

    // ---------------------------------------------------------------- tray

    private fun trayTypes(): List<PartType> = if (isFreeform) PartType.values().toList() else level.tray.map { it.type }.distinct()
    private val bigTray: Boolean get() = trayTypes().size > BIG_TRAY

    private fun buildTabs() {
        val u = game.u
        val tr = layout.tray
        tabs = if (!bigTray) emptyList() else {
            val cats = trayTypes().map { it.category }.distinct()
            // up to four big tabs per row so each one is an easy target for small fingers
            val perRow = minOf(4, cats.size)
            val gap = 5 * u
            val tw = (tr.width - 12 * u - gap * (perRow - 1)) / perRow
            val th = 50 * u
            cats.mapIndexed { i, c ->
                val rep = when (c) {
                    PartCategory.BALL -> PartType.BASKETBALL; PartCategory.STRUCTURE -> PartType.BRICK_WALL
                    PartCategory.MACHINE -> PartType.FAN; PartCategory.CREATURE -> PartType.CAT
                    PartCategory.TRIGGER -> PartType.SWITCH; PartCategory.GOAL -> PartType.STAR; PartCategory.LINK -> PartType.ROPE
                }
                val col = i % perRow; val row = i / perRow
                val x0 = tr.minX + 6 * u + col * (tw + gap)
                val y0 = tr.minY + 8 * u + row * (th + gap)
                TrayTab(c, AABB(x0, y0, x0 + tw, y0 + th), icon(rep))
            }
        }
        if (category == null && tabs.isNotEmpty()) category = tabs[0].category
    }

    private fun tilesTop(): Double = if (tabs.isEmpty()) layout.tray.minY + 12 * game.u else tabs.maxOf { it.rect.maxY } + 10 * game.u

    private fun buildTiles() {
        val u = game.u
        val tr = layout.tray
        val types = trayTypes().filter { tabs.isEmpty() || category == null || it.category == category }
        val cols = if (bigTray) 2 else 1
        val gap = 10 * u
        val size = (tr.width - 24 * u - (cols - 1) * gap) / cols
        val th = if (cols == 1) size * 0.8 else size * 0.95
        val list = ArrayList<TrayTile>()
        types.forEachIndexed { i, t ->
            val col = i % cols; val row = i / cols
            val tx = tr.minX + 12 * u + col * (size + gap)
            val ty = tilesTop() + row * (th + gap) - trayScroll
            list.add(TrayTile(t, AABB(tx, ty, tx + size, ty + th), icon(t)))
        }
        tiles = list
    }

    private fun trayContentHeight(): Double {
        val u = game.u
        val cols = if (bigTray) 2 else 1
        val gap = 10 * u
        val size = (layout.tray.width - 24 * u - (cols - 1) * gap) / cols
        val th = if (cols == 1) size * 0.8 else size * 0.95
        val n = trayTypes().count { tabs.isEmpty() || category == null || it.category == category }
        val rows = (n + cols - 1) / cols
        return rows * (th + gap) + 14 * u
    }

    /** Tray area available for tiles (below the tabs, above the play button). */
    private fun trayTilesArea(): AABB = AABB(layout.tray.minX, tilesTop() - 2 * game.u, layout.tray.maxX, playButton.rect.minY - 10 * game.u)

    private fun selectTab(c: PartCategory) {
        if (category == c) return
        category = c
        trayScroll = 0.0
        buildTiles()
        game.play("tap")
    }

    /** Remaining count for a tray type (tools count the links already tied). */
    fun remaining(t: PartType): Int {
        if (isFreeform) return 99
        val total = level.tray.filter { it.type == t }.sumOf { it.count }
        val used = if (t.isTool) board.playerLinks.count { LinkRules.toolFor(it.kind) == t }
        else board.playerParts.count { it.type == t } + (if (drag?.type == t && drag?.existing == -1) 1 else 0)
        return total - used
    }

    // ---------------------------------------------------------------- editing

    private fun boardChanged() {
        preview = Machine(board.copy(), gravity = level.gravity, airPressure = level.airPressure)
        // free play never loses work: every edit is kept so the next visit resumes here
        if (isFreeform) game.machines.current = BoardCodec.encode(board)
    }

    private fun saveMachine() {
        if (running) return
        linkMode = null
        if (board.playerParts.isEmpty()) { game.play("nope"); showToast("Build something first!", 2.0); return }
        val id = game.machines.save(savedId, BoardCodec.encode(board))
        savedId = id
        game.machines.currentId = id
        showToast("Saved as ${game.machines.nameOf(id)}", 3.0)
        game.play("win")
        val r = saveButton?.rect ?: layout.field
        sparkle = Confetti(game.width, game.height).also { it.burst(r.center.x, r.maxY, 40) }
        sparkleTime = 0.0
    }
    private var sparkle: Confetti? = null
    private var sparkleTime = 0.0

    private fun pushHistory() { history.add(Snapshot(ArrayList(board.playerParts), ArrayList(board.playerLinks))); if (history.size > 50) history.removeAt(0) }

    private fun undo() {
        if (running) return
        linkMode = null
        if (history.isEmpty()) { game.play("nope"); return }
        val prev = history.removeAt(history.size - 1)
        board.playerParts.clear(); board.playerParts.addAll(prev.parts)
        board.playerLinks.clear(); board.playerLinks.addAll(prev.links)
        selected = -1; selectedLink = -1
        boardChanged()
        game.play("undo")
    }

    private fun askClearAll() {
        if (running) return
        linkMode = null
        if (board.playerParts.isEmpty() && board.playerLinks.isEmpty()) { game.play("nope"); return }
        confirmClear = true
        game.play("tap")
    }

    private fun clearAll() {
        pushHistory()
        board.playerParts.clear()
        board.playerLinks.clear()
        selected = -1; selectedLink = -1
        boardChanged()
        game.play("whoosh")
    }

    private fun showHint() {
        if (running || level.solution.isEmpty()) return
        hintsUsed++
        hintUntil = game.clock + 4.0
        if (hintMachine == null) hintMachine = Machine(level.solvedBoard(), gravity = level.gravity, airPressure = level.airPressure)
        game.play("hint")
    }

    private fun snap(v: Double) = (v / Machine.GRID).roundToInt() * Machine.GRID

    private fun fits(pl: Placement, ignoreIndex: Int): Boolean {
        if (pl.x < 0 || pl.y < 0 || pl.x + pl.w > Machine.WIDTH || pl.y + pl.h > Machine.HEIGHT) return false
        if (tim.core.game.Fit.overlapsAny(pl, board.fixed)) return false
        if (tim.core.game.Fit.overlapsAny(pl, board.playerParts, ignoreIndex)) return false
        return true
    }

    /** Find the nearest valid grid spot near the requested placement (forgiving drops for kids). */
    private fun findSpot(pl: Placement, ignoreIndex: Int): Placement? {
        if (fits(pl, ignoreIndex)) return pl
        val g = Machine.GRID
        val maxRing = (24.0 / g).toInt()
        for (ring in 1..maxRing) {
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
        boardChanged()
        game.play("flip")
    }

    private fun rotateSelected() {
        val i = selected; if (i < 0 || running) return
        val p = board.playerParts[i]
        if (!p.type.rotatable) return
        val r = p.copy(rotation = (p.rotation + 1) % 4)
        val moved = r.moved(snap(p.cx - r.w / 2), snap(p.cy - r.h / 2))
        val spot = findSpot(moved, i) ?: run { game.play("nope"); return }
        pushHistory()
        board.playerParts[i] = spot
        wobble[i] = 1.0
        boardChanged()
        game.play("flip")
    }

    private fun deleteSelected() {
        val i = selected; if (i < 0 || running) return
        pushHistory()
        board.removePlayerPart(i)
        selected = -1
        boardChanged()
        game.play("whoosh")
    }

    private fun deleteSelectedLink() {
        val i = selectedLink; if (i < 0 || i >= board.playerLinks.size || running) return
        pushHistory()
        board.playerLinks.removeAt(i)
        selectedLink = -1
        boardChanged()
        game.play("snip")
    }

    // ---------------------------------------------------------------- links

    private fun startLinkMode(tool: PartType) {
        val kind = LinkRules.kindOf(tool) ?: return
        if (remaining(tool) <= 0) { game.play("nope"); return }
        linkMode = LinkMode(kind, tool)
        selected = -1; selectedLink = -1
        game.play("pick")
        showToast(when (kind) { LinkKind.ROPE -> "Tap the first thing to tie"; LinkKind.BELT -> "Tap the motor or the belt"; LinkKind.WIRE -> "Tap the switch or the machine" }, 6.0)
    }

    /** Index into board.all of the part under a world point that the current tool may use. */
    private fun linkTargetAt(w: Vec2, kind: LinkKind): Int {
        var best = -1; var bestD = Double.MAX_VALUE
        board.all.forEachIndexed { i, p ->
            if (!LinkRules.candidate(kind, p.type)) return@forEachIndexed
            val slop = maxOf(12.0, (48.0 - minOf(p.w, p.h)) / 2)
            if (p.aabb.expanded(slop).contains(w)) {
                val d = (p.aabb.center - w).lengthSq
                if (d < bestD) { bestD = d; best = i }
            }
        }
        return best
    }

    private fun toolName(kind: LinkKind) = when (kind) { LinkKind.ROPE -> "rope"; LinkKind.BELT -> "belt"; LinkKind.WIRE -> "wire" }

    /** What the current tool can be attached to, in words a child can follow. */
    private fun canTieText(kind: LinkKind) = when (kind) {
        LinkKind.ROPE -> "A rope ties to a bucket, hook, cage, balloon or seesaw"
        LinkKind.BELT -> "A belt joins a motor and a conveyor belt"
        LinkKind.WIRE -> "A wire joins a switch or outlet to a fan, motor, conveyor or flashlight"
    }

    /** Any part under the point at all (used to explain why it cannot be tied). */
    private fun anyPartAt(w: Vec2): Int = board.all.indexOfFirst { it.aabb.expanded(8.0).contains(w) }

    private fun linkTap(w: Vec2) {
        val lm = linkMode ?: return
        val idx = linkTargetAt(w, lm.kind)
        if (idx < 0) {
            // explain, and keep the tool in hand
            val other = anyPartAt(w)
            game.play("nope")
            if (other >= 0) showToast("You can't tie the ${board.all[other].type.label.lowercase()}. ${canTieText(lm.kind)}", 4.0)
            else showToast("Nothing to tie there. ${canTieText(lm.kind)}", 4.0)
            return
        }
        val type = board.all[idx].type
        if (lm.from < 0) {
            if (!LinkRules.canStart(lm.kind, type)) { game.play("nope"); showToast("Start with something else: ${canTieText(lm.kind).lowercase()}", 4.0); return }
            if (alreadyLinked(idx, lm.kind)) { game.play("nope"); showToast("That ${type.label.lowercase()} already has a ${toolName(lm.kind)}", 3.0); return }
            lm.from = idx
            game.play("click")
            showToast(if (lm.kind == LinkKind.ROPE) "Now tap the other end (or a pulley on the way)" else "Now tap the other one", 6.0)
            return
        }
        if (idx == lm.from) { linkMode = null; showToast("${toolName(lm.kind).replaceFirstChar { it.uppercase() }} put away", 1.5); game.play("undo"); return }
        if (lm.kind == LinkKind.ROPE && LinkRules.isPulley(type)) {
            if (idx !in lm.via) { lm.via.add(idx); game.play("click"); showToast("Over the pulley! Now tap the other end", 4.0) }
            return
        }
        if (alreadyLinked(idx, lm.kind)) { game.play("nope"); showToast("That ${type.label.lowercase()} already has a ${toolName(lm.kind)}", 3.0); return }
        val link = LinkRules.connect(lm.kind, lm.from, board.all[lm.from].type, idx, type, lm.via)
        if (link == null) { game.play("nope"); showToast("Those two don't go together. ${canTieText(lm.kind)}", 4.0); return }
        pushHistory()
        board.playerLinks.add(link)
        selectedLink = board.playerLinks.size - 1
        linkMode = null
        boardChanged()
        game.play(if (lm.kind == LinkKind.ROPE) "drop" else "click")
        toastUntil = 0.0
    }

    /** A part may carry one rope, one belt, and (as a consumer) one wire. */
    private fun alreadyLinked(idx: Int, kind: LinkKind): Boolean = board.links.any { it.kind == kind && (it.from == idx || it.to == idx) && !(kind == LinkKind.WIRE && it.from == idx) }

    private fun linkAt(w: Vec2): Int {
        var best = -1; var bestD = 8.0
        board.playerLinks.forEachIndexed { i, l ->
            val pts = preview.linkPath(l)
            for (k in 0 until pts.size - 1) {
                val d = distanceToSegment(w, pts[k], pts[k + 1])
                if (d < bestD) { bestD = d; best = i }
            }
        }
        return best
    }

    private fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val ab = b - a
        val l2 = ab.lengthSq
        if (l2 < 1e-9) return p.distanceTo(a)
        val t = ((p - a) dot ab / l2).coerceIn(0.0, 1.0)
        return p.distanceTo(a + ab * t)
    }

    // ---------------------------------------------------------------- running

    private fun togglePlay() { if (running) stopRun() else startRun() }

    private fun syncPlayButtons() { playButton.visible = !running; stopButton.visible = running }

    fun startRun() {
        if (running) return
        selected = -1; selectedLink = -1
        drag = null
        linkMode = null
        confirmClear = false
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
            sparkle?.let { sp -> sparkleTime += dt; sp.update(dt); if (sparkleTime > 2.5) sparkle = null }
            idleTime += dt
            // Nobody has placed anything for a while: show where the first part goes (twice at most).
            if (isTutorial && board.playerParts.isEmpty() && drag == null && idleTime > 7.0 && nudges < 2 && hintUntil < game.clock) {
                hintUntil = game.clock + 5.0
                if (hintMachine == null) hintMachine = Machine(level.solvedBoard(), gravity = level.gravity, airPressure = level.airPressure)
                nudges++
                idleTime = 0.0
                game.play("hint")
            }
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
        sparkle?.let { sp -> sparkleTime += dt; sp.update(dt); if (sparkleTime > 2.5) sparkle = null }
        if (won) {
            celebrationTime += dt
            confetti?.update(dt)
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

    private fun showToast(s: String, seconds: Double = 2.5) { toast = s; toastUntil = game.clock + seconds }

    // ---------------------------------------------------------------- input

    override fun touch(ev: TouchEvent) {
        if (ev.pointer != 0) return
        val x = ev.x; val y = ev.y
        lastTouch = Vec2(x, y)
        when (ev.action) {
            TouchAction.DOWN -> onDown(x, y)
            TouchAction.MOVE -> onMove(x, y)
            TouchAction.UP -> onUp(x, y)
            TouchAction.CANCEL -> { drag = null; trayScrolling = false; buttons.forEach { it.pressed = false } }
        }
    }

    private var pressedButton: Button? = null

    private fun onDown(x: Double, y: Double) {
        idleTime = 0.0
        touchStart = Vec2(x, y); touchMoved = false
        if (confirmClear) {
            pressedButton = listOf(clearYesButton, clearNoButton).firstOrNull { it.hit(x, y) }
            pressedButton?.pressed = true
            // a tap anywhere outside the panel just closes it, keeping everything
            if (pressedButton == null && !clearPanel().contains(Vec2(x, y))) { confirmClear = false; game.play("tap") }
            return
        }
        pressedButton = buttons.lastOrNull { it.hit(x, y) }
        pressedButton?.let { it.pressed = true; return }
        if (running) return
        // tray tabs
        tabs.firstOrNull { it.rect.contains(Vec2(x, y)) }?.let { selectTab(it.category); return }
        // tray
        if (layout.tray.contains(Vec2(x, y))) {
            trayPressIndex = tiles.indexOfFirst { it.rect.contains(Vec2(x, y)) && trayTilesArea().contains(Vec2(x, y)) }
            if (linkMode != null && (trayPressIndex < 0 || tiles[trayPressIndex].type != linkMode!!.tool)) { linkMode = null; showToast("Tool put away", 1.5) }
            trayDragStartY = y; trayDragStartScroll = trayScroll; trayScrolling = false
            return
        }
        // playfield
        if (layout.field.expanded(20 * game.u).contains(Vec2(x, y))) {
            val wpos = layout.toWorld(x, y)
            if (linkMode != null) { linkTap(wpos); return }
            val hitIndex = hitPlayerPart(wpos)
            if (hitIndex >= 0) {
                val p = board.playerParts[hitIndex]
                selected = hitIndex; selectedLink = -1
                drag = Drag(p.type, hitIndex, p, p.flipped, p.rotation, Vec2(p.x, p.y))
                updateDrag(x, y)
                game.play("pick")
                return
            }
            val link = linkAt(wpos)
            if (link >= 0) { selectedLink = link; selected = -1; game.play("tap"); return }
            // tap on empty: deselect (a fixed part wiggles to say it's fixed)
            selected = -1; selectedLink = -1
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
        if (running || confirmClear) return
        val d = drag
        if (d != null) { updateDrag(x, y); return }
        if (trayDragStartY >= 0) {
            val dy = y - trayDragStartY
            val idx = trayPressIndex
            // pulling a tile starts a drag; only a mostly vertical pull on a tray that can scroll scrolls it
            val scrollable = trayContentHeight() > trayTilesArea().height + 1
            val dx = x - touchStart.x
            val pull = (Vec2(x, y) - touchStart).length > 10 * game.u
            val wantsDrag = pull && (!scrollable || abs(dx) >= abs(dy) || x < layout.tray.minX)
            if (!trayScrolling && idx >= 0 && remaining(tiles[idx].type) > 0 && wantsDrag) {
                val t = tiles[idx].type
                trayDragStartY = -1.0
                trayPressIndex = -1
                if (t.isTool) { startLinkMode(t); return }
                drag = Drag(t, -1, null, false, 0, Vec2.ZERO)
                selected = -1; selectedLink = -1
                updateDrag(x, y)
                game.play("pick")
                return
            }
            if (scrollable && (abs(dy) > 12 * game.u || trayScrolling)) {
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
        if (confirmClear) return
        trayDragStartY = -1.0
        val wasScrolling = trayScrolling
        trayScrolling = false
        if (running) return
        val d = drag
        if (d != null) {
            drag = null
            val pl = Placement(d.type, d.worldPos.x, d.worldPos.y, d.flipped, d.rotation)
            if (d.overTray) {
                if (d.existing >= 0) { pushHistory(); board.removePlayerPart(d.existing); selected = -1; boardChanged(); game.play("whoosh") }
                return
            }
            if (!touchMoved && d.existing >= 0) {
                selected = d.existing
                game.play("tap")
                return
            }
            val spot = findSpot(pl, d.existing)
            if (spot == null) {
                if (d.existing >= 0) selected = d.existing
                game.play("nope")
                showToast("No room there!")
                return
            }
            pushHistory()
            if (d.existing >= 0) { board.playerParts[d.existing] = spot; selected = d.existing }
            else { board.playerParts.add(spot); selected = board.playerParts.size - 1 }
            wobble[selected] = 1.0
            boardChanged()
            game.play("drop")
            return
        }
        // tap on a tray tile without dragging: tools start tying, parts land in a free spot near the tray
        if (!wasScrolling && !touchMoved && trayPressIndex >= 0 && layout.tray.contains(Vec2(x, y))) {
            val t = tiles[trayPressIndex].type
            trayPressIndex = -1
            if (t.isTool) {
                if (linkMode?.tool == t) { linkMode = null; showToast("${toolName(LinkRules.kindOf(t)!!).replaceFirstChar { it.uppercase() }} put away", 1.5); game.play("undo") }
                else startLinkMode(t)
                return
            }
            if (remaining(t) <= 0) { game.play("nope"); return }
            val start = Placement(t, snap(Machine.WIDTH - t.w - 40), snap(60.0))
            val spot = findSpot(start, -1) ?: findAnySpot(t) ?: run { game.play("nope"); return }
            pushHistory()
            board.playerParts.add(spot)
            selected = board.playerParts.size - 1; selectedLink = -1
            wobble[selected] = 1.0
            boardChanged()
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
    fun tabRects(): List<Pair<PartCategory, AABB>> = tabs.map { it.category to it.rect }
    fun playButtonRect(): AABB = playButton.rect
    val hintShowing: Boolean get() = hintUntil > game.clock
    val linking: Boolean get() = linkMode != null
    val clearDialogShowing: Boolean get() = confirmClear
    fun clearDialogButtons(): Pair<AABB, AABB> = clearYesButton.rect to clearNoButton.rect
    fun winButtons(): Pair<AABB, AABB> = replayButton.rect to nextButton.rect
    fun topBarButtonRect(name: String): AABB = when (name) { "home" -> homeButton; "undo" -> undoButton; "clear" -> resetButton; "save" -> saveButton!!; "machines" -> machinesButton!!; else -> hintButton!! }.rect
    val dragging: Boolean get() = drag != null
    /** Screen rectangle of a floating action button ("flip", "rotate", "delete", "unlink"), if visible. */
    fun actionButton(name: String): AABB? {
        drawSelectionButtons()
        val b = when (name) { "flip" -> flipButton; "rotate" -> rotateButton; "unlink" -> unlinkButton; else -> deleteButton }
        return if (b.visible) b.rect else null
    }

    override fun back(): Boolean {
        if (confirmClear) { confirmClear = false; return true }
        if (linkMode != null) { linkMode = null; return true }
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
        drawSelectionButtons()
        syncPlayButtons()
        for (b in buttons) if (b !== nextButton && b !== replayButton && b !== clearYesButton && b !== clearNoButton) b.draw(p, u, t)
        drawDragGhost(p, t)
        sparkle?.draw(p)
        if (hintUntil > t && board.playerParts.isEmpty() && !running) drawNudgeArrow(p, t)
        if (won) drawWin(p)
        else if (failed) drawFail(p)
        if (confirmClear) drawClearDialog(p)
    }

    private fun drawField(p: Painter, t: Double) {
        val f = layout.field
        val u = game.u
        p.fillRoundRect(f.minX, f.minY + 6 * u, f.width, f.height, 18 * u, Colors.withAlpha(Style.OUTLINE, 0.25))
        p.save()
        p.clipRoundRect(f.minX, f.minY, f.width, f.height, 18 * u)
        p.gradientRect(f.minX, f.minY, f.width, f.height, Colors.rgb(0xF6FAFF), Colors.rgb(0xDDEBF9), true)
        p.save()
        p.translate(f.minX, f.minY); p.scale(layout.scale, layout.scale)
        val dot = Colors.withAlpha(Style.OUTLINE, 0.08)
        val dotStep = 32.0
        var gy = dotStep
        while (gy < Machine.HEIGHT) { var gx = dotStep; while (gx < Machine.WIDTH) { p.fillCircle(gx, gy, 1.2, dot); gx += dotStep }; gy += dotStep }
        val m = machine
        if (m != null) {
            m.draw(p, m.time)
        } else {
            // build mode: wires first, then fixed and player parts with wobble, then ropes/belts on top
            preview.drawLinksOnly(p)
            preview.parts.forEachIndexed { i, part ->
                val key = if (i < board.fixed.size) -1 - i else i - board.fixed.size
                val wb = wobble[key] ?: 0.0
                p.save()
                if (wb > 0.02) { val c = part.center; p.translate(c.x, c.y); p.rotate(kotlin.math.sin(t * 40) * 0.08 * wb); p.translate(-c.x, -c.y) }
                part.draw(p, t)
                p.restore()
            }
            preview.drawLinksOnly(p)
            if (selected >= 0 && selected < board.playerParts.size) {
                val s = board.playerParts[selected]
                p.strokeRoundRect(s.x - 4, s.y - 4, s.w + 8, s.h + 8, 6.0, Style.BLUE, 2.5)
            }
            if (selectedLink >= 0 && selectedLink < board.playerLinks.size) {
                val pts = preview.linkPath(board.playerLinks[selectedLink])
                for (k in 0 until pts.size - 1) p.line(pts[k].x, pts[k].y, pts[k + 1].x, pts[k + 1].y, Colors.withAlpha(Style.BLUE, 0.6), 8.0)
            }
            linkMode?.let { drawLinkMode(p, it, t) }
            if (hintUntil > t) drawHint(p, t)
        }
        p.restore()
        p.restore()
        p.strokeRoundRect(f.minX, f.minY, f.width, f.height, 18 * u, Style.OUTLINE, 3 * u)
    }

    /** Highlights everything the current tool can touch and draws the rope being strung. */
    private fun drawLinkMode(p: Painter, lm: LinkMode, t: Double) {
        val pulse = 0.5 + 0.5 * kotlin.math.sin(t * 6)
        board.all.forEachIndexed { i, pl ->
            if (!LinkRules.candidate(lm.kind, pl.type)) return@forEachIndexed
            val col = if (i == lm.from || i in lm.via) Style.GREEN else Style.YELLOW
            p.alpha = 0.5 + 0.5 * pulse
            p.strokeRoundRect(pl.x - 5, pl.y - 5, pl.w + 10, pl.h + 10, 7.0, col, 3.5)
            p.alpha = 1.0
        }
        if (lm.from >= 0) {
            val pts = ArrayList<Vec2>()
            pts.add(board.all[lm.from].aabb.center)
            for (v in lm.via) pts.add(board.all[v].aabb.center)
            pts.add(layout.toWorld(lastTouch.x, lastTouch.y))
            for (k in 0 until pts.size - 1) drawDashed(p, pts[k], pts[k + 1], Style.OUTLINE, 3.0)
        }
    }

    private fun drawDashed(p: Painter, a: Vec2, b: Vec2, color: Int, width: Double) {
        val d = b - a
        val len = d.length
        if (len < 1e-6) return
        val n = d / len
        var s = 0.0
        while (s < len) {
            val e = minOf(len, s + 8.0)
            p.line(a.x + n.x * s, a.y + n.y * s, a.x + n.x * e, a.y + n.y * e, color, width)
            s += 14.0
        }
    }

    private fun drawHint(p: Painter, t: Double) {
        val fade = ((hintUntil - t) / 0.5).coerceIn(0.0, 1.0)
        val hm = hintMachine ?: return
        p.alpha = 0.45 * fade
        for (i in level.solution.indices) hm.parts[board.fixed.size + i].draw(p, t)
        p.alpha = 0.9 * fade
        for (pl in level.solution) p.strokeRoundRect(pl.x - 3, pl.y - 3, pl.w + 6, pl.h + 6, 5.0, Style.YELLOW, 3.0)
        for (l in level.solutionLinks) {
            val pts = hm.linkPath(l)
            for (k in 0 until pts.size - 1) drawDashed(p, pts[k], pts[k + 1], Style.YELLOW, 4.0)
        }
        p.alpha = 1.0
    }

    private fun drawTray(p: Painter, t: Double) {
        val tr = layout.tray
        val u = game.u
        p.fillRect(tr.minX, tr.minY, tr.width, tr.height, Colors.rgb(0xC8D8EA))
        // category tabs
        for (tab in tabs) {
            val r = tab.rect
            val on = tab.category == category
            p.fillRoundRect(r.minX, r.minY, r.width, r.height, 10 * u, if (on) Style.WHITE else Colors.rgb(0xB3C6DB))
            p.strokeRoundRect(r.minX, r.minY, r.width, r.height, 10 * u, if (on) Style.OUTLINE else Colors.withAlpha(Style.OUTLINE, 0.4), 2 * u)
            p.save()
            p.clipRoundRect(r.minX, r.minY, r.width, r.height, 10 * u)
            if (!on) p.alpha = 0.55
            val sz = minOf(r.height, r.width) * 0.84
            p.translate(r.center.x - sz / 2, r.minY + (r.height - sz) / 2)
            tab.icon.drawIcon(p, sz)
            p.restore()
        }
        val area = trayTilesArea()
        p.save()
        p.clipRect(area.minX, area.minY, area.width, area.height)
        for (tile in tiles) {
            val r = tile.rect
            if (r.maxY < area.minY || r.minY > area.maxY) continue
            val n = remaining(tile.type)
            val enabled = n > 0 && !running
            val active = linkMode?.tool == tile.type
            p.fillRoundRect(r.minX, r.minY + 3 * u, r.width, r.height, 14 * u, Colors.withAlpha(Style.OUTLINE, 0.2))
            p.fillRoundRect(r.minX, r.minY, r.width, r.height, 14 * u, if (active) Style.YELLOW else if (enabled) Style.WHITE else Colors.rgb(0xE6ECF2))
            p.strokeRoundRect(r.minX, r.minY, r.width, r.height, 14 * u, Style.OUTLINE, 2 * u)
            p.save()
            p.clipRoundRect(r.minX, r.minY, r.width, r.height, 14 * u)
            if (!enabled) p.alpha = 0.35
            val iconSize = minOf(r.height * 0.86, r.width * 0.86)
            p.translate(r.minX + (r.width - iconSize) / 2 - (if (isFreeform) 0.0 else 8 * u), r.minY + (r.height - iconSize) / 2)
            tile.icon.drawIcon(p, iconSize)
            p.restore()
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
        val left = topBarEnd + 14 * u
        val right = tb.maxX - 14 * u
        val gw = right - left
        if (gw > 100 * u) {
            // messages (what a tool can tie, "no room there") take over the banner so they never cover the field
            val showingToast = toastUntil > t
            p.fillRoundRect(left, tb.minY + 10 * u, gw, tb.height - 20 * u, 14 * u, if (showingToast) Style.YELLOW else Style.CREAM)
            p.strokeRoundRect(left, tb.minY + 10 * u, gw, tb.height - 20 * u, 14 * u, Style.OUTLINE, 2 * u)
            val title = if (showingToast) toast else if (isFreeform) (savedId?.let { game.machines.nameOf(it) } ?: "Free play: build anything!") else level.goalText
            val textLeft = if (isFreeform) left + 15 * u else left + 60 * u
            val avail = right - 15 * u - textLeft
            var size = 28 * u
            while (size > 18 * u && p.textWidth(title, size) > avail) size -= 2 * u
            val cut = title.indexOf(". ")
            if (p.textWidth(title, size) > avail && cut > 0) {
                // two short lines beat one unreadable one
                val a = title.substring(0, cut + 1); val b = title.substring(cut + 2)
                var s2 = 20 * u
                while (s2 > 12 * u && maxOf(p.textWidth(a, s2), p.textWidth(b, s2)) > avail) s2 -= 1 * u
                p.textCentered(a, textLeft + avail / 2, tb.center.y - s2 * 0.6, s2, Style.OUTLINE)
                p.textCentered(b, textLeft + avail / 2, tb.center.y + s2 * 0.6, s2, Style.OUTLINE)
            } else {
                while (size > 12 * u && p.textWidth(title, size) > avail) size -= 1 * u
                p.textCentered(title, textLeft + avail / 2, tb.center.y, size, Style.OUTLINE)
            }
            if (!isFreeform) {
                val badge = 46 * u
                p.fillCircle(left + badge * 0.7, tb.center.y, badge * 0.45, Style.NAVY)
                p.textCentered("${levelIndex + 1}", left + badge * 0.7, tb.center.y, badge * 0.5, Style.WHITE)
            }
        }
    }

    private fun drawSelectionButtons() {
        val u = game.u
        flipButton.visible = false; rotateButton.visible = false; deleteButton.visible = false; unlinkButton.visible = false
        if (running || drag != null || linkMode != null || confirmClear) return
        val fs = 64 * u
        if (selected >= 0 && selected < board.playerParts.size) {
            val s = board.playerParts[selected]
            val sc = layout.toScreen(Vec2(s.x + s.w / 2, s.y))
            val items = ArrayList<Button>()
            if (s.type.flippable) items.add(flipButton)
            if (s.type.rotatable) items.add(rotateButton)
            items.add(deleteButton)
            val total = items.size * fs + (items.size - 1) * 10 * u
            var bx = (sc.x - total / 2).coerceIn(layout.field.minX, layout.field.maxX - total)
            var by = sc.y - fs - 16 * u
            if (by < layout.field.minY + 4 * u) by = layout.toScreen(Vec2(0.0, s.y + s.h)).y + 16 * u
            for (b in items) { b.rect = AABB(bx, by, bx + fs, by + fs); b.visible = true; bx += fs + 10 * u }
        } else if (selectedLink >= 0 && selectedLink < board.playerLinks.size) {
            val pts = preview.linkPath(board.playerLinks[selectedLink])
            if (pts.size >= 2) {
                val mid = layout.toScreen((pts[0] + pts[pts.size - 1]) / 2.0)
                val bx = (mid.x - fs / 2).coerceIn(layout.field.minX, layout.field.maxX - fs)
                val by = (mid.y - fs - 12 * u).coerceIn(layout.field.minY, layout.field.maxY - fs)
                unlinkButton.rect = AABB(bx, by, bx + fs, by + fs); unlinkButton.visible = true
            }
        }
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

    /** A big bouncing arrow from the tray tile of the first solution part to where it should go. */
    private fun drawNudgeArrow(p: Painter, t: Double) {
        val first = level.solution.firstOrNull() ?: return
        val tile = tiles.firstOrNull { it.type == first.type } ?: return
        val u = game.u
        val from = Vec2(tile.rect.minX - 6 * u, tile.rect.center.y)
        val to = layout.toScreen(Vec2(first.x + first.w / 2, first.y + first.h / 2))
        val bounce = (kotlin.math.sin(t * 5) * 0.5 + 0.5)
        val dir = (to - from).normalized()
        val end = to - dir * (40 * u + bounce * 14 * u)
        val start = from - dir * (bounce * 6 * u)
        val path = Path()
        path.moveTo(start.x, start.y)
        path.quadTo((start.x + end.x) / 2, minOf(start.y, end.y) - 80 * u, end.x, end.y)
        p.strokePath(path, Style.OUTLINE, 12 * u)
        p.strokePath(path, Style.YELLOW, 7 * u)
        val tangent = (end - Vec2((start.x + end.x) / 2, minOf(start.y, end.y) - 80 * u)).normalized()
        val n = tangent.perp()
        val tip = end + tangent * (18 * u)
        val head = Path.polygon(tip.x, tip.y, end.x + n.x * 16 * u, end.y + n.y * 16 * u, end.x - n.x * 16 * u, end.y - n.y * 16 * u)
        p.fillPath(head, Style.YELLOW)
        p.strokePath(head, Style.OUTLINE, 3 * u)
    }

    private fun drawClearDialog(p: Painter) {
        val u = game.u
        p.alpha = 0.5
        p.fillRect(0.0, 0.0, game.width, game.height, Style.NAVY)
        p.alpha = 1.0
        val r = clearPanel()
        p.panel(r.minX, r.minY, r.width, r.height, 28 * u, Style.CREAM, 4 * u)
        // big broom on its own line, then the question and what it means
        p.save(); p.translate(game.width / 2, r.minY + 58 * u); p.scale(1.7 * u, 1.7 * u); Icons.broom(p, 0.0, 0.0, 48.0); p.restore()
        p.textCentered("Sweep everything away?", game.width / 2, r.minY + 128 * u, 34 * u, Style.OUTLINE)
        p.textCentered("All the parts you placed go back in the tray.", game.width / 2, r.minY + 168 * u, 20 * u, Style.GREY_DARK)
        clearNoButton.visible = true; clearYesButton.visible = true
        clearNoButton.draw(p, u, game.clock)
        clearYesButton.draw(p, u, game.clock)
    }

    private fun drawWin(p: Painter) {
        val u = game.u
        val a = (celebrationTime / 0.4).coerceIn(0.0, 1.0)
        p.alpha = 0.55 * a
        p.fillRect(0.0, 0.0, game.width, game.height, Style.NAVY)
        p.alpha = 1.0
        confetti?.draw(p)
        val r = winPanel()
        val px = r.minX; val py = r.minY; val pw = r.width; val ph = r.height
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
