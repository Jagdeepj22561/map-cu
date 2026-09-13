package com.example.maps123.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import com.example.shared.model.Comment
import org.json.JSONObject

@Entity(tableName = "cached_announcements")
data class AnnouncementCacheEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val imageUrl: String? = null,
    val timestamp: Long,
    val author: String,
    val authorUid: String? = null,
    val authorProfilePicUrl: String? = null,
    val type: String,
    val likesJson: String = "{}",
    val commentsJson: String = "{}",
    val shareCount: Int = 0,
    val viewCount: Int = 0,
    val itemName: String? = null,
    val place: String? = null,
    val time: String? = null,
    val reward: String? = null,
    val eventVenue: String? = null,
    val eventTime: String? = null,
    val eventPurpose: String? = null,
    val eventDlType: String? = null,
    val eventMode: String? = null,
    val eventMaxMembers: Int? = null,
    val eventDepartments: String? = null,
    val eventLink: String? = null,
    val eventCategory: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)

fun AnnouncementCacheEntity.toAnnouncement(): Announcement {
    val typeValue = runCatching { AnnouncementType.valueOf(type) }.getOrElse {
        when (type.uppercase()) {
            "LOST & FOUND", "LOST_AND_FOUND" -> AnnouncementType.LOST_AND_FOUND
            "EVENTS", "EVENT" -> AnnouncementType.EVENT
            else -> AnnouncementType.NEWS
        }
    }
    return Announcement(
        id = id,
        title = title,
        content = content,
        imageUrl = imageUrl,
        timestamp = timestamp,
        author = author,
        authorUid = authorUid,
        authorProfilePicUrl = authorProfilePicUrl,
        type = typeValue,
        likes = likesJson.toLikesMap(),
        comments = commentsJson.toCommentsMap(),
        shareCount = shareCount,
        viewCount = viewCount,
        itemName = itemName,
        place = place,
        time = time,
        reward = reward,
        eventVenue = eventVenue,
        eventTime = eventTime,
        eventPurpose = eventPurpose,
        eventDlType = eventDlType,
        eventMode = eventMode,
        eventMaxMembers = eventMaxMembers,
        eventDepartments = eventDepartments,
        eventLink = eventLink,
        eventCategory = eventCategory
    )
}

fun Announcement.toCacheEntity(cachedAt: Long = System.currentTimeMillis()): AnnouncementCacheEntity =
    AnnouncementCacheEntity(
        id = id,
        title = title,
        content = content,
        imageUrl = imageUrl,
        timestamp = timestamp,
        author = author,
        authorUid = authorUid,
        authorProfilePicUrl = authorProfilePicUrl,
        type = type.name,
        likesJson = likes.toLikesJson(),
        commentsJson = comments.toCommentsJson(),
        shareCount = shareCount,
        viewCount = viewCount,
        itemName = itemName,
        place = place,
        time = time,
        reward = reward,
        eventVenue = eventVenue,
        eventTime = eventTime,
        eventPurpose = eventPurpose,
        eventDlType = eventDlType,
        eventMode = eventMode,
        eventMaxMembers = eventMaxMembers,
        eventDepartments = eventDepartments,
        eventLink = eventLink,
        eventCategory = eventCategory,
        cachedAt = cachedAt
    )

private fun String.toLikesMap(): Map<String, Boolean> = runCatching {
    val json = JSONObject(this)
    buildMap {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, json.optBoolean(key, true))
        }
    }
}.getOrDefault(emptyMap())

private fun String.toCommentsMap(): Map<String, Comment> = runCatching {
    val json = JSONObject(this)
    buildMap {
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val item = json.optJSONObject(id)
            if (item != null) {
                put(id, Comment(id, item.optString("userId"), item.optString("userName"), item.optString("userProfilePic").ifBlank { null }, item.optString("text"), item.optLong("timestamp")))
            }
        }
    }
}.getOrDefault(emptyMap())

private fun Map<String, Boolean>.toLikesJson() = JSONObject(this).toString()
private fun Map<String, Comment>.toCommentsJson() = JSONObject().also { json ->
    forEach { (id, comment) -> json.put(id, JSONObject().put("userId", comment.userId).put("userName", comment.userName).put("userProfilePic", comment.userProfilePic).put("text", comment.text).put("timestamp", comment.timestamp)) }
}.toString()
