package com.example.shared.model

import com.example.shared.GeoPoint

data class PureUser(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val year: String = "",
    val semester: String = "",
    val course: String = "",
    val dob: String = "",
    val profilePicUrl: String = "",
    val lastUpdated: Long = 0L,
    val location: GeoPoint? = null
)
