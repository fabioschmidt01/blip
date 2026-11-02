package dev.vereda.core

object Waveform {
    const val BARS = 36
    const val REST = 8

    fun level(amplitude: Int): Int {
        if (amplitude < 250) return REST
        val shaped = kotlin.math.sqrt(amplitude.coerceAtMost(32_767) / 32_767.0)
        return (shaped * 100).toInt().coerceIn(REST, 100)
    }

    fun encode(samples: List<Int>): String {
        val bars = peaks(samples, BARS)
        val max = bars.maxOrNull() ?: 0
        val shaped = if (max <= REST) {
            IntArray(BARS) { REST }
        } else {
            IntArray(BARS) { index ->
                (REST + bars[index] * (100 - REST) / max).coerceIn(REST, 100)
            }
        }
        return shaped.joinToString(",")
    }

    fun decode(text: String): IntArray? {
        if (text.length !in 15..420 || text.any { it != ',' && it !in '0'..'9' }) return null
        val parts = text.split(',')
        if (parts.size !in 8..64) return null
        val out = IntArray(parts.size)
        for (index in parts.indices) {
            val value = parts[index].toIntOrNull() ?: return null
            if (value !in 0..100) return null
            out[index] = value
        }
        return out
    }

    fun peaks(samples: List<Int>, bars: Int): IntArray {
        val out = IntArray(bars)
        if (samples.isEmpty() || bars <= 0) return out
        val count = samples.size
        for (index in 0 until bars) {
            val start = index * count / bars
            var end = (index + 1) * count / bars
            if (end <= start) end = (start + 1).coerceAtMost(count)
            var peak = 0
            for (cursor in start until end) {
                val sample = samples[cursor]
                if (sample > peak) peak = sample
            }
            out[index] = peak
        }
        return out
    }
}
