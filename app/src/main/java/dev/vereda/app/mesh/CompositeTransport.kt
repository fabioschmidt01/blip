package dev.vereda.app.mesh

import dev.vereda.core.MeshTransport
import dev.vereda.core.TransportEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

class CompositeTransport(
    private val parts: List<MeshTransport>,
    private val owns: List<(String) -> Boolean>,
) : MeshTransport {
    override val events: Flow<TransportEvent> = merge(*parts.map { it.events }.toTypedArray())

    override suspend fun start() {
        parts.forEach { it.start() }
    }

    override suspend fun stop() {
        parts.forEach { it.stop() }
    }

    override suspend fun send(linkId: String, bytes: ByteArray) {
        val index = owns.indexOfFirst { it(linkId) }
        if (index >= 0) parts[index].send(linkId, bytes)
    }
}
