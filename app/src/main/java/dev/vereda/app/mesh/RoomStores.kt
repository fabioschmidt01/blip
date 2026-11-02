package dev.vereda.app.mesh

import dev.vereda.app.data.OutboxEntity
import dev.vereda.app.data.PeerEntity
import dev.vereda.app.data.PublicLogEntity
import dev.vereda.app.data.SeenEntity
import dev.vereda.app.data.VeredaDatabase
import dev.vereda.core.MeshLimits
import dev.vereda.core.OutboxEntry
import dev.vereda.core.OutboxStore
import dev.vereda.core.PeerSnapshot
import dev.vereda.core.PeerStore
import dev.vereda.core.PublicLog
import dev.vereda.core.SeenStore

class RoomSeen(private val db: VeredaDatabase) : SeenStore {
    override suspend fun contains(id: String): Boolean = db.seen().count(id) > 0

    override suspend fun add(id: String, at: Long) {
        db.seen().insert(SeenEntity(id, at))
    }

    override suspend fun prune(olderThan: Long, maxEntries: Int) {
        db.seen().deleteOlderThan(olderThan)
        val extra = db.seen().countAll() - maxEntries
        if (extra > 0) db.seen().deleteOldest(extra)
    }
}

class RoomOutbox(private val db: VeredaDatabase) : OutboxStore {
    override suspend fun enqueue(id: String, encoded: ByteArray, at: Long) {
        db.outbox().upsert(OutboxEntity(id, encoded, at, 0))
    }

    override suspend fun list(): List<OutboxEntry> {
        return db.outbox().all().map { OutboxEntry(it.id, it.encoded, it.attempts, it.createdAt) }
    }

    override suspend fun bump(id: String) {
        db.outbox().bump(id)
    }

    override suspend fun remove(id: String) {
        db.outbox().delete(id)
    }
}

class RoomPeers(private val db: VeredaDatabase) : PeerStore {
    override suspend fun all(): List<PeerSnapshot> {
        return db.peers().all().map {
            PeerSnapshot(it.id, it.nickname, it.encPublicHex, it.lastSeen, direct = false)
        }
    }

    override suspend fun upsert(peer: PeerSnapshot) {
        db.peers().upsert(PeerEntity(peer.id, peer.nickname, peer.encPublicHex, peer.lastSeen))
    }
}

class RoomPublicLog(private val db: VeredaDatabase) : PublicLog {
    override suspend fun add(id: String, encoded: ByteArray, at: Long) {
        db.publicLog().upsert(PublicLogEntity(id, encoded, at))
        db.publicLog().trim(MeshLimits.PUBLIC_MEMORY)
    }

    override suspend fun recent(limit: Int): List<ByteArray> {
        return db.publicLog().encodedAscending().takeLast(limit)
    }
}
