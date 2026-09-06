package tim.web

import kotlinx.browser.localStorage
import kotlinx.browser.window
import tim.core.sound.Synth
import tim.core.ui.SoundPlayer
import tim.core.ui.Storage

/** Progress lives in localStorage, so it survives reloads and works offline. */
class WebStorage : Storage {
    override fun get(key: String): String? = try { localStorage.getItem("tim:$key") } catch (e: Throwable) { null }
    override fun put(key: String, value: String) { try { localStorage.setItem("tim:$key", value) } catch (e: Throwable) {} }
}

/**
 * Plays the procedurally generated effects through WebAudio. Browsers only allow audio after a user
 * gesture, so the context is created (and resumed) from the first touch.
 */
class WebSound : SoundPlayer {
    private var ctx: dynamic = null
    private val buffers = HashMap<String, dynamic>()
    private val pending = ArrayList<String>()

    /** Call from a user gesture (pointer down); safe to call repeatedly. */
    fun unlock() {
        if (ctx == null) {
            val ac = js("window.AudioContext || window.webkitAudioContext")
            if (ac == null || ac == undefined) return
            ctx = js("new (window.AudioContext || window.webkitAudioContext)()")
        }
        val c = ctx
        if (c.state == "suspended") c.resume()
        for (name in pending) play(name)
        pending.clear()
    }

    override fun play(name: String) {
        val c = ctx
        if (c == null) { if (pending.size < 4) pending.add(name); return }
        if (c.state != "running") return
        val buf = buffers.getOrPut(name) {
            val pcm = Synth.sample(name) ?: return
            val b = c.createBuffer(1, pcm.size, Synth.RATE)
            val ch = b.getChannelData(0)
            for (i in pcm.indices) ch[i] = pcm[i] / 32768.0
            b
        } ?: return
        val src = c.createBufferSource()
        src.buffer = buf
        src.connect(c.destination)
        src.start()
    }
}
