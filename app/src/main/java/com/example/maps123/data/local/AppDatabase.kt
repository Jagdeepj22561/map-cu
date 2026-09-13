package com.example.maps123.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.maps123.data.local.AppDatabase.Companion.INSTANCE
import com.example.maps123.data.repository.AuthRepository

// Migration 28→29: recreate cached_announcements with current schema (new event columns added)
private val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `cached_announcements`")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cached_announcements` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `imageUrl` TEXT,
                `timestamp` INTEGER NOT NULL,
                `author` TEXT NOT NULL,
                `authorUid` TEXT,
                `authorProfilePicUrl` TEXT,
                `type` TEXT NOT NULL,
                `likesJson` TEXT NOT NULL,
                `commentsJson` TEXT NOT NULL,
                `shareCount` INTEGER NOT NULL,
                `viewCount` INTEGER NOT NULL,
                `itemName` TEXT,
                `place` TEXT,
                `time` TEXT,
                `reward` TEXT,
                `eventVenue` TEXT,
                `eventTime` TEXT,
                `eventPurpose` TEXT,
                `eventDlType` TEXT,
                `eventMode` TEXT,
                `eventMaxMembers` INTEGER,
                `eventDepartments` TEXT,
                `eventLink` TEXT,
                `eventCategory` TEXT,
                `cachedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

// Migration 29→30: future-proof bump (no schema changes)
private val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) { /* reserved */ }
}

@Database(
    entities = [
        RouteEntity::class,
        PlaceEntity::class,
        UserEntity::class,
        AnnouncementCacheEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        FriendRequestEntity::class
    ],
    version = 30
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun routeDao(): RouteDao
    abstract fun placeDao(): PlaceDao
    abstract fun userDao(): UserDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var ACTIVE_DB_NAME: String? = null

        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
            ACTIVE_DB_NAME = null
        }

        fun getInstance(context: Context): AppDatabase {
            val authUid = AuthRepository.currentUserId()
            val uid = authUid ?: "guest"
            val dbName = "chat_database_$uid"
            INSTANCE?.let { existing ->
                if (ACTIVE_DB_NAME == dbName) return existing
                existing.close()
                INSTANCE = null
                ACTIVE_DB_NAME = null
            }
            return synchronized(this) {
                INSTANCE?.let { existing ->
                    if (ACTIVE_DB_NAME == dbName) {
                        return@synchronized existing
                    }
                    existing.close()
                    INSTANCE = null
                }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(MIGRATION_28_29, MIGRATION_29_30)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                ACTIVE_DB_NAME = dbName
                instance
            }
        }
    }
}

