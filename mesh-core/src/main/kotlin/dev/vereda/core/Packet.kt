package dev.vereda.core

enum class PacketType(val code: Int) {
    ANNOUNCE(1),
    PUBLIC_TEXT(2),
    DIRECT(3),
    ACK(4),
    ROOM_META(5),
    ROOM_TEXT(6),
    MEDIA(7),
    PLACE_TEXT(8),
    ;

    companion object {
        fun from(code: Int): PacketType? = entries.firstOrNull { it.code == code }
    }
}

class Packet(
    val type: PacketType,
    val ttl: Int,
    val id: String,
    val timestamp: Long,
    val sender: String,
    val recipient: String?,
    val encrypted: Boolean,
    val linkLocal: Boolean,
    val payload: ByteArray,
    val signature: ByteArray,
) {
    fun withTtl(ttl: Int): Packet = Packet(
        type, ttl, id, timestamp, sender, recipient, encrypted, linkLocal, payload, signature,
    )
}

data class PeerSnapshot(
    val id: String,
    val nickname: String,
    val encPublicHex: String,
    val lastSeen: Long,
    val direct: Boolean,
)

sealed interface MeshEvent {
    data class PublicMessage(
        val id: String,
        val sender: String,
        val text: String,
        val at: Long,
    ) : MeshEvent

    data class DirectMessage(
        val id: String,
        val sender: String,
        val text: String,
        val at: Long,
    ) : MeshEvent

    data class Acked(val id: String) : MeshEvent

    data class GiveUp(val id: String) : MeshEvent

    data class RoomMeta(
        val roomId: String,
        val name: String,
        val hasPassword: Boolean,
        val from: String,
    ) : MeshEvent

    data class RoomPost(
        val id: String,
        val sender: String,
        val roomId: String,
        val encrypted: Boolean,
        val body: ByteArray,
        val at: Long,
    ) : MeshEvent

    data class MediaReady(
        val id: String,
        val sender: String,
        val conversation: String,
        val mime: String,
        val bytes: ByteArray,
        val at: Long,
    ) : MeshEvent

    data class PlacePost(
        val id: String,
        val sender: String,
        val geohash: String,
        val text: String,
        val at: Long,
    ) : MeshEvent
}
