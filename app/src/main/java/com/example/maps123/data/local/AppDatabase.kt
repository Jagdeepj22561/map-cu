package com.example.maps123.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.maps123.data.local.AppDatabase.Companion.INSTANCE
import com.example.maps123.data.repository.AuthRepository


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
    version = 28
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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                ACTIVE_DB_NAME = dbName
                instance
            }
        }

    }
}
