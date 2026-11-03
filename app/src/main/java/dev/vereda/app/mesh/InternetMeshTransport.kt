package dev.vereda.app.mesh

import android.content.Context
import android.os.Build
import android.util.Base64
import dev.vereda.app.R
import dev.vereda.core.MeshTransport
import dev.vereda.core.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One link per person reached through the relay. Link ids start with [PREFIX].
 * Place packets never leave the device on this path.
 */
class InternetMeshTransport(
    context: Context,
    private val selfId: String,
    private val nameOf: () -> String,
    private val announceHealth: Boolean,
    private val log: (String) -> Unit,
) : MeshTransport {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<TransportEvent>(replay = 8, extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()
    private val online = LinkedHashMap<String, String>()
    private var loop: Job? = null

    @Volatile
    private var running = false

    override suspend fun start() {
        if (running) return
        running = true
        loop = scope.launch {
            var up = false
            var warned = false
            while (isActive && running) {
                val ok = try {
                    poll()
                    true
                } catch (_: Exception) {
                    false
                }
                if (ok && !up) {
                    up = true
                    warned = false
                    if (announceHealth) {
                        emit(TransportEvent.Ready(appContext.getString(R.string.internet_ready)))
                    } else {
                        log("caminho de internet no ar")
                    }
                } else if (!ok && (up || !warned)) {
                    val wasUp = up
                    up = false
                    warned = true
                    dropAll()
                    if (announceHealth && (wasUp || !wasUp)) {
                        emit(TransportEvent.Failed(appContext.getString(R.string.internet_down)))
                    } else if (!announceHealth) {
                        log("caminho de internet indisponível")
                    }
                }
                delay(2_000)
            }
        }
    }

    override suspend fun stop() {
        running = false
        loop?.cancel()
        loop = null
        online.clear()
    }

    override suspend fun send(linkId: String, bytes: ByteArray) {
        if (!linkId.startsWith(PREFIX) || isPlace(bytes)) return
        val target = linkId.removePrefix(PREFIX)
        val body = JSONObject()
            .put("id", selfId)
            .put("to", target)
            .put("packet", Base64.encodeToString(bytes, Base64.NO_WRAP))
        try {
            withContext(Dispatchers.IO) { post("/v1/push", body) }
        } catch (_: Exception) {
            // The relay can be down. The engine keeps the message and tries again.
        }
    }

    private fun poll() {
        val hello = JSONObject().put("id", selfId).put("name", nameOf().take(80))
        post("/v1/hello", hello)
        val pull = get("/v1/pull?id=$selfId")
        val root = JSONObject(pull)
        val peers = root.optJSONArray("peers")
        val seen = LinkedHashSet<String>()
        if (peers != null) {
            for (index in 0 until peers.length()) {
                val row = peers.optJSONObject(index) ?: continue
                val id = row.optString("id")
                if (id.isBlank() || id == selfId) continue
                seen.add(id)
                val name = row.optString("name").ifBlank { id.take(8) }
                if (online[id] == null) {
                    online[id] = name
                    emit(TransportEvent.LinkUp(PREFIX + id, name))
                }
            }
        }
        val gone = online.keys.filter { it !in seen }
        gone.forEach { id ->
            online.remove(id)
            emit(TransportEvent.LinkDown(PREFIX + id))
        }
        val packets = root.optJSONArray("packets") ?: return
        for (index in 0 until packets.length()) {
            val encoded = packets.optString(index)
            if (encoded.isBlank()) continue
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (isPlace(bytes)) continue
            val sender = senderOf(bytes) ?: continue
            if (sender == selfId) continue
            emit(TransportEvent.Frame(PREFIX + sender, bytes))
        }
    }

    private fun dropAll() {
        val ids = online.keys.toList()
        online.clear()
        ids.forEach { emit(TransportEvent.LinkDown(PREFIX + it)) }
    }

    private fun post(path: String, body: JSONObject) {
        val connection = open(path, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val raw = body.toString().toByteArray()
        connection.outputStream.use { it.write(raw) }
        finish(connection)
    }

    private fun get(path: String): String = finish(open(path, "GET"))

    private fun open(path: String, method: String): HttpURLConnection {
        val connection = URL(relayBase() + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 2_500
        connection.readTimeout = 4_000
        return connection
    }

    private fun finish(connection: HttpURLConnection): String {
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("relay $code")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun emit(event: TransportEvent) {
        _events.tryEmit(event)
    }

    private fun relayBase(): String {
        val model = Build.MODEL.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        if (model.contains("WayDroid", ignoreCase = true) || hardware.contains("waydroid", ignoreCase = true)) {
            return "http://192.168.240.1:$PORT"
        }
        return "http://127.0.0.1:$PORT"
    }

    companion object {
        const val PREFIX = "i:"
        const val PORT = 8791

        fun owns(linkId: String) = linkId.startsWith(PREFIX)

        private fun isPlace(bytes: ByteArray): Boolean {
            return bytes.size > 3 && (bytes[3].toInt() and 0xFF) == 8
        }

        private fun senderOf(bytes: ByteArray): String? {
            if (bytes.size < 62) return null
            return bytes.copyOfRange(30, 62).joinToString("") { "%02x".format(it) }
        }
    }
}
