package dev.vereda.core

interface SeenStore {
    suspend fun contains(id: String): Boolean
    suspend fun add(id: String, at: Long)
    suspend fun prune(olderThan: Long, maxEntries: Int)
}

interface OutboxStore {
    suspend fun enqueue(id: String, encoded: ByteArray, at: Long)
    suspend fun list(): List<OutboxEntry>
    suspend fun bump(id: String)
    suspend fun remove(id: String)
}

data class OutboxEntry(
    val id: String,
    val encoded: ByteArray,
    val attempts: Int,
    val createdAt: Long,
)

interface PeerStore {
    suspend fun all(): List<PeerSnapshot>
    suspend fun upsert(peer: PeerSnapshot)
}

interface PublicLog {
    suspend fun add(id: String, encoded: ByteArray, at: Long)
    suspend fun recent(limit: Int): List<ByteArray>
}

class MemorySeen : SeenStore {
    private val ids = LinkedHashMap<String, Long>()

    override suspend fun contains(id: String): Boolean = ids.containsKey(id)

    override suspend fun add(id: String, at: Long) {
        ids.remove(id)
        ids[id] = at
    }

    override suspend fun prune(olderThan: Long, maxEntries: Int) {
        val stale = ids.filterValues { it < olderThan }.keys.toList()
        stale.forEach { ids.remove(it) }
        while (ids.size > maxEntries) {
            val eldest = ids.entries.firstOrNull()?.key ?: break
            ids.remove(eldest)
        }
    }
}

class MemoryOutbox : OutboxStore {
    private val items = LinkedHashMap<String, OutboxEntry>()

    override suspend fun enqueue(id: String, encoded: ByteArray, at: Long) {
        items[id] = OutboxEntry(id, encoded, 0, at)
    }

    override suspend fun list(): List<OutboxEntry> = items.values.toList()

    override suspend fun bump(id: String) {
        val current = items[id] ?: return
        items[id] = current.copy(attempts = current.attempts + 1)
    }

    override suspend fun remove(id: String) {
        items.remove(id)
    }
}

class MemoryPeers : PeerStore {
    private val peers = LinkedHashMap<String, PeerSnapshot>()

    override suspend fun all(): List<PeerSnapshot> = peers.values.toList()

    override suspend fun upsert(peer: PeerSnapshot) {
        peers[peer.id] = peer
    }
}

class MemoryPublicLog : PublicLog {
    private val items = LinkedHashMap<String, Pair<Long, ByteArray>>()

    override suspend fun add(id: String, encoded: ByteArray, at: Long) {
        items.remove(id)
        items[id] = at to encoded
        while (items.size > MeshLimits.PUBLIC_MEMORY) {
            val eldest = items.entries.firstOrNull()?.key ?: break
            items.remove(eldest)
        }
    }

    override suspend fun recent(limit: Int): List<ByteArray> {
        return items.values.toList().takeLast(limit).map { it.second }
    }
}
