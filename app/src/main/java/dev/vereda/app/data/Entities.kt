package dev.vereda.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversation: String,
    val senderId: String,
    val body: String,
    val sentAt: Long,
    val outgoing: Boolean,
    val status: String,
    val kind: String = "text",
    val mediaPath: String? = null,
)

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hasPassword: Boolean,
    val secret: String?,
    val creator: String,
    val joined: Boolean,
)

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val encPublicHex: String,
    val lastSeen: Long,
)

@Entity(tableName = "seen")
data class SeenEntity(
    @PrimaryKey val id: String,
    val seenAt: Long,
)

@Entity(tableName = "outbox")
class OutboxEntity(
    @PrimaryKey val id: String,
    val encoded: ByteArray,
    val createdAt: Long,
    val attempts: Int,
)

@Entity(tableName = "public_log")
class PublicLogEntity(
    @PrimaryKey val id: String,
    val encoded: ByteArray,
    val sentAt: Long,
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation = :conversation ORDER BY sentAt ASC, id ASC")
    fun observe(conversation: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE messages SET body = :body WHERE id = :id AND kind = 'audio'")
    suspend fun updateWave(id: String, body: String)

    @Query("SELECT mediaPath FROM messages WHERE conversation = :conversation AND mediaPath IS NOT NULL")
    suspend fun mediaPaths(conversation: String): List<String>

    @Query("DELETE FROM messages WHERE conversation = :conversation")
    suspend fun deleteConversation(conversation: String)
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers")
    suspend fun all(): List<PeerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)
}

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<RoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(room: RoomEntity)

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun get(id: String): RoomEntity?

    @Query("DELETE FROM rooms")
    suspend fun deleteAll()
}

@Dao
interface SeenDao {
    @Query("SELECT COUNT(*) FROM seen WHERE id = :id")
    suspend fun count(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(seen: SeenEntity)

    @Query("DELETE FROM seen WHERE seenAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("SELECT COUNT(*) FROM seen")
    suspend fun countAll(): Int

    @Query("DELETE FROM seen WHERE id IN (SELECT id FROM seen ORDER BY seenAt ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox")
    suspend fun all(): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OutboxEntity)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bump(id: String)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PublicLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PublicLogEntity)

    @Query("SELECT encoded FROM public_log ORDER BY sentAt ASC")
    suspend fun encodedAscending(): List<ByteArray>

    @Query(
        "DELETE FROM public_log WHERE id NOT IN (SELECT id FROM public_log ORDER BY sentAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}
