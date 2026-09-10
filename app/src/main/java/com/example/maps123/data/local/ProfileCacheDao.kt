package com.example.maps123.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProfileCacheDao {

    @Query("SELECT * FROM profile_cache WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): ProfileCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProfileCacheEntity)

    @Query("DELETE FROM profile_cache WHERE uid = :uid")
    suspend fun delete(uid: String)
}
