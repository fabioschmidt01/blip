package dev.vereda.core

object Hex {
    fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        val alphabet = "0123456789abcdef"
        for (i in bytes.indices) {
            val value = bytes[i].toInt() and 0xFF
            out[i * 2] = alphabet[value ushr 4]
            out[i * 2 + 1] = alphabet[value and 0x0F]
        }
        return String(out)
    }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex ímpar" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = digit(hex[i * 2])
            val lo = digit(hex[i * 2 + 1])
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun digit(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            in 'A'..'F' -> char - 'A' + 10
            else -> throw IllegalArgumentException("hex inválido")
        }
    }
}
