package dev.vereda.core

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object MediaFrames {
    fun write(mediaId: ByteArray, conversation: String, mime: String, index: Int, count: Int, data: ByteArray): ByteArray {
        require(mediaId.size == 16)
        val conv = conversation.encodeToByteArray()
        val mimeBytes = mime.encodeToByteArray()
        require(conv.size in 1..80 && mimeBytes.size in 1..40 && index < count && count <= 64)
        val out = ByteArray(16 + 1 + conv.size + 1 + mimeBytes.size + 2 + 2 + data.size)
        var cursor = 0
        mediaId.copyInto(out, cursor)
        cursor += 16
        out[cursor++] = conv.size.toByte()
        conv.copyInto(out, cursor)
        cursor += conv.size
        out[cursor++] = mimeBytes.size.toByte()
        mimeBytes.copyInto(out, cursor)
        cursor += mimeBytes.size
        out[cursor++] = (index shr 8).toByte()
        out[cursor++] = index.toByte()
        out[cursor++] = (count shr 8).toByte()
        out[cursor++] = count.toByte()
        data.copyInto(out, cursor)
        return out
    }

    data class Part(
        val mediaId: String,
        val conversation: String,
        val mime: String,
        val index: Int,
        val count: Int,
        val data: ByteArray,
    )

    fun read(payload: ByteArray): Part? {
        if (payload.size < 22) return null
        var cursor = 0
        val mediaId = Hex.encode(payload.copyOfRange(0, 16))
        cursor = 16
        val convLen = payload[cursor].toInt() and 0xFF
        cursor += 1
        if (convLen == 0 || cursor + convLen >= payload.size) return null
        val conversation = payload.copyOfRange(cursor, cursor + convLen).toString(Charsets.UTF_8)
        cursor += convLen
        val mimeLen = payload[cursor].toInt() and 0xFF
        cursor += 1
        if (mimeLen == 0 || cursor + mimeLen + 4 > payload.size) return null
        val mime = payload.copyOfRange(cursor, cursor + mimeLen).toString(Charsets.UTF_8)
        cursor += mimeLen
        val index = ((payload[cursor].toInt() and 0xFF) shl 8) or (payload[cursor + 1].toInt() and 0xFF)
        val count = ((payload[cursor + 2].toInt() and 0xFF) shl 8) or (payload[cursor + 3].toInt() and 0xFF)
        cursor += 4
        if (count == 0 || index >= count || count > 64) return null
        return Part(mediaId, conversation, mime, index, count, payload.copyOfRange(cursor, payload.size))
    }
}

object RoomFrames {
    fun meta(roomId: String, name: String, hasPassword: Boolean): ByteArray {
        val id = roomId.encodeToByteArray()
        val title = name.encodeToByteArray()
        require(id.size in 1..40 && title.size in 1..MeshLimits.MAX_ROOM_NAME)
        val out = ByteArray(1 + id.size + 1 + title.size + 1)
        out[0] = id.size.toByte()
        id.copyInto(out, 1)
        out[1 + id.size] = title.size.toByte()
        title.copyInto(out, 2 + id.size)
        out[out.lastIndex] = if (hasPassword) 1 else 0
        return out
    }

    data class Meta(val roomId: String, val name: String, val hasPassword: Boolean)

    fun readMeta(payload: ByteArray): Meta? {
        if (payload.size < 4) return null
        val idLen = payload[0].toInt() and 0xFF
        if (idLen == 0 || payload.size < 2 + idLen) return null
        val id = payload.copyOfRange(1, 1 + idLen).toString(Charsets.UTF_8)
        val nameLen = payload[1 + idLen].toInt() and 0xFF
        if (nameLen == 0 || payload.size != 3 + idLen + nameLen) return null
        val name = payload.copyOfRange(2 + idLen, 2 + idLen + nameLen).toString(Charsets.UTF_8)
        val locked = payload.last().toInt() == 1
        return Meta(id, name, locked)
    }

    fun text(roomId: String, encrypted: Boolean, body: ByteArray): ByteArray {
        val id = roomId.encodeToByteArray()
        require(id.size in 1..40)
        val out = ByteArray(1 + id.size + 1 + body.size)
        out[0] = id.size.toByte()
        id.copyInto(out, 1)
        out[1 + id.size] = if (encrypted) 1 else 0
        body.copyInto(out, 2 + id.size)
        return out
    }

    data class Text(val roomId: String, val encrypted: Boolean, val body: ByteArray)

    fun readText(payload: ByteArray): Text? {
        if (payload.size < 3) return null
        val idLen = payload[0].toInt() and 0xFF
        if (idLen == 0 || payload.size < 2 + idLen) return null
        val id = payload.copyOfRange(1, 1 + idLen).toString(Charsets.UTF_8)
        val encrypted = payload[1 + idLen].toInt() == 1
        return Text(id, encrypted, payload.copyOfRange(2 + idLen, payload.size))
    }
}

object PlaceFrames {
    fun text(geohash: String, message: String): ByteArray {
        val hash = geohash.encodeToByteArray()
        val body = message.encodeToByteArray()
        require(hash.size in 2..12)
        val out = ByteArray(1 + hash.size + body.size)
        out[0] = hash.size.toByte()
        hash.copyInto(out, 1)
        body.copyInto(out, 1 + hash.size)
        return out
    }

    data class Text(val geohash: String, val message: String)

    fun read(payload: ByteArray): Text? {
        if (payload.isEmpty()) return null
        val len = payload[0].toInt() and 0xFF
        if (len !in 2..12 || payload.size <= 1 + len) return null
        val hash = payload.copyOfRange(1, 1 + len).toString(Charsets.UTF_8)
        val message = payload.copyOfRange(1 + len, payload.size).toString(Charsets.UTF_8)
        if (TextRules.message(message) == null) return null
        return Text(hash, message.trim())
    }
}

object RoomCipher {
    fun key(password: String, roomId: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), roomId.encodeToByteArray(), 50_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun seal(key: ByteArray, roomId: String, plain: ByteArray, nonceSeed: ByteArray): ByteArray {
        val nonce = nonceSeed.copyOf(12)
        val aad = roomId.encodeToByteArray()
        return crypt(Cipher.ENCRYPT_MODE, key, nonce, aad, plain)
    }

    fun open(key: ByteArray, roomId: String, sealed: ByteArray, nonceSeed: ByteArray): ByteArray? {
        if (nonceSeed.size < 12) return null
        return try {
            crypt(Cipher.DECRYPT_MODE, key, nonceSeed.copyOf(12), roomId.encodeToByteArray(), sealed)
        } catch (_: Exception) {
            null
        }
    }

    private fun crypt(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key, "ChaCha20")
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        try {
            cipher.init(mode, keySpec, IvParameterSpec(nonce))
        } catch (_: java.security.GeneralSecurityException) {
            cipher.init(mode, keySpec, GCMParameterSpec(128, nonce))
        }
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }

    fun nonceFromId(packetIdHex: String): ByteArray = Hex.decode(packetIdHex).copyOf(12)
}

object Geohash {
    private const val ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(latitude: Double, longitude: Double, precision: Int): String {
        require(precision in 1..12)
        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0
        var even = true
        var bit = 0
        var value = 0
        val out = StringBuilder()
        while (out.length < precision) {
            if (even) {
                val mid = (minLon + maxLon) / 2
                if (longitude >= mid) {
                    value = (value shl 1) or 1
                    minLon = mid
                } else {
                    value = value shl 1
                    maxLon = mid
                }
            } else {
                val mid = (minLat + maxLat) / 2
                if (latitude >= mid) {
                    value = (value shl 1) or 1
                    minLat = mid
                } else {
                    value = value shl 1
                    maxLat = mid
                }
            }
            even = !even
            bit += 1
            if (bit == 5) {
                out.append(ALPHABET[value])
                bit = 0
                value = 0
            }
        }
        return out.toString()
    }

    data class Box(
        val south: Double,
        val north: Double,
        val west: Double,
        val east: Double,
    ) {
        fun contains(latitude: Double, longitude: Double): Boolean {
            return latitude in south..north && longitude in west..east
        }
    }

    fun box(hash: String): Box? {
        if (hash.isEmpty() || hash.any { it !in ALPHABET }) return null
        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0
        var even = true
        for (char in hash) {
            val value = ALPHABET.indexOf(char)
            for (mask in intArrayOf(16, 8, 4, 2, 1)) {
                if (even) {
                    val mid = (minLon + maxLon) / 2
                    if (value and mask != 0) minLon = mid else maxLon = mid
                } else {
                    val mid = (minLat + maxLat) / 2
                    if (value and mask != 0) minLat = mid else maxLat = mid
                }
                even = !even
            }
        }
        return Box(minLat, maxLat, minLon, maxLon)
    }

    fun center(hash: String): Pair<Double, Double>? {
        val area = box(hash) ?: return null
        return (area.south + area.north) / 2 to (area.west + area.east) / 2
    }
}

fun conversationForPlace(geohash: String) = "place:$geohash"

fun conversationForRoom(roomId: String) = "room:$roomId"

fun randomId(random: SecureRandom = SecureRandom()): String {
    return Hex.encode(ByteArray(8).also { random.nextBytes(it) })
}
