package com.example.maps123.data.repository

import android.content.Context
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.local.UserEntity
import com.example.maps123.data.supabase.SupabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from

class UserRepository(context: Context) {
    private val userDao by lazy { AppDatabase.getInstance(context).userDao() }

    fun getUserFlow(uid: String): Flow<UserEntity?> = userDao.getUserFlow(uid)

    suspend fun saveUser(user: UserEntity) {
        withContext(Dispatchers.IO) {
            userDao.insertUser(user)
            SupabaseProvider.client.from("profiles").upsert(user.toProfileRow())
        }
    }

    suspend fun loadUser(uid: String): UserEntity? = withContext(Dispatchers.IO) {
        userDao.getUser(uid)?.let { return@withContext it }

        try {
            val remote = fetchRemoteUser(uid)
            if (remote != null) {
                userDao.insertUser(remote)
                return@withContext remote
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun getUser(uid: String): UserEntity? = loadUser(uid)

    suspend fun syncUser(uid: String): UserEntity? = withContext(Dispatchers.IO) {
        val local = userDao.getUser(uid)
        if (local != null && !local.isUserRefreshStale()) {
            return@withContext local
        }

        try {
            val remote = fetchRemoteUser(uid)
            if (remote != null) {
                userDao.insertUser(remote)
                return@withContext remote
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        local
    }

    suspend fun refreshUser(uid: String): UserEntity? = withContext(Dispatchers.IO) {
        try {
            val remote = fetchRemoteUser(uid)
            if (remote != null) {
                userDao.insertUser(remote)
            }
            remote
        } catch (e: Exception) {
            e.printStackTrace()
            userDao.getUser(uid)
        }
    }

    suspend fun updateUserLocation(uid: String, lat: Double, lng: Double) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val localUser = userDao.getUser(uid)
            if (!AndroidUserQuotaGuards.shouldWriteLocation(uid, lat, lng, now)) {
                localUser?.let {
                    userDao.insertUser(it.copy(latitude = lat, longitude = lng))
                }
                return@withContext
            }

            try {
                val updated = (localUser ?: fetchRemoteUser(uid))?.copy(
                    latitude = lat,
                    longitude = lng,
                    lastUpdated = now
                ) ?: return@withContext
                SupabaseProvider.client.from("profiles").upsert(updated.toProfileRow())
                AndroidUserQuotaGuards.recordLocationWrite(uid, lat, lng, now)

                userDao.insertUser(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun updateUserProfilePic(uid: String, url: String) {
        withContext(Dispatchers.IO) {
            try {
                val updated = (userDao.getUser(uid) ?: fetchRemoteUser(uid))
                    ?.copy(profilePicUrl = url)
                    ?: throw IllegalStateException("User profile not found")
                SupabaseProvider.client.from("profiles").upsert(updated.toProfileRow())
                userDao.insertUser(updated)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun updateUserProfile(user: UserEntity) {
        withContext(Dispatchers.IO) {
            val lastUpdated = System.currentTimeMillis()
            val updatedUser = user.copy(lastUpdated = lastUpdated)
            userDao.insertUser(updatedUser)
            SupabaseProvider.client.from("profiles").upsert(updatedUser.toProfileRow())
        }
    }

    private suspend fun fetchRemoteUser(uid: String): UserEntity? =
        SupabaseProvider.client.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeList<ProfileRow>()
            .firstOrNull()
            ?.toUserEntity()
}

@Serializable
private data class ProfileRow(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("phone_number") val phoneNumber: String,
    val year: String,
    val semester: String,
    val course: String,
    val university: String = "",
    val dob: String,
    val gender: String = "",
    @SerialName("profile_pic_url") val profilePicUrl: String,
    @SerialName("instagram_link") val instagramLink: String,
    @SerialName("snapchat_link") val snapchatLink: String,
    @SerialName("linkedin_link") val linkedinLink: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("ghost_mode") val ghostMode: Boolean,
    @SerialName("last_updated") val lastUpdated: Long
)

private fun UserEntity.toProfileRow() = ProfileRow(
    id = uid, email = email, name = name, phoneNumber = phoneNumber, year = year,
    semester = semester, course = course, university = university, dob = dob, gender = gender, profilePicUrl = profilePicUrl,
    instagramLink = instagramLink, snapchatLink = snapchatLink, linkedinLink = linkedinLink,
    latitude = latitude, longitude = longitude, ghostMode = ghostMode, lastUpdated = lastUpdated
)

private fun ProfileRow.toUserEntity(): UserEntity {
    return UserEntity(
        uid = id, email = email, name = name, phoneNumber = phoneNumber, year = year,
        semester = semester, course = course, university = university, dob = dob, gender = gender, profilePicUrl = profilePicUrl,
        instagramLink = instagramLink, snapchatLink = snapchatLink, linkedinLink = linkedinLink,
        lastUpdated = lastUpdated, latitude = latitude, longitude = longitude, ghostMode = ghostMode
    )
}

private object AndroidUserQuotaGuards {
    private const val userRefreshFreshMs = 2 * 60 * 1000L
    private const val minLocationSyncIntervalMs = 60 * 1000L
    private const val forcedLocationSyncIntervalMs = 5 * 60 * 1000L
    private const val minLocationDeltaMeters = 25.0
    private const val ignoreTinyLocationDeltaMeters = 8.0

    private val lastLocationWrites = mutableMapOf<String, LocationSyncState>()

    fun shouldWriteLocation(uid: String, lat: Double, lng: Double, now: Long): Boolean {
        synchronized(lastLocationWrites) {
            val previous = lastLocationWrites[uid] ?: return true
            val ageMs = now - previous.timestamp
            val movedMeters = distanceMeters(previous.lat, previous.lng, lat, lng)

            if (ageMs >= forcedLocationSyncIntervalMs) return true
            if (movedMeters < ignoreTinyLocationDeltaMeters) return false
            if (ageMs < minLocationSyncIntervalMs && movedMeters < minLocationDeltaMeters) return false
            return true
        }
    }

    fun recordLocationWrite(uid: String, lat: Double, lng: Double, timestamp: Long) {
        synchronized(lastLocationWrites) {
            lastLocationWrites[uid] = LocationSyncState(lat, lng, timestamp)
        }
    }

    fun isUserRefreshStale(lastUpdated: Long): Boolean {
        if (lastUpdated <= 0L) return true
        return System.currentTimeMillis() - lastUpdated > userRefreshFreshMs
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val latDelta = Math.toRadians(lat2 - lat1)
        val lngDelta = Math.toRadians(lng2 - lng1)
        val originLat = Math.toRadians(lat1)
        val targetLat = Math.toRadians(lat2)
        val haversine = kotlin.math.sin(latDelta / 2) * kotlin.math.sin(latDelta / 2) +
            kotlin.math.cos(originLat) * kotlin.math.cos(targetLat) *
            kotlin.math.sin(lngDelta / 2) * kotlin.math.sin(lngDelta / 2)
        return earthRadiusMeters * 2 * kotlin.math.atan2(kotlin.math.sqrt(haversine), kotlin.math.sqrt(1 - haversine))
    }

    private data class LocationSyncState(
        val lat: Double,
        val lng: Double,
        val timestamp: Long
    )
}

private fun UserEntity.isUserRefreshStale(): Boolean =
    AndroidUserQuotaGuards.isUserRefreshStale(lastUpdated)
