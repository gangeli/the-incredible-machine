package tim.core.sound

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedural sound effects: every cue the game raises is synthesised from sines and noise so the
 * APK ships no audio assets. Output is 16-bit mono PCM at [RATE] Hz.
 */
object Synth {
    const val RATE = 22050

    val names = listOf(
        "tap", "pick", "drop", "nope", "undo", "flip", "whoosh", "start", "win", "hint", "bounce", "thud", "pop",
        "boing", "boom", "click", "ding", "chime", "swish", "snip", "fizz", "puff", "squeak", "meow", "nibble",
        "creak", "punch", "bump", "fwoosh",
    )

    private class Buf(seconds: Double) {
        val n = (seconds * RATE).toInt()
        val data = DoubleArray(n)
        fun t(i: Int) = i.toDouble() / RATE
        /** Adds a tone with a linear frequency sweep and exponential decay. */
        fun tone(start: Double, dur: Double, f0: Double, f1: Double = f0, amp: Double = 0.5, decay: Double = 6.0, square: Boolean = false, attack: Double = 0.005) {
            val i0 = (start * RATE).toInt(); val i1 = min(n, ((start + dur) * RATE).toInt())
            var phase = 0.0
            for (i in i0 until i1) {
                val u = (i - i0).toDouble() / (i1 - i0)
                val f = f0 + (f1 - f0) * u
                phase += 2 * PI * f / RATE
                val env = min(1.0, (i - i0) / (attack * RATE)) * exp(-decay * u)
                val v = if (square) (if (sin(phase) >= 0) 1.0 else -1.0) * 0.5 else sin(phase)
                data[i] += v * amp * env
            }
        }
        /** Adds decaying noise, optionally low-passed for a softer sound. */
        fun noise(start: Double, dur: Double, amp: Double = 0.4, decay: Double = 5.0, smooth: Double = 0.0, attack: Double = 0.005, seed: Long = 12345) {
            val i0 = (start * RATE).toInt(); val i1 = min(n, ((start + dur) * RATE).toInt())
            var s = seed
            var last = 0.0
            for (i in i0 until i1) {
                s = s * 6364136223846793005L + 1442695040888963407L
                val r = ((s ushr 11).toDouble() / (1L shl 53).toDouble()) * 2 - 1
                last = last * smooth + r * (1 - smooth)
                val u = (i - i0).toDouble() / (i1 - i0)
                val env = min(1.0, (i - i0) / (attack * RATE)) * exp(-decay * u)
                data[i] += last * amp * env
            }
        }
        fun toPcm(): ShortArray {
            var peak = 1e-9
            for (v in data) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
            val g = 0.85 / peak // normalise every effect to the same loudness
            return ShortArray(n) { (data[it] * g * 32767).toInt().coerceIn(-32768, 32767).toShort() }
        }
    }

    fun sample(name: String): ShortArray? {
        val b: Buf = when (name) {
            "tap" -> Buf(0.06).apply { tone(0.0, 0.06, 1000.0, 800.0, 0.4, 8.0) }
            "pick" -> Buf(0.1).apply { tone(0.0, 0.1, 500.0, 900.0, 0.4, 4.0) }
            "drop" -> Buf(0.14).apply { tone(0.0, 0.12, 220.0, 150.0, 0.5, 7.0); noise(0.0, 0.05, 0.15, 12.0, 0.6) }
            "nope" -> Buf(0.3).apply { tone(0.0, 0.1, 220.0, 220.0, 0.35, 3.0, square = true); tone(0.15, 0.15, 170.0, 160.0, 0.35, 3.0, square = true) }
            "undo" -> Buf(0.12).apply { tone(0.0, 0.12, 900.0, 500.0, 0.4, 4.0) }
            "flip" -> Buf(0.08).apply { tone(0.0, 0.08, 1300.0, 600.0, 0.4, 5.0) }
            "whoosh" -> Buf(0.3).apply { noise(0.0, 0.3, 0.5, 3.0, 0.9, attack = 0.08) }
            "start" -> Buf(0.35).apply { tone(0.0, 0.1, 523.0, 523.0, 0.35, 4.0); tone(0.1, 0.1, 659.0, 659.0, 0.35, 4.0); tone(0.2, 0.15, 784.0, 784.0, 0.35, 4.0) }
            "win" -> Buf(1.1).apply {
                tone(0.0, 0.14, 523.0, 523.0, 0.3, 3.0); tone(0.14, 0.14, 659.0, 659.0, 0.3, 3.0); tone(0.28, 0.14, 784.0, 784.0, 0.3, 3.0)
                tone(0.42, 0.6, 1046.0, 1046.0, 0.35, 2.5); tone(0.42, 0.6, 1318.0, 1318.0, 0.2, 2.5); tone(0.42, 0.6, 1568.0, 1568.0, 0.15, 2.5)
            }
            "hint" -> Buf(0.3).apply { tone(0.0, 0.3, 1500.0, 1500.0, 0.25, 4.0); tone(0.08, 0.22, 2000.0, 2000.0, 0.2, 4.0) }
            "bounce" -> Buf(0.1).apply { tone(0.0, 0.1, 160.0, 90.0, 0.5, 6.0) }
            "thud" -> Buf(0.15).apply { tone(0.0, 0.13, 90.0, 50.0, 0.6, 6.0); noise(0.0, 0.06, 0.25, 10.0, 0.7) }
            "pop" -> Buf(0.09).apply { noise(0.0, 0.05, 0.6, 14.0, 0.2); tone(0.0, 0.08, 500.0, 200.0, 0.35, 10.0) }
            "boing" -> Buf(0.3).apply { tone(0.0, 0.3, 150.0, 650.0, 0.45, 4.0); tone(0.05, 0.25, 300.0, 700.0, 0.2, 5.0) }
            "boom" -> Buf(0.5).apply { noise(0.0, 0.5, 0.6, 4.0, 0.85); tone(0.0, 0.4, 60.0, 35.0, 0.5, 4.0) }
            "click" -> Buf(0.05).apply { noise(0.0, 0.01, 0.5, 20.0, 0.0); tone(0.0, 0.04, 800.0, 800.0, 0.3, 12.0) }
            "ding" -> Buf(0.6).apply { tone(0.0, 0.6, 1760.0, 1760.0, 0.35, 3.5); tone(0.0, 0.5, 2640.0, 2640.0, 0.15, 5.0); tone(0.0, 0.3, 880.0, 880.0, 0.1, 6.0) }
            "chime" -> Buf(0.5).apply { tone(0.0, 0.4, 1046.0, 1046.0, 0.3, 3.0); tone(0.08, 0.4, 1318.0, 1318.0, 0.3, 3.0); tone(0.16, 0.4, 1568.0, 1568.0, 0.3, 3.0) }
            "swish" -> Buf(0.2).apply { noise(0.0, 0.2, 0.45, 5.0, 0.7, attack = 0.03) }
            "snip" -> Buf(0.09).apply { noise(0.0, 0.02, 0.5, 15.0, 0.0); noise(0.05, 0.03, 0.5, 15.0, 0.0, seed = 99); tone(0.05, 0.04, 2500.0, 1800.0, 0.2, 8.0) }
            "fizz" -> Buf(0.35).apply { noise(0.0, 0.35, 0.4, 2.0, 0.3, attack = 0.02) }
            "puff" -> Buf(0.25).apply { noise(0.0, 0.25, 0.4, 4.0, 0.92, attack = 0.02) }
            "squeak" -> Buf(0.14).apply { tone(0.0, 0.14, 2000.0, 2600.0, 0.3, 4.0) }
            "meow" -> Buf(0.35).apply { tone(0.0, 0.35, 650.0, 380.0, 0.35, 3.0); tone(0.0, 0.3, 1300.0, 760.0, 0.12, 4.0) }
            "nibble" -> Buf(0.25).apply { for (k in 0 until 3) tone(k * 0.08, 0.05, 300.0, 250.0, 0.4, 10.0) }
            "creak" -> Buf(0.25).apply { tone(0.0, 0.25, 130.0, 90.0, 0.35, 3.0, square = true) }
            "punch" -> Buf(0.12).apply { noise(0.0, 0.08, 0.5, 10.0, 0.5); tone(0.0, 0.1, 120.0, 70.0, 0.5, 8.0) }
            "bump" -> Buf(0.08).apply { tone(0.0, 0.08, 240.0, 180.0, 0.5, 8.0) }
            "fwoosh" -> Buf(0.25).apply { noise(0.0, 0.25, 0.45, 5.0, 0.8, attack = 0.02) }
            else -> return null
        }
        return b.toPcm()
    }
}
