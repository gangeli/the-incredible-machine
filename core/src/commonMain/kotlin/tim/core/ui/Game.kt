package tim.core.ui

import tim.core.game.Levels
import tim.core.physics.Vec2
import tim.core.render.Painter

/** Persistent key/value storage for progress (SharedPreferences on Android, a map in tests). */
interface Storage {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class MemoryStorage : Storage {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
}

/** Sound sink; the Android app synthesises short effects for these names. */
fun interface SoundPlayer { fun play(name: String) }

enum class TouchAction { DOWN, MOVE, UP, CANCEL }
data class TouchEvent(val action: TouchAction, val x: Double, val y: Double, val pointer: Int = 0)

/** Progress record: which levels are solved and with how many stars. */
class Progress(private val storage: Storage) {
    fun stars(levelId: String): Int = storage.get("stars:$levelId")?.toIntOrNull() ?: 0
    fun setStars(levelId: String, stars: Int) { if (stars > stars(levelId)) storage.put("stars:$levelId", stars.toString()) }
    fun solved(levelId: String) = stars(levelId) > 0
    fun solvedCount(): Int = Levels.all.count { solved(it.id) }
    /** Index of the first unsolved level, or the last level if everything is done. */
    fun nextLevelIndex(): Int = Levels.all.indexOfFirst { !solved(it.id) }.let { if (it < 0) Levels.all.size - 1 else it }
    var soundOn: Boolean
        get() = storage.get("sound") != "off"
        set(v) = storage.put("sound", if (v) "on" else "off")
}

abstract class Screen(val game: Game) {
    open fun onEnter() {}
    open fun onExit() {}
    abstract fun update(dt: Double)
    abstract fun render(p: Painter)
    abstract fun touch(ev: TouchEvent)
    /** Handle the system back button; return true if consumed. */
    open fun back(): Boolean = false
}

/**
 * The whole game, independent of the platform: owns the current screen, the clock and the
 * layout. The platform layer feeds it frames, touch events and a painter.
 */
class Game(val storage: Storage, val sound: SoundPlayer = SoundPlayer {}) {
    val progress = Progress(storage)
    /**
     * Set by platforms that can put the game on a home screen (the browser build); while set, the
     * title screen shows an Install button that calls it. [installAttention] makes that button pulse.
     */
    var installAction: (() -> Unit)? = null
    var installAttention = false
    var width = 1280.0
        private set
    var height = 800.0
        private set
    /** Scale unit: 1.0 at 800 px tall. All UI sizes are multiples of this. */
    val u: Double get() = height / 800.0
    var clock = 0.0
        private set
    var screen: Screen = TitleScreen(this)
        private set
    private var pending: Screen? = null
    private var started = false

    fun resize(w: Int, h: Int) {
        if (width == w.toDouble() && height == h.toDouble()) return
        width = w.toDouble(); height = h.toDouble()
        if (started) screen.onEnter()
    }

    fun goTo(s: Screen) { pending = s }

    fun update(dt: Double) {
        val d = dt.coerceIn(0.0, 0.1)
        clock += d
        if (!started) { started = true; screen.onEnter() }
        pending?.let { screen.onExit(); screen = it; pending = null; it.onEnter() }
        screen.update(d)
    }

    fun render(p: Painter) { screen.render(p) }

    fun touch(ev: TouchEvent) { screen.touch(ev) }

    fun back(): Boolean = screen.back()

    fun play(name: String) { if (progress.soundOn) sound.play(name) }

    fun startLevel(index: Int) { goTo(PlayScreen(this, Levels.all[index.coerceIn(0, Levels.all.size - 1)], index)) }
    fun startFreeform() { goTo(PlayScreen(this, Levels.freeform(), -1)) }
    fun toTitle() { goTo(TitleScreen(this)) }
    fun toLevelSelect() { goTo(LevelSelectScreen(this)) }

    val center: Vec2 get() = Vec2(width / 2, height / 2)
}
