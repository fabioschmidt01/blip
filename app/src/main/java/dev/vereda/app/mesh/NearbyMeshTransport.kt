package dev.vereda.app.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dev.vereda.core.MeshTransport
import dev.vereda.core.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.ConcurrentHashMap

class NearbyMeshTransport(
    context: Context,
    advertisedName: String,
    private val log: (String) -> Unit,
) : MeshTransport {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _events = MutableSharedFlow<TransportEvent>(replay = 8, extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()
    private val connected = ConcurrentHashMap.newKeySet<String>()
    private val requested = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var localName: String = advertisedName.take(80)

    @Volatile
    private var running = false

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            emit(TransportEvent.Frame(endpointId, bytes.copyOf()))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: com.google.android.gms.nearby.connection.ConnectionInfo) {
            Nearby.getConnectionsClient(appContext)
                .acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { error -> note("não aceitei ${info.endpointName}", error) }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val code = result.status.statusCode
            val ok = result.status.isSuccess || code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT
            if (!ok) {
                requested.remove(endpointId)
                log("conexão recusada ($code)")
                return
            }
            connected.add(endpointId)
            requested.remove(endpointId)
            emit(TransportEvent.LinkUp(endpointId, endpointId.take(6)))
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            requested.remove(endpointId)
            emit(TransportEvent.LinkDown(endpointId))
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (endpointId in connected || !requested.add(endpointId)) return
            Nearby.getConnectionsClient(appContext)
                .requestConnection(localName, endpointId, connectionCallback)
                .addOnFailureListener { error ->
                    requested.remove(endpointId)
                    note("não pedi ligação a ${info.endpointName}", error)
                }
        }

        override fun onEndpointLost(endpointId: String) {
            if (endpointId !in connected) requested.remove(endpointId)
        }
    }

    override suspend fun start() {
        if (running) return
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        if (availability != ConnectionResult.SUCCESS) {
            emit(TransportEvent.Failed(playMessage(availability)))
            return
        }
        try {
            withContext(Dispatchers.Main) {
                val client = Nearby.getConnectionsClient(appContext)
                awaitDone(client.startAdvertising(localName, SERVICE_ID, connectionCallback, advertising()))
                var discoveryError: Exception? = null
                try {
                    awaitDone(client.startDiscovery(SERVICE_ID, discoveryCallback, discovery()))
                } catch (error: Exception) {
                    discoveryError = error
                }
                if (discoveryError != null) {
                    ignore { client.stopAdvertising() }
                    throw discoveryError
                }
            }
            running = true
            emit(TransportEvent.Ready("Rádio ligado. Procurando aparelhos."))
        } catch (error: Exception) {
            note("falha ao ligar", error)
            val detail = error.message.orEmpty()
            val message = when {
                "ACCESS_WIFI_STATE" in detail -> "O rádio precisa da permissão de estado do Wi-Fi."
                "MISSING_PERMISSION" in detail -> "Falta uma permissão para o rádio ligar."
                else -> "Não consegui ligar o rádio. Confere o Bluetooth e o Google Play Services."
            }
            emit(TransportEvent.Failed(message))
        }
    }

    override suspend fun stop() {
        running = false
        withContext(Dispatchers.Main) {
            val client = Nearby.getConnectionsClient(appContext)
            ignore { client.stopAdvertising() }
            ignore { client.stopDiscovery() }
            ignore { client.stopAllEndpoints() }
        }
        connected.clear()
        requested.clear()
    }

    override suspend fun send(linkId: String, bytes: ByteArray) {
        if (linkId !in connected) return
        if (bytes.size > MAX_BYTES) {
            log("pacote grande demais para o rádio")
            return
        }
        withContext(Dispatchers.Main) {
            Nearby.getConnectionsClient(appContext).sendPayload(linkId, Payload.fromBytes(bytes))
        }
    }

    suspend fun setAdvertisedName(name: String) {
        localName = name.take(80)
        if (!running) return
        withContext(Dispatchers.Main) {
            val client = Nearby.getConnectionsClient(appContext)
            ignore { client.stopAdvertising() }
            awaitDone(client.startAdvertising(localName, SERVICE_ID, connectionCallback, advertising()))
        }
    }

    private fun advertising() = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

    private fun discovery() = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

    private fun emit(event: TransportEvent) {
        if (!_events.tryEmit(event)) {
            scope.launch { _events.emit(event) }
        }
    }

    private suspend fun ignore(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }

    private suspend fun awaitDone(task: com.google.android.gms.tasks.Task<*>) {
        suspendCancellableCoroutine { continuation ->
            task.addOnCompleteListener { finished ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (finished.isSuccessful) continuation.resume(Unit)
                else continuation.resumeWithException(finished.exception ?: IllegalStateException("tarefa sem detalhe"))
            }
        }
    }

    private fun note(what: String, error: Exception) {
        Log.w(TAG, what, error)
        log(what)
    }

    private fun playMessage(code: Int): String = when (code) {
        ConnectionResult.SERVICE_MISSING -> "Este aparelho não tem Google Play Services."
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Atualiza o Google Play Services para o rádio funcionar."
        ConnectionResult.SERVICE_DISABLED -> "O Google Play Services está desativado."
        else -> "Google Play Services indisponível ($code)."
    }

    private companion object {
        const val TAG = "Blip"
        const val SERVICE_ID = "dev.vereda.mesh"
        const val MAX_BYTES = 28_000
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    }
}
