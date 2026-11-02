package dev.vereda.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vereda.app.MainActivity
import dev.vereda.app.VeredaApp
import dev.vereda.app.service.MeshService
import dev.vereda.core.MeshLimits
import dev.vereda.core.Reject
import dev.vereda.core.SendResult
import dev.vereda.core.TextRules
import dev.vereda.core.Waveform
import dev.vereda.core.conversationForPlace
import dev.vereda.core.conversationForRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.system.exitProcess

class RadioViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as VeredaApp).graph
    private val repository = graph.repository

    val selfId = graph.identity.peerId
    val fingerprint = graph.identity.fingerprint()
    val ready = graph.profile.ready
    val nickname = graph.profile.nickname
    val peers = repository.peers
    val linkCount = repository.linkCount
    val phase = repository.phase
    val status = repository.status
    val log = repository.log
    val rooms = repository.rooms()

    private val _banner = MutableStateFlow<String?>(null)
    val banner = _banner.asStateFlow()

    private val _conversation = MutableStateFlow(MeshLimits.CONVERSATION_PUBLIC)
    val conversation = _conversation.asStateFlow()

    private val _conversationTitle = MutableStateFlow("Aqui")
    val conversationTitle = _conversationTitle.asStateFlow()

    fun messagesFor(conversation: String) = repository.messages(conversation)

    fun direct(peerId: String) = repository.messages(peerId)

    fun openPublic() {
        _conversation.value = MeshLimits.CONVERSATION_PUBLIC
        _conversationTitle.value = "Aqui"
    }

    fun openRoom(roomId: String, name: String) {
        _conversation.value = conversationForRoom(roomId)
        _conversationTitle.value = name
    }

    fun openPlace(geohash: String) {
        _conversation.value = conversationForPlace(geohash)
        _conversationTitle.value = geohash
    }

    fun completeOnboarding(name: String): String? {
        val clean = TextRules.nickname(name) ?: return "Usa um nome curto, de até 32 bytes."
        graph.profile.setNickname(clean)
        graph.profile.markReady()
        return null
    }

    fun rename(name: String): String? {
        val clean = TextRules.nickname(name) ?: return "Usa um nome curto, de até 32 bytes."
        graph.profile.setNickname(clean)
        viewModelScope.launch { repository.rename(clean) }
        return null
    }

    fun sendCurrent(text: String) {
        val conversation = _conversation.value
        launchSend {
            when {
                conversation == MeshLimits.CONVERSATION_PUBLIC -> repository.sendPublic(text)
                conversation.startsWith("room:") -> repository.sendRoom(conversation.removePrefix("room:"), text)
                conversation.startsWith("place:") -> repository.sendPlace(conversation.removePrefix("place:"), text)
                else -> repository.sendPublic(text)
            }
        }
    }

    fun sendDirect(peerId: String, text: String) = launchSend { repository.sendDirect(peerId, text) }

    fun createRoom(name: String, password: String) {
        viewModelScope.launch {
            when (val result = repository.createRoom(name, password.ifBlank { null })) {
                is SendResult.Queued -> {
                    openRoom(result.id, name.trim())
                    _banner.value = null
                }
                is SendResult.Rejected -> _banner.value = result.reason.pt()
            }
        }
    }

    fun joinRoom(roomId: String, name: String, password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.joinRoom(roomId, password.ifBlank { null })
            if (ok) openRoom(roomId, name)
            onDone(ok)
        }
    }

    fun sendImageCurrent(uri: Uri) = sendImage(_conversation.value, uri)

    fun sendAudioCurrent(file: File, wave: String) = sendAudio(_conversation.value, file, wave)

    fun report(text: String) {
        _banner.value = text
    }

    fun storeWave(id: String, wave: String) {
        if (Waveform.decode(wave) == null) return
        viewModelScope.launch { repository.saveWave(id, wave) }
    }

    fun sendImage(conversation: String, uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { compressImage(uri) }
            if (bytes == null) {
                _banner.value = "Não consegui ler essa imagem."
                return@launch
            }
            launchResult(repository.sendMedia(conversation, "image/jpeg", bytes))
        }
    }

    fun sendAudio(conversation: String, file: File, wave: String) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val size = if (file.exists()) file.length() else 0L
                val limit = MeshLimits.MAX_MEDIA_BYTES.toLong()
                val data = if (size in 500L..limit) file.readBytes() else null
                val tooBig = size > limit
                file.delete()
                if (tooBig) ByteArray(0) else data
            }
            when {
                bytes == null -> _banner.value = "Segura um pouco mais."
                bytes.isEmpty() -> _banner.value = "Esse áudio não cabe no rádio."
                else -> launchResult(repository.sendMedia(conversation, "audio/mp4", bytes, wave))
            }
        }
    }

    fun clearConversation(conversation: String) {
        viewModelScope.launch { repository.clearConversation(conversation) }
    }

    fun panic() {
        val context = getApplication<Application>()
        MeshService.stop(context)
        graph.profile.wipe()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.getDatabasePath("vereda.db").delete()
        context.getDatabasePath("vereda.db-wal").delete()
        context.getDatabasePath("vereda.db-shm").delete()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }

    fun clearBanner() {
        _banner.value = null
    }

    private fun launchSend(block: suspend () -> SendResult) {
        viewModelScope.launch { launchResult(block()) }
    }

    private fun launchResult(result: SendResult) {
        _banner.value = when (result) {
            is SendResult.Queued -> null
            is SendResult.Rejected -> result.reason.pt()
        }
    }

    private fun compressImage(uri: Uri): ByteArray? {
        val context = getApplication<Application>()
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val maxSide = 1280
        val scale = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1).toFloat() / maxSide
        val target = if (scale > 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width / scale).toInt(), (bitmap.height / scale).toInt(), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, 72, out)
        if (target !== bitmap) target.recycle()
        return out.toByteArray()
    }
}

fun Reject.pt(): String = when (this) {
    Reject.Empty -> "Escreve alguma coisa."
    Reject.TooLong -> "Isso não cabe no rádio."
    Reject.RadioOff -> "O rádio ainda não está ligado."
    Reject.UnknownPeer -> "Esse rádio ainda não se apresentou."
    Reject.NoKey -> "Entra na sala com a senha antes de falar."
}
