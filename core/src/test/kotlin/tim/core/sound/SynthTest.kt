package tim.core.sound

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SynthTest {
    @Test
    fun `every named sound renders as short, audible, clipped-safe PCM`() {
        for (name in Synth.names) {
            val pcm = Synth.sample(name)
            assertNotNull(pcm, "no sample for $name")
            pcm!!
            val seconds = pcm.size.toDouble() / Synth.RATE
            assertTrue(seconds in 0.03..1.5, "$name lasts $seconds s")
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue(peak in 8000..32767, "$name peak $peak")
            assertTrue(abs(pcm[0].toInt()) < 3000, "$name should start near silence to avoid clicks (${pcm[0]})")
            assertTrue(abs(pcm.last().toInt()) < 6000, "$name should end near silence (${pcm.last()})")
        }
    }

    @Test
    fun `unknown names produce nothing`() { assertNull(Synth.sample("no-such-sound")) }
}
