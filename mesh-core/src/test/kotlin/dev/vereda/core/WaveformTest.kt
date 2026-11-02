package dev.vereda.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaveformTest {
    @Test
    fun quietInputStaysOnTheRestLine() {
        assertEquals(Waveform.REST, Waveform.level(0))
        assertEquals(Waveform.REST, Waveform.level(200))
    }

    @Test
    fun loudInputReachesTheTop() {
        assertEquals(100, Waveform.level(32_767))
        assertEquals(50, Waveform.level(8_192))
    }

    @Test
    fun encodeKeepsTheShapeAndRoundTrips() {
        val encoded = Waveform.encode(listOf(10, 10, 40, 20))
        val decoded = Waveform.decode(encoded)
        assertNotNull(decoded)
        assertEquals(Waveform.BARS, decoded.size)
        assertEquals(100, decoded.maxOrNull())
        assertTrue(decoded.last() > decoded.first())
    }

    @Test
    fun peaksTakeTheLoudestSampleInEachBucket() {
        val bars = Waveform.peaks(listOf(1, 10, 2, 80), 2)
        assertEquals(10, bars[0])
        assertEquals(80, bars[1])
    }

    @Test
    fun proseIsNotAWave() {
        assertNull(Waveform.decode("Áudio"))
        assertNull(Waveform.decode("1,2,no"))
        assertNull(Waveform.decode(""))
    }
}
