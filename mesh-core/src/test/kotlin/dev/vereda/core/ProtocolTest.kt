package dev.vereda.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolTest {
    @Test
    fun identityRoundTripAndFingerprint() {
        val identity = Identity.generate()
        val restored = Identity.decode(identity.encode())
        assertEquals(identity.peerId, restored.peerId)
        assertEquals(identity.fingerprint(), restored.fingerprint())
        val message = "vereda".encodeToByteArray()
        val signature = identity.sign(message)
        assertTrue(Identity.verify(restored.signPublic(), message, signature))
        signature[0] = (signature[0].toInt() xor 1).toByte()
        assertFalse(Identity.verify(restored.signPublic(), message, signature))
    }

    @Test
    fun sealedBoxBindsTheSender() {
        val sender = Identity.generate()
        val recipient = Identity.generate()
        val other = Identity.generate()
        val plain = "segredo da trilha".encodeToByteArray()
        val sealed = SealedBox.seal(sender.signPublic(), recipient.encPublic(), plain, java.security.SecureRandom())
        assertEquals(
            plain.toString(Charsets.UTF_8),
            recipient.open(sender.signPublic(), sealed)?.toString(Charsets.UTF_8),
        )
        assertNull(other.open(sender.signPublic(), sealed))
        assertNull(recipient.open(other.signPublic(), sealed))
        val tampered = sealed.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 1).toByte()
        assertNull(recipient.open(sender.signPublic(), tampered))
    }

    @Test
    fun signatureSurvivesTtlChangeOnly() {
        val identity = Identity.generate()
        val packet = signed(identity, payload = "olá".encodeToByteArray())
        val encoded = PacketCodec.encode(packet)
        val decoded = PacketCodec.decode(encoded)
        assertNotNull(decoded)
        assertTrue(PacketCodec.verify(decoded))
        val relayed = decoded.withTtl(decoded.ttl - 1)
        assertTrue(PacketCodec.verify(relayed))
        val broken = Packet(
            relayed.type,
            relayed.ttl,
            relayed.id,
            relayed.timestamp,
            relayed.sender,
            relayed.recipient,
            relayed.encrypted,
            relayed.linkLocal,
            "não".encodeToByteArray(),
            relayed.signature,
        )
        assertFalse(PacketCodec.verify(broken))
        assertEquals(encoded.toList(), PacketCodec.encode(decoded).toList())
    }

    @Test
    fun garbageIsRejected() {
        assertNull(PacketCodec.decode(ByteArray(4)))
        assertNull(PacketCodec.decode(ByteArray(120)))
    }

    @Test
    fun routerFloodsPublicAndPinsDirect() {
        val publicPacket = sample(recipient = null, ttl = 5)
        assertEquals(
            setOf("b", "c"),
            Router.targets(publicPacket, "a", "me", setOf("a", "b", "c")) { null }.toSet(),
        )
        val direct = sample(recipient = "bob", ttl = 5)
        assertEquals(
            listOf("c"),
            Router.targets(direct, "a", "me", setOf("a", "b", "c")) { if (it == "bob") "c" else null },
        )
        val mine = sample(recipient = "me", ttl = 5)
        assertTrue(Router.targets(mine, "a", "me", setOf("a", "b")) { null }.isEmpty())
        val unknown = sample(recipient = "ghost", ttl = 4)
        assertEquals(
            listOf("a", "b", "c"),
            Router.targets(unknown, null, "me", setOf("e", "d", "c", "b", "a")) { null },
        )
        assertTrue(Router.targets(sample(null, 1), null, "me", setOf("a")) { null }.isEmpty())
    }

    @Test
    fun namesAndTextHaveASizeLimit() {
        assertEquals("Ana Maria", TextRules.nickname("  Ana   Maria \n"))
        assertNull(TextRules.nickname(""))
        assertNull(TextRules.nickname("a".repeat(40)))
        assertNull(TextRules.message("   "))
        assertNull(TextRules.message("a".repeat(801)))
    }

    private fun signed(identity: Identity, payload: ByteArray): Packet {
        val unsigned = Packet(
            type = PacketType.PUBLIC_TEXT,
            ttl = MeshLimits.PUBLIC_TTL,
            id = Hex.encode(ByteArray(16) { 7 }),
            timestamp = 10L,
            sender = identity.peerId,
            recipient = null,
            encrypted = false,
            linkLocal = false,
            payload = payload,
            signature = ByteArray(0),
        )
        return PacketCodec.sign(identity, unsigned)
    }

    private fun sample(recipient: String?, ttl: Int): Packet {
        return Packet(
            type = if (recipient == null) PacketType.PUBLIC_TEXT else PacketType.DIRECT,
            ttl = ttl,
            id = "ab".repeat(16),
            timestamp = 1L,
            sender = "cd".repeat(32),
            recipient = recipient,
            encrypted = recipient != null,
            linkLocal = false,
            payload = ByteArray(1),
            signature = ByteArray(64),
        )
    }
}
