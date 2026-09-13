package com.example.maps123.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnouncementDao {
    @Query("SELECT * FROM cached_announcements ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AnnouncementCacheEntity>>

    @Query("SELECT * FROM cached_announcements WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<AnnouncementCacheEntity?>

    @Query("SELECT * FROM cached_announcements ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AnnouncementCacheEntity>

    @Query("SELECT * FROM cached_announcements WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AnnouncementCacheEntity?

    @Query("SELECT MAX(timestamp) FROM cached_announcements")
    suspend fun getLatestTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM cached_announcements WHERE authorUid = :authorUid")
    suspend fun getLatestTimestampByAuthor(authorUid: String): Long?

    @Query("SELECT COUNT(*) FROM cached_announcements")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AnnouncementCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AnnouncementCacheEntity>)

    @Query("DELETE FROM cached_announcements WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_announcements")
    suspend fun clearAll()

    @Query("DELETE FROM cached_announcements WHERE timestamp >= :minTimestamp AND id NOT IN (:keepIds)")
    suspend fun deleteRecentMissing(minTimestamp: Long, keepIds: List<String>)
}
