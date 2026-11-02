package dev.vereda.app.mesh

import android.content.Context
import android.util.Log
import dev.vereda.app.data.MessageEntity
import dev.vereda.app.data.Profile
import dev.vereda.app.data.VeredaDatabase
import dev.vereda.app.data.RoomEntity
import dev.vereda.core.Identity
import dev.vereda.core.MeshEngine
import dev.vereda.core.MeshEvent
import dev.vereda.core.MeshLimits
import dev.vereda.core.PacketType
import dev.vereda.core.PeerSnapshot
import dev.vereda.core.PlaceFrames
import dev.vereda.core.RadioPhase
import dev.vereda.core.Reject
import dev.vereda.core.RoomCipher
import dev.vereda.core.RoomFrames
import dev.vereda.core.SendResult
import dev.vereda.core.TextRules
import dev.vereda.core.Waveform
import dev.vereda.core.conversationForPlace
import dev.vereda.core.conversationForRoom
import dev.vereda.core.randomId
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MeshRepository(
    context: Context,
    private val identity: Identity,
    private val profile: Profile,
    private val db: VeredaDatabase,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val lines = ArrayDeque<String>()
    private var engine: MeshEngine? = null
    private var nearby: NearbyMeshTransport? = null
    private var generation = 0

    private val _peers = MutableStateFlow<List<PeerSnapshot>>(emptyList())
    val peers: StateFlow<List<PeerSnapshot>> = _peers.asStateFlow()
    private val _linkCount = MutableStateFlow(0)
    val linkCount: StateFlow<Int> = _linkCount.asStateFlow()
    private val _phase = MutableStateFlow(RadioPhase.OFF)
    val phase: StateFlow<RadioPhase> = _phase.asStateFlow()
    private val _status = MutableStateFlow("Rádio desligado")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun messages(conversation: String): Flow<List<MessageEntity>> = db.messages().observe(conversation)

    fun rooms(): Flow<List<RoomEntity>> = db.rooms().observe()

    fun ensureStarted() {
        synchronized(this) {
            if (engine != null) return
            generation += 1
            val token = generation
            val name = TextRules.nickname(profile.nickname.value) ?: "sem nome"
            val transport = NearbyMeshTransport(appContext, name, ::logLine)
            val created = MeshEngine(
                identity = identity,
                transport = transport,
                seen = RoomSeen(db),
                outbox = RoomOutbox(db),
                peerStore = RoomPeers(db),
                publicLog = RoomPublicLog(db),
                scope = scope,
                log = ::logLine,
            )
            created.prepareNickname(name)
            nearby = transport
            engine = created
            _phase.value = RadioPhase.STARTING
            _status.value = "Ligando o rádio"
            scope.launch {
                created.events.collect { event ->
                    if (token == generation) handle(event)
                }
            }
            scope.launch {
                created.peers.collect { peers ->
                    if (token == generation) _peers.value = peers
                }
            }
            scope.launch {
                created.linkCount.collect { count ->
                    if (token == generation) _linkCount.value = count
                }
            }
            scope.launch {
                created.health.collect { health ->
                    if (token != generation) return@collect
                    _phase.value = health.phase
                    _status.value = health.detail
                }
            }
            created.start()
            logLine("serviço no ar")
        }
    }

    fun stop() {
        val current: MeshEngine
        synchronized(this) {
            generation += 1
            current = engine ?: return
            engine = null
            nearby = null
            _phase.value = RadioPhase.OFF
            _status.value = "Rádio desligado"
            _linkCount.value = 0
        }
        scope.launch { current.stop() }
        logLine("rádio desligado")
    }

    suspend fun rename(name: String) {
        engine?.rename(name)
        try {
            nearby?.setAdvertisedName(name)
        } catch (_: Exception) {
            logLine("não atualizei o nome anunciado")
        }
    }

    suspend fun sendPublic(text: String): SendResult {
        val current = engine ?: return SendResult.Rejected(Reject.RadioOff)
        val clean = TextRules.message(text) ?: return SendResult.Rejected(if (text.isBlank()) Reject.Empty else Reject.TooLong)
        val result = current.sendPublic(clean)
        if (result is SendResult.Queued) {
            db.messages().insert(
                MessageEntity(
                    id = result.id,
                    conversation = MeshLimits.CONVERSATION_PUBLIC,
                    senderId = identity.peerId,
                    body = clean,
                    sentAt = result.timestamp,
                    outgoing = true,
                    status = "sent",
                ),
            )
        }
        return result
    }

    suspend fun sendDirect(peerId: String, text: String): SendResult {
        val current = engine ?: return SendResult.Rejected(Reject.RadioOff)
        val clean = TextRules.message(text) ?: return SendResult.Rejected(if (text.isBlank()) Reject.Empty else Reject.TooLong)
        val result = current.sendDirect(peerId, clean)
        if (result is SendResult.Queued) {
            db.messages().insert(
                MessageEntity(
                    id = result.id,
                    conversation = peerId,
                    senderId = identity.peerId,
                    body = clean,
                    sentAt = result.timestamp,
                    outgoing = true,
                    status = "pending",
                ),
            )
        }
        return result
    }

    suspend fun createRoom(name: String, password: String?): SendResult {
        val title = TextRules.nickname(name) ?: return SendResult.Rejected(Reject.Empty)
        val secret = password?.trim().orEmpty()
        val locked = secret.isNotEmpty()
        val id = randomId()
        db.rooms().upsert(RoomEntity(id, title, locked, if (locked) secret else null, identity.peerId, true))
        engine?.sendFlood(PacketType.ROOM_META, RoomFrames.meta(id, title, locked), MeshLimits.ANNOUNCE_TTL)
        logLine("sala $title")
        return SendResult.Queued(id, System.currentTimeMillis())
    }

    suspend fun joinRoom(roomId: String, password: String?): Boolean {
        val room = db.rooms().get(roomId) ?: return false
        if (!room.hasPassword) {
            db.rooms().upsert(room.copy(joined = true))
            return true
        }
        val given = password?.trim().orEmpty()
        if (given.isEmpty()) return false
        val probe = "blip".encodeToByteArray()
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = RoomCipher.key(given, roomId)
        val sealed = RoomCipher.seal(key, roomId, probe, nonce)
        if (RoomCipher.open(key, roomId, sealed, nonce) == null) return false
        db.rooms().upsert(room.copy(secret = given, joined = true))
        return true
    }

    suspend fun sendRoom(roomId: String, text: String): SendResult {
        val current = engine ?: return SendResult.Rejected(Reject.RadioOff)
        val clean = TextRules.message(text) ?: return SendResult.Rejected(if (text.isBlank()) Reject.Empty else Reject.TooLong)
        val room = db.rooms().get(roomId) ?: return SendResult.Rejected(Reject.UnknownPeer)
        val encrypted: Boolean
        val body = if (room.hasPassword) {
            val secret = room.secret ?: return SendResult.Rejected(Reject.NoKey)
            encrypted = true
            val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
            nonce + RoomCipher.seal(RoomCipher.key(secret, roomId), roomId, clean.encodeToByteArray(), nonce)
        } else {
            encrypted = false
            clean.encodeToByteArray()
        }
        val result = current.sendFlood(PacketType.ROOM_TEXT, RoomFrames.text(roomId, encrypted, body), MeshLimits.PUBLIC_TTL)
        if (result is SendResult.Queued) {
            insertText(result.id, conversationForRoom(roomId), identity.peerId, clean, result.timestamp, true, "sent")
        }
        return result
    }

    suspend fun sendPlace(geohash: String, text: String): SendResult {
        val current = engine ?: return SendResult.Rejected(Reject.RadioOff)
        val clean = TextRules.message(text) ?: return SendResult.Rejected(if (text.isBlank()) Reject.Empty else Reject.TooLong)
        val result = current.sendFlood(PacketType.PLACE_TEXT, PlaceFrames.text(geohash, clean), MeshLimits.PUBLIC_TTL)
        if (result is SendResult.Queued) {
            insertText(result.id, conversationForPlace(geohash), identity.peerId, clean, result.timestamp, true, "sent")
        }
        return result
    }

    suspend fun sendMedia(conversation: String, mime: String, bytes: ByteArray, wave: String? = null): SendResult {
        val current = engine ?: return SendResult.Rejected(Reject.RadioOff)
        val direct = if (conversation.length == 64 && conversation.all { it in '0'..'9' || it in 'a'..'f' }) conversation else null
        val result = current.sendMedia(conversation, mime, bytes, direct)
        if (result is SendResult.Queued) {
            val path = writeMedia(conversation, result.id, mime, bytes)
            val audio = mime.startsWith("audio")
            db.messages().insert(
                MessageEntity(
                    id = result.id,
                    conversation = conversation,
                    senderId = identity.peerId,
                    body = if (audio) wave?.takeIf { Waveform.decode(it) != null } ?: "Áudio" else "Imagem",
                    sentAt = result.timestamp,
                    outgoing = true,
                    status = "sent",
                    kind = if (audio) "audio" else "image",
                    mediaPath = path,
                ),
            )
        }
        return result
    }

    suspend fun saveWave(id: String, wave: String) {
        if (Waveform.decode(wave) == null) return
        db.messages().updateWave(id, wave)
    }

    suspend fun clearConversation(conversation: String) {
        db.messages().mediaPaths(conversation).forEach { path -> File(path).delete() }
        db.messages().deleteConversation(conversation)
        logLine("histórico apagado")
    }

    private suspend fun handle(event: MeshEvent) {
        when (event) {
            is MeshEvent.PublicMessage -> db.messages().insert(
                MessageEntity(
                    id = event.id,
                    conversation = MeshLimits.CONVERSATION_PUBLIC,
                    senderId = event.sender,
                    body = event.text,
                    sentAt = event.at,
                    outgoing = false,
                    status = "sent",
                ),
            )
            is MeshEvent.DirectMessage -> db.messages().insert(
                MessageEntity(
                    id = event.id,
                    conversation = event.sender,
                    senderId = event.sender,
                    body = event.text,
                    sentAt = event.at,
                    outgoing = false,
                    status = "delivered",
                ),
            )
            is MeshEvent.Acked -> db.messages().updateStatus(event.id, "delivered")
            is MeshEvent.GiveUp -> db.messages().updateStatus(event.id, "failed")
            is MeshEvent.RoomMeta -> {
                val existing = db.rooms().get(event.roomId)
                if (existing == null) {
                    db.rooms().upsert(
                        RoomEntity(event.roomId, event.name, event.hasPassword, null, event.from, false),
                    )
                }
            }
            is MeshEvent.RoomPost -> storeRoomPost(event)
            is MeshEvent.MediaReady -> {
                val path = writeMedia(event.conversation, event.id, event.mime, event.bytes)
                db.messages().insert(
                    MessageEntity(
                        id = event.id,
                        conversation = event.conversation,
                        senderId = event.sender,
                        body = if (event.mime.startsWith("audio")) "Áudio" else "Imagem",
                        sentAt = event.at,
                        outgoing = false,
                        status = "sent",
                        kind = if (event.mime.startsWith("audio")) "audio" else "image",
                        mediaPath = path,
                    ),
                )
            }
            is MeshEvent.PlacePost -> insertText(
                event.id,
                conversationForPlace(event.geohash),
                event.sender,
                event.text,
                event.at,
                false,
                "sent",
            )
        }
    }

    private suspend fun storeRoomPost(event: MeshEvent.RoomPost) {
        val room = db.rooms().get(event.roomId)
        val text = if (!event.encrypted) {
            TextRules.message(event.body.toString(Charsets.UTF_8))
        } else {
            val secret = room?.secret ?: return
            if (event.body.size < 13) return
            val nonce = event.body.copyOf(12)
            val open = RoomCipher.open(
                RoomCipher.key(secret, event.roomId),
                event.roomId,
                event.body.copyOfRange(12, event.body.size),
                nonce,
            ) ?: return
            TextRules.message(open.toString(Charsets.UTF_8))
        } ?: return
        insertText(event.id, conversationForRoom(event.roomId), event.sender, text, event.at, false, "sent")
    }

    private suspend fun insertText(
        id: String,
        conversation: String,
        sender: String,
        body: String,
        at: Long,
        outgoing: Boolean,
        status: String,
    ) {
        db.messages().insert(
            MessageEntity(id, conversation, sender, body, at, outgoing, status),
        )
    }

    private fun writeMedia(conversation: String, id: String, mime: String, bytes: ByteArray): String {
        val ext = if (mime.startsWith("audio")) "m4a" else "jpg"
        val dir = File(appContext.filesDir, "media/$conversation").apply { mkdirs() }
        val file = File(dir, "$id.$ext")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun logLine(line: String) {
        val stamp = clock.format(LocalTime.now())
        val row = "$stamp  $line"
        Log.i("Blip", line)
        synchronized(lines) {
            lines.addLast(row)
            while (lines.size > 80) lines.removeFirst()
            _log.value = lines.toList()
        }
    }
}
