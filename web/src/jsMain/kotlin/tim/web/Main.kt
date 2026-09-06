package tim.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import tim.core.physics.Vec2
import tim.core.ui.Game
import tim.core.ui.PlayScreen
import tim.core.ui.TouchAction
import tim.core.ui.TouchEvent

/**
 * Browser entry point: a full-window canvas, a requestAnimationFrame loop, pointer events mapped to
 * the game's single-finger touch model, and a service worker for offline / home-screen installs.
 */
fun main() {
    if (document.readyState.toString() == "loading") document.addEventListener("DOMContentLoaded", { start() }) else start()
}

private fun start() {
    val canvas = document.getElementById("game") as HTMLCanvasElement
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    val sound = WebSound()
    val game = Game(WebStorage(), sound)
    val painter = CanvasPainter(ctx, 1.0, 1.0)
    var dpr = 1.0

    fun fit() {
        dpr = (window.devicePixelRatio.takeIf { it > 0 } ?: 1.0).coerceIn(1.0, 3.0)
        val cssW = window.innerWidth; val cssH = window.innerHeight
        val w = (cssW * dpr).toInt().coerceAtLeast(1); val h = (cssH * dpr).toInt().coerceAtLeast(1)
        if (canvas.width != w || canvas.height != h) {
            canvas.width = w; canvas.height = h
            canvas.style.width = "${cssW}px"; canvas.style.height = "${cssH}px"
        }
        painter.width = w.toDouble(); painter.height = h.toDouble()
        game.resize(w, h)
    }
    fit()
    window.addEventListener("resize", { fit() })
    window.asDynamic().visualViewport?.addEventListener("resize", { fit() })

    // one finger at a time, like the tablet build
    var activePointer = -1
    fun send(action: TouchAction, e: Event) {
        val pe = e.asDynamic()
        val id = (pe.pointerId as? Int) ?: 0
        when (action) {
            TouchAction.DOWN -> { if (activePointer != -1) return; activePointer = id; sound.unlock() }
            else -> if (id != activePointer) return
        }
        val rect = canvas.getBoundingClientRect()
        val x = ((pe.clientX as Double) - rect.left) * dpr
        val y = ((pe.clientY as Double) - rect.top) * dpr
        game.touch(TouchEvent(action, x, y, 0))
        if (action == TouchAction.UP || action == TouchAction.CANCEL) activePointer = -1
        e.preventDefault()
    }
    canvas.addEventListener("pointerdown", { e -> try { canvas.asDynamic().setPointerCapture(e.asDynamic().pointerId) } catch (t: Throwable) {}; send(TouchAction.DOWN, e) })
    canvas.addEventListener("pointermove", { e -> send(TouchAction.MOVE, e) })
    canvas.addEventListener("pointerup", { e -> send(TouchAction.UP, e) })
    canvas.addEventListener("pointercancel", { e -> send(TouchAction.CANCEL, e) })
    canvas.addEventListener("contextmenu", { e -> e.preventDefault() })
    document.addEventListener("keydown", { e -> if (e.asDynamic().key == "Escape") { game.back(); e.preventDefault() } })

    var last = 0.0
    fun frame(now: Double) {
        val dt = if (last == 0.0) 1.0 / 60 else (now - last) / 1000.0
        last = now
        game.update(dt)
        game.render(painter)
        window.requestAnimationFrame { frame(it) }
    }
    window.requestAnimationFrame { frame(it) }

    // offline + install support
    val nav = window.navigator.asDynamic()
    if (nav.serviceWorker != null && nav.serviceWorker != undefined) {
        try { nav.serviceWorker.register("sw.js") } catch (t: Throwable) {}
    }
    document.getElementById("loading")?.let { it.parentNode?.removeChild(it) }
    window.asDynamic().timDebug = debugHooks(game)
}

/** A few read-only hooks so a browser test can find things on the canvas. */
private fun debugHooks(game: Game): dynamic {
    val hooks = js("({})")
    fun rect(r: tim.core.physics.AABB): dynamic { val o = js("({})"); o.x = r.minX; o.y = r.minY; o.w = r.width; o.h = r.height; o.cx = r.center.x; o.cy = r.center.y; return o }
    hooks.screen = { game.screen::class.simpleName ?: "?" }
    hooks.won = { (game.screen as? PlayScreen)?.won ?: false }
    hooks.running = { (game.screen as? PlayScreen)?.running ?: false }
    hooks.placed = { (game.screen as? PlayScreen)?.board?.playerParts?.size ?: -1 }
    hooks.playButton = { (game.screen as? PlayScreen)?.let { rect(it.playButtonRect()) } }
    hooks.trayTile = { i: Int -> (game.screen as? PlayScreen)?.visibleTiles()?.getOrNull(i)?.let { rect(it.rect) } }
    hooks.toScreen = { x: Double, y: Double -> (game.screen as? PlayScreen)?.let { val p = it.layout.toScreen(Vec2(x, y)); val o = js("({})"); o.x = p.x; o.y = p.y; o } }
    hooks.solution = { (game.screen as? PlayScreen)?.level?.solution?.map { val o = js("({})"); o.type = it.type.name; o.x = it.x; o.y = it.y; o.w = it.w; o.h = it.h; o }?.toTypedArray() }
    hooks.startLevel = { i: Int -> game.startLevel(i) }
    // physics cost: milliseconds per 60 Hz step of a busy level, averaged over [steps] steps
    hooks.bench = { steps: Int ->
        val lvl = tim.core.game.Levels.all.first { it.id == "l43" }
        val m = tim.core.game.Machine(lvl.solvedBoard(), gravity = lvl.gravity, airPressure = lvl.airPressure)
        val t0 = window.performance.now()
        repeat(steps) { m.step() }
        (window.performance.now() - t0) / steps
    }
    return hooks
}
