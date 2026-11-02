package dev.vereda.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.security.SecureRandom

enum class RadioPhase {
    OFF,
    STARTING,
    ON,
    FAILED,
}

data class RadioHealth(val phase: RadioPhase, val detail: String)

class MeshEngine(
    private val identity: Identity,
    private val transport: MeshTransport,
    private val seen: SeenStore,
    private val outbox: OutboxStore,
    private val peerStore: PeerStore,
    private val publicLog: PublicLog,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: SecureRandom = SecureRandom(),
    private val log: (String) -> Unit = {},
    private val flushIntervalMs: Long? = 20_000,
) {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val engineScope = CoroutineScope(scope.coroutineContext + job)
    private val mutex = Mutex()
    private val links = mutableSetOf<String>()
    private val linkOfPeer = mutableMapOf<String, String>()
    private val peerByLink = mutableMapOf<String, String>()
    private val directory = mutableMapOf<String, PeerSnapshot>()
    private val heardAnnounces = LinkedHashMap<String, ByteArray>()
    private val mediaIn = LinkedHashMap<String, Array<ByteArray?>>()

    private val _events = MutableSharedFlow<MeshEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    private val _peers = MutableStateFlow<List<PeerSnapshot>>(emptyList())
    val peers: StateFlow<List<PeerSnapshot>> = _peers.asStateFlow()

    private val _linkCount = MutableStateFlow(0)
    val linkCount: StateFlow<Int> = _linkCount.asStateFlow()

    private val _health = MutableStateFlow(RadioHealth(RadioPhase.STARTING, "Ligando o rádio"))
    val health: StateFlow<RadioHealth> = _health.asStateFlow()

    @Volatile
    private var nickname: String = "sem nome"

    private var started = false

    fun prepareNickname(name: String) {
        nickname = TextRules.nickname(name) ?: "sem nome"
    }

    fun start() {
        if (started) return
        started = true
        engineScope.launch {
            val stored = peerStore.all()
            mutex.withLock {
                stored.forEach { peer -> directory[peer.id] = peer.copy(direct = false) }
                publishLocked()
            }
            transport.events.collect { event ->
                try {
                    onTransport(event)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log("falha ao tratar quadro: ${error.message ?: error::class.simpleName}")
                }
            }
        }
        engineScope.launch {
            yield()
            transport.start()
        }
        val interval = flushIntervalMs
        if (interval != null) {
            engineScope.launch {
                while (isActive) {
                    delay(interval)
                    sendFloodAnnounce()
                    flushOutbox(countAttempt = true)
                    seen.prune(clock() - MeshLimits.SEEN_TTL_MS, MeshLimits.SEEN_MAX)
                }
            }
        }
    }

    suspend fun stop() {
        runCatching { transport.stop() }
        job.cancel()
    }

    suspend fun rename(name: String) {
        val clean = TextRules.nickname(name) ?: return
        nickname = clean
        if (!started) return
        val open = mutex.withLock { links.toList() }
        open.forEach { sendLinkAnnounce(it) }
        sendFloodAnnounce()
    }

    suspend fun sendPublic(text: String): SendResult {
        if (!started) return SendResult.Rejected(Reject.RadioOff)
        val clean = TextRules.message(text) ?: return rejectText(text)
        val packet = originate(
            type = PacketType.PUBLIC_TEXT,
            ttl = MeshLimits.PUBLIC_TTL,
            recipient = null,
            encrypted = false,
            linkLocal = false,
            payload = clean.encodeToByteArray(),
        )
        publicLog.add(packet.id, PacketCodec.encode(packet), packet.timestamp)
        dispatch(packet)
        log("falei no canal")
        return SendResult.Queued(packet.id, packet.timestamp)
    }

    suspend fun sendDirect(to: String, text: String): SendResult {
        if (!started) return SendResult.Rejected(Reject.RadioOff)
        val clean = TextRules.message(text) ?: return rejectText(text)
        val peer = mutex.withLock { directory[to] } ?: return SendResult.Rejected(Reject.UnknownPeer)
        if (peer.encPublicHex.length != 64) return SendResult.Rejected(Reject.NoKey)
        val sealed = SealedBox.seal(
            senderSignPublic = identity.signPublic(),
            recipientEncPublic = Hex.decode(peer.encPublicHex),
            plaintext = clean.encodeToByteArray(),
            random = random,
        )
        val packet = originate(
            type = PacketType.DIRECT,
            ttl = MeshLimits.DIRECT_TTL,
            recipient = to,
            encrypted = true,
            linkLocal = false,
            payload = sealed,
        )
        outbox.enqueue(packet.id, PacketCodec.encode(packet), packet.timestamp)
        flushOutbox(countAttempt = true)
        log("direta para ${peer.nickname}")
        return SendResult.Queued(packet.id, packet.timestamp)
    }

    suspend fun sendFlood(type: PacketType, payload: ByteArray, ttl: Int): SendResult {
        if (!started) return SendResult.Rejected(Reject.RadioOff)
        if (payload.size > MeshLimits.MAX_PAYLOAD) return SendResult.Rejected(Reject.TooLong)
        val packet = originate(type, ttl, null, false, false, payload)
        dispatch(packet)
        return SendResult.Queued(packet.id, packet.timestamp)
    }

    suspend fun sendMedia(conversation: String, mime: String, bytes: ByteArray, directTo: String?): SendResult {
        if (!started) return SendResult.Rejected(Reject.RadioOff)
        if (bytes.isEmpty() || bytes.size > MeshLimits.MAX_MEDIA_BYTES) return SendResult.Rejected(Reject.TooLong)
        val wire = if (directTo != null) sealDirect(directTo, mime, bytes) else Wire(mime, bytes, encrypted = false)
        if (wire is SendResult.Rejected) return wire
        val sealed = wire as Wire
        val mediaId = ByteArray(16).also { random.nextBytes(it) }
        val parts = sealed.bytes.toList().chunked(MeshLimits.MEDIA_CHUNK).map { it.toByteArray() }
        var last: SendResult = SendResult.Rejected(Reject.Empty)
        parts.forEachIndexed { index, part ->
            val payload = MediaFrames.write(mediaId, conversation, sealed.mime, index, parts.size, part)
            val packet = originate(PacketType.MEDIA, MeshLimits.MEDIA_TTL, directTo, sealed.encrypted, false, payload)
            if (directTo == null) dispatch(packet) else {
                outbox.enqueue(packet.id, PacketCodec.encode(packet), packet.timestamp)
                flushOutbox(countAttempt = false)
            }
            last = SendResult.Queued(Hex.encode(mediaId), packet.timestamp)
        }
        log(if (sealed.encrypted) "mídia cifrada para $conversation" else "mídia para $conversation")
        return last
    }

    private suspend fun sealDirect(peerId: String, mime: String, bytes: ByteArray): Any {
        val peer = mutex.withLock { directory[peerId] } ?: return SendResult.Rejected(Reject.UnknownPeer)
        if (peer.encPublicHex.length != 64) return SendResult.Rejected(Reject.NoKey)
        val sealed = SealedBox.seal(
            identity.signPublic(),
            Hex.decode(peer.encPublicHex),
            MediaSeal.pack(mime, bytes),
            random,
        )
        if (sealed.size > MeshLimits.MAX_MEDIA_BYTES) return SendResult.Rejected(Reject.TooLong)
        return Wire(MediaSeal.DIRECT, sealed, encrypted = true)
    }

    private class Wire(val mime: String, val bytes: ByteArray, val encrypted: Boolean)

    private fun rejectText(text: String): SendResult {
        val reason = if (text.trim().isEmpty()) Reject.Empty else Reject.TooLong
        return SendResult.Rejected(reason)
    }

    private suspend fun onTransport(event: TransportEvent) {
        when (event) {
            is TransportEvent.LinkUp -> onLinkUp(event.linkId, event.remoteName)
            is TransportEvent.LinkDown -> onLinkDown(event.linkId)
            is TransportEvent.Frame -> onFrame(event.linkId, event.bytes)
            is TransportEvent.Ready -> {
                _health.value = RadioHealth(RadioPhase.ON, event.message)
                log(event.message)
            }
            is TransportEvent.Failed -> {
                _health.value = RadioHealth(RadioPhase.FAILED, event.message)
                log(event.message)
            }
        }
    }

    private suspend fun onLinkUp(linkId: String, remoteName: String) {
        mutex.withLock {
            links += linkId
            publishLocked()
        }
        log("link com $remoteName")
        sendLinkAnnounce(linkId)
        sendFloodAnnounce()
        replayIdentities(linkId)
        replayPublic(linkId)
        flushOutbox(countAttempt = false)
    }

    private suspend fun onLinkDown(linkId: String) {
        mutex.withLock {
            links.remove(linkId)
            val peerId = peerByLink.remove(linkId)
            if (peerId != null) {
                linkOfPeer.remove(peerId)
                directory[peerId]?.let { directory[peerId] = it.copy(direct = false, lastSeen = clock()) }
            }
            publishLocked()
        }
        log("link caiu")
    }

    private suspend fun onFrame(fromLink: String, bytes: ByteArray) {
        val packet = try {
            PacketCodec.decode(bytes)
        } catch (_: RuntimeException) {
            null
        }
        if (packet == null) {
            log("quadro inválido")
            return
        }
        if (!PacketCodec.verify(packet)) {
            log("assinatura inválida")
            return
        }
        if (packet.ttl !in 1..MeshLimits.MAX_TTL) {
            log("ttl inválido")
            return
        }
        if (packet.sender == identity.peerId) return
        if (seen.contains(packet.id)) {
            if (packet.type == PacketType.DIRECT && packet.recipient == identity.peerId) {
                sendAck(packet)
            }
            return
        }
        seen.add(packet.id, clock())
        if (packet.linkLocal) {
            rememberAnnounce(packet, fromLink, linkLocal = true)
            return
        }
        when (packet.type) {
            PacketType.ANNOUNCE -> {
                mutex.withLock { rememberAnnounceBytesLocked(packet.sender, bytes) }
                rememberAnnounce(packet, fromLink, linkLocal = false)
            }
            PacketType.PUBLIC_TEXT -> deliverPublic(packet, bytes)
            PacketType.DIRECT -> deliverDirect(packet)
            PacketType.ACK -> deliverAck(packet)
            PacketType.ROOM_META -> deliverRoomMeta(packet)
            PacketType.ROOM_TEXT -> deliverRoomText(packet)
            PacketType.MEDIA -> deliverMedia(packet)
            PacketType.PLACE_TEXT -> deliverPlace(packet)
        }
        forward(packet, fromLink)
    }

    private suspend fun deliverPublic(packet: Packet, bytes: ByteArray) {
        val text = TextRules.message(packet.payload.toString(Charsets.UTF_8)) ?: return
        publicLog.add(packet.id, bytes, packet.timestamp)
        emit(MeshEvent.PublicMessage(packet.id, packet.sender, text, packet.timestamp))
        val who = mutex.withLock { directory[packet.sender]?.nickname ?: packet.sender.take(8) }
        log("texto de $who")
    }

    private suspend fun deliverDirect(packet: Packet) {
        if (packet.recipient != identity.peerId) return
        val senderKey = try {
            Hex.decode(packet.sender)
        } catch (_: IllegalArgumentException) {
            return
        }
        val text = identity.open(senderKey, packet.payload)?.let { TextRules.message(it.toString(Charsets.UTF_8)) }
        if (text == null) {
            log("direta ilegível")
            return
        }
        emit(MeshEvent.DirectMessage(packet.id, packet.sender, text, packet.timestamp))
        sendAck(packet)
        val who = mutex.withLock { directory[packet.sender]?.nickname ?: packet.sender.take(8) }
        log("direta de $who")
    }

    private fun deliverRoomMeta(packet: Packet) {
        val meta = RoomFrames.readMeta(packet.payload) ?: return
        emit(MeshEvent.RoomMeta(meta.roomId, meta.name, meta.hasPassword, packet.sender))
    }

    private fun deliverRoomText(packet: Packet) {
        val text = RoomFrames.readText(packet.payload) ?: return
        emit(MeshEvent.RoomPost(packet.id, packet.sender, text.roomId, text.encrypted, text.body, packet.timestamp))
    }

    private fun deliverPlace(packet: Packet) {
        val text = PlaceFrames.read(packet.payload) ?: return
        emit(MeshEvent.PlacePost(packet.id, packet.sender, text.geohash, text.message, packet.timestamp))
    }

    private fun deliverMedia(packet: Packet) {
        if (packet.recipient != null && packet.recipient != identity.peerId) return
        val part = MediaFrames.read(packet.payload) ?: return
        val key = packet.sender + ":" + part.mediaId
        val slots = mediaIn.getOrPut(key) { arrayOfNulls(part.count) }
        if (slots.size != part.count || part.index !in slots.indices) return
        slots[part.index] = part.data
        if (slots.any { it == null }) return
        val all = slots.fold(ByteArray(0)) { acc, chunk -> acc + chunk!! }
        mediaIn.remove(key)
        val opened = if (part.mime == MediaSeal.DIRECT) openDirect(packet.sender, all) else (part.mime to all)
        if (opened == null) {
            log("mídia ilegível")
            return
        }
        emit(
            MeshEvent.MediaReady(
                id = part.mediaId,
                sender = packet.sender,
                conversation = part.conversation,
                mime = opened.first,
                bytes = opened.second,
                at = packet.timestamp,
            ),
        )
        log("mídia de ${packet.sender.take(8)}")
    }

    private fun openDirect(sender: String, sealed: ByteArray): Pair<String, ByteArray>? {
        val senderKey = try {
            Hex.decode(sender)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val plain = identity.open(senderKey, sealed) ?: return null
        return MediaSeal.unpack(plain)
    }

    private suspend fun deliverAck(packet: Packet) {
        if (packet.recipient != identity.peerId) return
        val id = Payloads.readAck(packet.payload) ?: return
        outbox.remove(id)
        emit(MeshEvent.Acked(id))
        log("entrega confirmada")
    }

    private suspend fun rememberAnnounce(packet: Packet, fromLink: String?, linkLocal: Boolean) {
        if (packet.type != PacketType.ANNOUNCE) return
        val announce = Payloads.readAnnounce(packet.payload) ?: run {
            log("anúncio inválido")
            return
        }
        val snapshot = mutex.withLock {
            if (linkLocal && fromLink != null) {
                linkOfPeer[packet.sender] = fromLink
                peerByLink[fromLink] = packet.sender
            }
            val direct = linkOfPeer[packet.sender] != null
            val peer = PeerSnapshot(
                id = packet.sender,
                nickname = announce.nickname,
                encPublicHex = Hex.encode(announce.encPublic),
                lastSeen = clock(),
                direct = direct,
            )
            directory[packet.sender] = peer
            publishLocked()
            peer
        }
        peerStore.upsert(snapshot.copy(direct = false))
        log("anúncio de ${snapshot.nickname}")
    }

    private fun rememberAnnounceBytesLocked(sender: String, bytes: ByteArray) {
        heardAnnounces.remove(sender)
        heardAnnounces[sender] = bytes.copyOf()
        while (heardAnnounces.size > 100) {
            val eldest = heardAnnounces.entries.firstOrNull()?.key ?: break
            heardAnnounces.remove(eldest)
        }
    }

    private suspend fun forward(packet: Packet, fromLink: String) {
        val next = packet.withTtl(packet.ttl - 1)
        val targets = mutex.withLock { currentTargets(packet, fromLink) }
        if (targets.isEmpty()) return
        val encoded = PacketCodec.encode(next)
        targets.forEach { transport.send(it, encoded) }
    }

    private suspend fun dispatch(packet: Packet) {
        val targets = mutex.withLock { currentTargets(packet, fromLink = null) }
        if (targets.isEmpty()) return
        val encoded = PacketCodec.encode(packet)
        targets.forEach { transport.send(it, encoded) }
    }

    private fun currentTargets(packet: Packet, fromLink: String?): List<String> {
        return Router.targets(
            packet = packet,
            fromLink = fromLink,
            self = identity.peerId,
            links = links.toSet(),
            directLinkOf = { peerId -> linkOfPeer[peerId] },
        )
    }

    private suspend fun sendLinkAnnounce(linkId: String) {
        val packet = announcePacket(linkLocal = true, ttl = 1)
        transport.send(linkId, PacketCodec.encode(packet))
    }

    private suspend fun sendFloodAnnounce() {
        if (mutex.withLock { links.isEmpty() }) return
        dispatch(announcePacket(linkLocal = false, ttl = MeshLimits.ANNOUNCE_TTL))
    }

    private suspend fun announcePacket(linkLocal: Boolean, ttl: Int): Packet {
        return originate(
            type = PacketType.ANNOUNCE,
            ttl = ttl,
            recipient = null,
            encrypted = false,
            linkLocal = linkLocal,
            payload = Payloads.announce(nickname, identity.encPublic()),
        )
    }

    private suspend fun replayIdentities(linkId: String) {
        val cached = mutex.withLock { heardAnnounces.values.map { it.copyOf() } }
        for (encoded in cached) {
            val packet = PacketCodec.decode(encoded) ?: continue
            if (packet.sender == identity.peerId) continue
            transport.send(linkId, PacketCodec.encode(packet.withTtl(1)))
        }
    }

    private suspend fun replayPublic(linkId: String) {
        for (encoded in publicLog.recent(MeshLimits.PUBLIC_REPLAY)) {
            val packet = PacketCodec.decode(encoded) ?: continue
            transport.send(linkId, PacketCodec.encode(packet.withTtl(1)))
        }
    }

    private suspend fun flushOutbox(countAttempt: Boolean) {
        for (item in outbox.list()) {
            if (item.attempts >= MeshLimits.MAX_ATTEMPTS) {
                outbox.remove(item.id)
                emit(MeshEvent.GiveUp(item.id))
                log("desisti de uma direta")
                continue
            }
            val packet = PacketCodec.decode(item.encoded) ?: continue
            val targets = mutex.withLock { currentTargets(packet, fromLink = null) }
            if (targets.isEmpty()) continue
            targets.forEach { transport.send(it, item.encoded) }
            if (countAttempt) outbox.bump(item.id)
        }
    }

    private suspend fun sendAck(original: Packet) {
        val ack = originate(
            type = PacketType.ACK,
            ttl = MeshLimits.DIRECT_TTL,
            recipient = original.sender,
            encrypted = false,
            linkLocal = false,
            payload = Payloads.ack(original.id),
        )
        dispatch(ack)
    }

    private suspend fun originate(
        type: PacketType,
        ttl: Int,
        recipient: String?,
        encrypted: Boolean,
        linkLocal: Boolean,
        payload: ByteArray,
    ): Packet {
        val unsigned = Packet(
            type = type,
            ttl = ttl,
            id = Hex.encode(ByteArray(16).also { random.nextBytes(it) }),
            timestamp = clock(),
            sender = identity.peerId,
            recipient = recipient,
            encrypted = encrypted,
            linkLocal = linkLocal,
            payload = payload,
            signature = ByteArray(0),
        )
        val signed = PacketCodec.sign(identity, unsigned)
        seen.add(signed.id, clock())
        return signed
    }

    private fun publishLocked() {
        _peers.value = directory.values.sortedBy { it.nickname.lowercase() }
        _linkCount.value = links.size
    }

    private fun emit(event: MeshEvent) {
        _events.tryEmit(event)
    }
}
