package dev.vereda.core

sealed interface TransportEvent {
    data class LinkUp(val linkId: String, val remoteName: String) : TransportEvent
    data class LinkDown(val linkId: String) : TransportEvent
    data class Frame(val linkId: String, val bytes: ByteArray) : TransportEvent
    data class Ready(val message: String) : TransportEvent
    data class Failed(val message: String) : TransportEvent
}

interface MeshTransport {
    val events: kotlinx.coroutines.flow.Flow<TransportEvent>
    suspend fun start()
    suspend fun stop()
    suspend fun send(linkId: String, bytes: ByteArray)
}
