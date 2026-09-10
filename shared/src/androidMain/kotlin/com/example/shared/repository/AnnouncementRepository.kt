package com.example.shared.repository

import android.util.Log
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

actual class AnnouncementRepository : IAnnouncementRepository {
    private val db = FirebaseFirestore.getInstance()
    private val announcements = db.collection("announcements")
    private val reports = db.collection("reports")

    override fun getAnnouncementsFlow(): Flow<List<Announcement>> = callbackFlow {
        val registration = announcements.orderBy("timestamp").limitToLast(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AnnouncementRepo", "Firestore error: ${error.message}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toAnnouncementOrNull() }
                    ?.sortedByDescending { it.timestamp }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getAnnouncement(id: String): Announcement? =
        announcements.document(id).get().await().toAnnouncementOrNull()

    override fun getAnnouncementFlow(id: String): Flow<Announcement?> = callbackFlow {
        val registration = announcements.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AnnouncementRepo", "Firestore error: ${error.message}")
                return@addSnapshotListener
            }
            trySend(snapshot?.toAnnouncementOrNull())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun createAnnouncement(announcement: Announcement) {
        announcements.document(announcement.id).set(announcement.toFirestoreMap()).await()
    }

    override suspend fun incrementViewCount(announcementId: String) {
        announcements.document(announcementId).update("viewCount", FieldValue.increment(1)).await()
    }

    override suspend fun reportAnnouncement(announcementId: String, reporterUid: String, reason: String) {
        val authorId = getAnnouncement(announcementId)?.authorUid ?: "unknown"
        reports.document().set(
            mapOf(
                "announcementId" to announcementId,
                "reporterUid" to reporterUid,
                "authorId" to authorId,
                "reason" to reason,
                "timestamp" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun deleteAnnouncement(announcementId: String) {
        announcements.document(announcementId).delete().await()
    }
}

private fun Announcement.toFirestoreMap(): Map<String, Any?> = buildMap {
    put("id", id)
    put("title", title)
    put("content", content)
    put("timestamp", timestamp)
    put("author", author)
    put("authorUid", authorUid)
    put("authorProfilePicUrl", authorProfilePicUrl)
    put("type", type.name)
    put("shareCount", shareCount)
    put("viewCount", viewCount)
    put("itemName", itemName)
    put("place", place)
    put("time", time)
    put("reward", reward)
    put("eventVenue", eventVenue)
    put("eventTime", eventTime)
    put("eventPurpose", eventPurpose)
    put("eventDlType", eventDlType)
    put("eventMode", eventMode)
    put("eventMaxMembers", eventMaxMembers)
    put("eventDepartments", eventDepartments)
    put("eventLink", eventLink)
    if (!imageUrl.isNullOrBlank()) put("imageUrl", imageUrl)
}

private fun com.google.firebase.firestore.DocumentSnapshot.toAnnouncementOrNull(): Announcement? {
    val data = data ?: return null
    val typeName = data["type"]?.toString().orEmpty()
    val type = runCatching { AnnouncementType.valueOf(typeName) }.getOrElse {
        when (typeName.uppercase()) {
            "LOST & FOUND", "LOST_AND_FOUND" -> AnnouncementType.LOST_AND_FOUND
            "EVENTS", "EVENT" -> AnnouncementType.EVENT
            else -> AnnouncementType.NEWS
        }
    }
    return Announcement(
        id = (data["id"] as? String).orEmpty().ifBlank { id },
        title = data["title"] as? String ?: "",
        content = data["content"] as? String ?: "",
        imageUrl = data["imageUrl"] as? String,
        timestamp = data["timestamp"].toLongCompat(),
        author = data["author"] as? String ?: "",
        authorUid = data["authorUid"] as? String,
        authorProfilePicUrl = data["authorProfilePicUrl"] as? String,
        type = type,
        likes = emptyMap(),
        comments = emptyMap(),
        shareCount = data["shareCount"].toIntCompat(),
        viewCount = data["viewCount"].toIntCompat(),
        itemName = data["itemName"] as? String,
        place = data["place"] as? String,
        time = data["time"] as? String,
        reward = data["reward"] as? String,
        eventVenue = data["eventVenue"] as? String,
        eventTime = data["eventTime"] as? String,
        eventPurpose = data["eventPurpose"] as? String,
        eventDlType = data["eventDlType"] as? String,
        eventMode = data["eventMode"] as? String,
        eventMaxMembers = data["eventMaxMembers"].toIntCompat().takeIf { it > 0 },
        eventDepartments = data["eventDepartments"] as? String,
        eventLink = data["eventLink"] as? String
    )
}

private fun Any?.toLongCompat(): Long = when (this) {
    is Long -> this
    is Int -> toLong()
    is Double -> toLong()
    is Float -> toLong()
    is Number -> toLong()
    is String -> toLongOrNull() ?: 0L
    else -> 0L
}

private fun Any?.toIntCompat(): Int = when (this) {
    is Int -> this
    is Long -> toInt()
    is Double -> toInt()
    is Float -> toInt()
    is Number -> toInt()
    is String -> toIntOrNull() ?: 0
    else -> 0
}

private fun Any?.toBooleanCompat(): Boolean = when (this) {
    is Boolean -> this
    is Number -> toLong() != 0L
    is String -> equals("true", ignoreCase = true) || this == "1"
    else -> false
}
