package com.example.maps123.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String = "",
    val email: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val year: String = "",
    val semester: String = "",
    val course: String = "",
    val university: String = "",
    val dob: String = "",
    val gender: String = "",
    val profilePicUrl: String = "",
    
    val instagramLink: String = "",
    val snapchatLink: String = "",
    val linkedinLink: String = "",

    val lastUpdated: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val ghostMode: Boolean = false
)
