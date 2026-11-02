package dev.vereda.core

object TextRules {
    fun nickname(raw: String): String? {
        val clean = raw.trim().replace(Regex("\\s+"), " ").replace("\n", "")
        if (clean.isEmpty()) return null
        if (clean.encodeToByteArray().size > MeshLimits.MAX_NICK_BYTES) return null
        return clean
    }

    fun message(raw: String): String? {
        val clean = raw.trim().replace("\u0000", "")
        if (clean.isEmpty()) return null
        if (clean.encodeToByteArray().size > MeshLimits.MAX_TEXT_BYTES) return null
        return clean
    }
}
