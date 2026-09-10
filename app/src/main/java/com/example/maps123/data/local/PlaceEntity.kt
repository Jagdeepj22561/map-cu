package com.example.maps123.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String
)
