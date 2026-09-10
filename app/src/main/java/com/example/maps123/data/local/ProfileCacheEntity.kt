package com.example.maps123.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_cache")
data class ProfileCacheEntity(
    @PrimaryKey val uid: String,
    val remoteUrl: String,
    val localPath: String,
    val updatedAt: Long
)
