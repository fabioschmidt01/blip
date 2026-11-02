package dev.vereda.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        SeenEntity::class,
        OutboxEntity::class,
        PublicLogEntity::class,
        RoomEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class VeredaDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun peers(): PeerDao
    abstract fun seen(): SeenDao
    abstract fun outbox(): OutboxDao
    abstract fun publicLog(): PublicLogDao
    abstract fun rooms(): RoomDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN kind TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE messages ADD COLUMN mediaPath TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rooms (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        hasPassword INTEGER NOT NULL,
                        secret TEXT,
                        creator TEXT NOT NULL,
                        joined INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): VeredaDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                VeredaDatabase::class.java,
                "vereda.db",
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
