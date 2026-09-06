package tim.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import tim.core.sound.Synth
import tim.core.ui.SoundPlayer

/**
 * Mixes procedurally generated effects into one streaming AudioTrack on a background thread.
 * Samples are rendered lazily from [Synth] and cached.
 */
class SoundEngine : SoundPlayer {
    private class Voice(val data: ShortArray) { var pos = 0 }
    private val cache = HashMap<String, ShortArray?>()
    private val voices = ArrayList<Voice>()
    @Volatile private var running = false
    private var thread: Thread? = null

    override fun play(name: String) {
        val data = synchronized(cache) { cache.getOrPut(name) { Synth.sample(name) } } ?: return
        synchronized(voices) {
            if (voices.size >= 8) voices.removeAt(0)
            voices.add(Voice(data))
        }
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "tim-audio").apply { isDaemon = true; start() }
    }

    fun stop() { running = false; thread = null }

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(Synth.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Synth.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val frames = 512
        val buf = ShortArray(frames)
        val mix = IntArray(frames)
        try {
            track.play()
            while (running) {
                java.util.Arrays.fill(mix, 0)
                var active = false
                synchronized(voices) {
                    val it = voices.iterator()
                    while (it.hasNext()) {
                        val v = it.next()
                        active = true
                        var i = 0
                        while (i < frames && v.pos < v.data.size) { mix[i] += v.data[v.pos]; i++; v.pos++ }
                        if (v.pos >= v.data.size) it.remove()
                    }
                }
                if (!active) { for (i in 0 until frames) buf[i] = 0 } else for (i in 0 until frames) buf[i] = mix[i].coerceIn(-32768, 32767).toShort()
                track.write(buf, 0, frames)
            }
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }
}
