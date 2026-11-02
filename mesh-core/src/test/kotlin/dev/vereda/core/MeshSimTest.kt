package dev.vereda.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeshSimTest {
    @Test
    fun publicTextCrossesTwoHopsOnce() = runTest {
        val mesh = SimMesh(backgroundScope)
        val ana = mesh.add("Ana")
        val bia = mesh.add("Bia")
        val caio = mesh.add("Caio")
        val heard = mutableListOf<MeshEvent>()
        backgroundScope.launch { caio.engine.events.collect { heard += it } }
        runCurrent()
        mesh.connect("Ana", "Bia")
        runCurrent()
        mesh.connect("Bia", "Caio")
        runCurrent()

        val sent = ana.engine.sendPublic("olá da trilha")
        assertIs<SendResult.Queued>(sent)
        runCurrent()

        val texts = heard.filterIsInstance<MeshEvent.PublicMessage>().filter { it.text == "olá da trilha" }
        assertEquals(1, texts.size)
        assertEquals(ana.identity.peerId, texts.single().sender)
        assertTrue(caio.engine.peers.value.any { it.nickname == "Ana" && !it.direct })
        assertTrue(bia.engine.linkCount.value == 2)
    }

    @Test
    fun directMessageStaysSealedUntilTheRecipient() = runTest {
        val mesh = SimMesh(backgroundScope)
        val ana = mesh.add("Ana")
        val bia = mesh.add("Bia")
        val caio = mesh.add("Caio")
        val atCaio = mutableListOf<MeshEvent>()
        val atBia = mutableListOf<MeshEvent>()
        val atAna = mutableListOf<MeshEvent>()
        backgroundScope.launch { caio.engine.events.collect { atCaio += it } }
        backgroundScope.launch { bia.engine.events.collect { atBia += it } }
        backgroundScope.launch { ana.engine.events.collect { atAna += it } }
        runCurrent()
        mesh.connect("Ana", "Bia")
        runCurrent()
        mesh.connect("Bia", "Caio")
        runCurrent()

        val secret = "segredo da trilha"
        val sent = ana.engine.sendDirect(caio.identity.peerId, secret)
        assertIs<SendResult.Queued>(sent)
        runCurrent()

        val delivered = atCaio.filterIsInstance<MeshEvent.DirectMessage>()
        assertEquals(listOf(secret), delivered.map { it.text })
        assertTrue(atBia.none { it is MeshEvent.DirectMessage })
        assertTrue(atAna.any { it is MeshEvent.Acked && it.id == sent.id })

        val directs = ana.transport.wire.mapNotNull { PacketCodec.decode(it.second) }
            .filter { it.type == PacketType.DIRECT }
        assertTrue(directs.isNotEmpty())
        directs.forEach { packet ->
            assertTrue(packet.encrypted)
            assertTrue(PacketCodec.verify(packet))
            assertNull(bia.identity.open(Hex.decode(packet.sender), packet.payload))
            assertEquals(secret, caio.identity.open(Hex.decode(packet.sender), packet.payload)?.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun directMediaStaysSealedUntilTheRecipient() = runTest {
        val mesh = SimMesh(backgroundScope)
        val ana = mesh.add("Ana")
        val bia = mesh.add("Bia")
        val caio = mesh.add("Caio")
        val atCaio = mutableListOf<MeshEvent>()
        val atBia = mutableListOf<MeshEvent>()
        backgroundScope.launch { caio.engine.events.collect { atCaio += it } }
        backgroundScope.launch { bia.engine.events.collect { atBia += it } }
        runCurrent()
        mesh.connect("Ana", "Bia")
        runCurrent()
        mesh.connect("Bia", "Caio")
        runCurrent()

        val secret = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)
        val sent = ana.engine.sendMedia(caio.identity.peerId, "audio/mp4", secret, caio.identity.peerId)
        assertIs<SendResult.Queued>(sent)
        runCurrent()

        val ready = atCaio.filterIsInstance<MeshEvent.MediaReady>()
        assertEquals(1, ready.size)
        assertEquals("audio/mp4", ready.single().mime)
        assertEquals(secret.toList(), ready.single().bytes.toList())
        assertTrue(atBia.none { it is MeshEvent.MediaReady })
        val packets = bia.transport.wire.mapNotNull { PacketCodec.decode(it.second) }
            .filter { it.type == PacketType.MEDIA }
        assertTrue(packets.isNotEmpty())
        packets.forEach { packet ->
            assertTrue(packet.encrypted)
            assertEquals(caio.identity.peerId, packet.recipient)
            assertTrue(secret.toList() !in packet.payload.toList().windowed(secret.size))
        }
    }

    @Test
    fun badSignatureIsNotRelayed() = runTest {
        val mesh = SimMesh(backgroundScope)
        mesh.add("Ana")
        val bia = mesh.add("Bia")
        val caio = mesh.add("Caio")
        val heard = mutableListOf<MeshEvent>()
        backgroundScope.launch { caio.engine.events.collect { heard += it } }
        runCurrent()
        mesh.connect("Ana", "Bia")
        runCurrent()
        mesh.connect("Bia", "Caio")
        runCurrent()
        val before = heard.size

        val identity = Identity.generate()
        val packet = PacketCodec.sign(
            identity,
            Packet(
                type = PacketType.PUBLIC_TEXT,
                ttl = 5,
                id = Hex.encode(ByteArray(16) { 3 }),
                timestamp = 1L,
                sender = identity.peerId,
                recipient = null,
                encrypted = false,
                linkLocal = false,
                payload = "intruso".encodeToByteArray(),
                signature = ByteArray(0),
            ),
        )
        val bytes = PacketCodec.encode(packet)
        bytes[bytes.lastIndex] = (bytes[bytes.lastIndex].toInt() xor 1).toByte()
        bia.transport.receive("Ana", bytes)
        runCurrent()
        assertTrue(heard.drop(before).none { it is MeshEvent.PublicMessage && it.text == "intruso" })
    }
}

private class SimMesh(private val scope: CoroutineScope) {
    private val nodes = mutableMapOf<String, Node>()

    fun add(name: String): Node {
        val identity = Identity.generate()
        val transport = SimTransport(name, scope, this)
        val engine = MeshEngine(
            identity = identity,
            transport = transport,
            seen = MemorySeen(),
            outbox = MemoryOutbox(),
            peerStore = MemoryPeers(),
            publicLog = MemoryPublicLog(),
            scope = scope,
            log = {},
            flushIntervalMs = null,
        )
        engine.prepareNickname(name)
        val node = Node(name, identity, engine, transport)
        nodes[name] = node
        engine.start()
        return node
    }

    suspend fun connect(left: String, right: String) {
        nodes.getValue(left).transport.up(right)
        nodes.getValue(right).transport.up(left)
    }

    fun node(name: String): Node = nodes.getValue(name)

    class Node(
        val name: String,
        val identity: Identity,
        val engine: MeshEngine,
        val transport: SimTransport,
    )
}

private class SimTransport(
    val name: String,
    private val scope: CoroutineScope,
    private val mesh: SimMesh,
) : MeshTransport {
    private val _events = MutableSharedFlow<TransportEvent>(replay = 8, extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()
    val wire = mutableListOf<Pair<String, ByteArray>>()

    override suspend fun start() = Unit

    override suspend fun stop() = Unit

    override suspend fun send(linkId: String, bytes: ByteArray) {
        val copy = bytes.copyOf()
        wire += linkId to copy
        scope.launch { mesh.node(linkId).transport.receive(name, copy) }
    }

    suspend fun up(other: String) {
        _events.emit(TransportEvent.LinkUp(other, other))
    }

    suspend fun receive(from: String, bytes: ByteArray) {
        _events.emit(TransportEvent.Frame(from, bytes.copyOf()))
    }
}
