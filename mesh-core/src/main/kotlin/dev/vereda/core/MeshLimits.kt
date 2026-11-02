package dev.vereda.core

object MeshLimits {
    const val PUBLIC_TTL = 5
    const val DIRECT_TTL = 5
    const val ANNOUNCE_TTL = 4
    const val MAX_TTL = 8
    const val MAX_NICK_BYTES = 32
    const val MAX_TEXT_BYTES = 800
    const val MAX_PAYLOAD = 24_000
    const val FANOUT = 3
    const val MAX_ATTEMPTS = 8
    const val SEEN_TTL_MS = 15 * 60 * 1000L
    const val SEEN_MAX = 2000
    const val PUBLIC_MEMORY = 50
    const val PUBLIC_REPLAY = 20
    const val CONVERSATION_PUBLIC = "public"
    const val MEDIA_TTL = 3
    const val MEDIA_CHUNK = 8_000
    const val MAX_MEDIA_BYTES = 120_000
    const val MAX_ROOM_NAME = 32
}

enum class Reject {
    Empty,
    TooLong,
    RadioOff,
    UnknownPeer,
    NoKey,
}

sealed interface SendResult {
    data class Queued(val id: String, val timestamp: Long) : SendResult
    data class Rejected(val reason: Reject) : SendResult
}
