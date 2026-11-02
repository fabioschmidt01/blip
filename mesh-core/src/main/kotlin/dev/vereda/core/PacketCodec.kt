package dev.vereda.core

import java.io.ByteArrayOutputStream

object PacketCodec {
    private const val MAGIC_0 = 'V'.code
    private const val MAGIC_1 = 'R'.code
    private const val VERSION = 1
    private const val HEADER = 2 + 1 + 1 + 1 + 1 + 16 + 8 + 32 + 32 + 4
    private const val FLAG_RECIPIENT = 1
    private const val FLAG_ENCRYPTED = 2
    private const val FLAG_LINK_LOCAL = 4

    fun sign(identity: Identity, packet: Packet): Packet {
        val signature = identity.sign(signingBytes(packet))
        return Packet(
            packet.type,
            packet.ttl,
            packet.id,
            packet.timestamp,
            packet.sender,
            packet.recipient,
            packet.encrypted,
            packet.linkLocal,
            packet.payload,
            signature,
        )
    }

    fun verify(packet: Packet): Boolean {
        if (packet.signature.size != 64 || packet.sender.length != 64) return false
        val publicKey = try {
            Hex.decode(packet.sender)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return Identity.verify(publicKey, signingBytes(packet), packet.signature)
    }

    fun encode(packet: Packet): ByteArray {
        require(packet.signature.size == 64)
        require(packet.payload.size <= MeshLimits.MAX_PAYLOAD)
        val id = Hex.decode(packet.id)
        val sender = Hex.decode(packet.sender)
        require(id.size == 16 && sender.size == 32)
        val recipient = if (packet.recipient == null) {
            ByteArray(32)
        } else {
            Hex.decode(packet.recipient).also { require(it.size == 32) }
        }
        val out = ByteArrayOutputStream(HEADER + packet.payload.size + 64)
        out.write(MAGIC_0)
        out.write(MAGIC_1)
        out.write(VERSION)
        out.write(packet.type.code)
        out.write(packet.ttl)
        out.write(flags(packet))
        out.write(id)
        writeU64(out, packet.timestamp)
        out.write(sender)
        out.write(recipient)
        writeU32(out, packet.payload.size)
        out.write(packet.payload)
        out.write(packet.signature)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Packet? {
        if (bytes.size < HEADER + 64) return null
        if (bytes[0].toInt() != MAGIC_0 || bytes[1].toInt() != MAGIC_1) return null
        if ((bytes[2].toInt() and 0xFF) != VERSION) return null
        val type = PacketType.from(bytes[3].toInt() and 0xFF) ?: return null
        val ttl = bytes[4].toInt() and 0xFF
        val flags = bytes[5].toInt() and 0xFF
        var cursor = 6
        val id = Hex.encode(bytes.copyOfRange(cursor, cursor + 16))
        cursor += 16
        val timestamp = readU64(bytes, cursor)
        cursor += 8
        val sender = Hex.encode(bytes.copyOfRange(cursor, cursor + 32))
        cursor += 32
        val recipientBytes = bytes.copyOfRange(cursor, cursor + 32)
        cursor += 32
        val payloadLength = readU32(bytes, cursor)
        cursor += 4
        if (payloadLength < 0 || payloadLength > MeshLimits.MAX_PAYLOAD) return null
        if (cursor + payloadLength + 64 != bytes.size) return null
        val payload = bytes.copyOfRange(cursor, cursor + payloadLength)
        val signature = bytes.copyOfRange(cursor + payloadLength, bytes.size)
        val hasRecipient = flags and FLAG_RECIPIENT != 0
        if (hasRecipient && recipientBytes.all { it == 0.toByte() }) return null
        if (!hasRecipient && recipientBytes.any { it != 0.toByte() }) return null
        return Packet(
            type = type,
            ttl = ttl,
            id = id,
            timestamp = timestamp,
            sender = sender,
            recipient = if (hasRecipient) Hex.encode(recipientBytes) else null,
            encrypted = flags and FLAG_ENCRYPTED != 0,
            linkLocal = flags and FLAG_LINK_LOCAL != 0,
            payload = payload,
            signature = signature,
        )
    }

    fun signingBytes(packet: Packet): ByteArray {
        val id = Hex.decode(packet.id)
        val sender = Hex.decode(packet.sender)
        val recipient = if (packet.recipient == null) ByteArray(32) else Hex.decode(packet.recipient)
        val out = ByteArrayOutputStream(HEADER + packet.payload.size)
        out.write(VERSION)
        out.write(packet.type.code)
        out.write(flags(packet))
        out.write(id)
        writeU64(out, packet.timestamp)
        out.write(sender)
        out.write(recipient)
        writeU32(out, packet.payload.size)
        out.write(packet.payload)
        return out.toByteArray()
    }

    private fun flags(packet: Packet): Int {
        var flags = 0
        if (packet.recipient != null) flags = flags or FLAG_RECIPIENT
        if (packet.encrypted) flags = flags or FLAG_ENCRYPTED
        if (packet.linkLocal) flags = flags or FLAG_LINK_LOCAL
        return flags
    }

    private fun writeU64(out: ByteArrayOutputStream, value: Long) {
        for (shift in 56 downTo 0 step 8) {
            out.write(((value ushr shift) and 0xFF).toInt())
        }
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Int) {
        for (shift in 24 downTo 0 step 8) {
            out.write((value ushr shift) and 0xFF)
        }
    }

    private fun readU64(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int {
        var value = 0
        for (i in 0 until 4) {
            value = (value shl 8) or (bytes[offset + i].toInt() and 0xFF)
        }
        return value
    }
}

object Payloads {
    data class Announce(val nickname: String, val encPublic: ByteArray)

    fun announce(nickname: String, encPublic: ByteArray): ByteArray {
        val nick = nickname.encodeToByteArray()
        require(nick.size in 1..MeshLimits.MAX_NICK_BYTES)
        require(encPublic.size == 32)
        val out = ByteArray(1 + nick.size + 32)
        out[0] = nick.size.toByte()
        nick.copyInto(out, 1)
        encPublic.copyInto(out, 1 + nick.size)
        return out
    }

    fun readAnnounce(payload: ByteArray): Announce? {
        if (payload.isEmpty()) return null
        val size = payload[0].toInt() and 0xFF
        if (size == 0 || payload.size != 1 + size + 32) return null
        val nickname = payload.copyOfRange(1, 1 + size).toString(Charsets.UTF_8)
        if (nickname.isBlank()) return null
        return Announce(nickname, payload.copyOfRange(1 + size, payload.size))
    }

    fun ack(packetId: String): ByteArray {
        val bytes = Hex.decode(packetId)
        require(bytes.size == 16)
        return bytes
    }

    fun readAck(payload: ByteArray): String? {
        if (payload.size != 16) return null
        return Hex.encode(payload)
    }
}
