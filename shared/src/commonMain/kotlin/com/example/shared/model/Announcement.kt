package com.example.shared.model

enum class AnnouncementType {
    NEWS,
    LOST_AND_FOUND,
    EVENT
}

data class Comment(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfilePic: String? = null,
    val text: String = "",
    val timestamp: Long = 0
)

data class Announcement(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = 0,
    val author: String = "",
    val authorUid: String? = null,
    val authorProfilePicUrl: String? = null,
    val type: AnnouncementType = AnnouncementType.NEWS,
    val likes: Map<String, Boolean> = emptyMap(),
    val comments: Map<String, Comment> = emptyMap(),
    val shareCount: Int = 0,
    val viewCount: Int = 0,
    // Lost & Found specific fields
    val itemName: String? = null,
    val place: String? = null,
    val time: String? = null,
    val reward: String? = null,
    // Event specific fields
    val eventVenue: String? = null,
    val eventTime: String? = null,
    val eventPurpose: String? = null,
    val eventDlType: String? = null,
    val eventMode: String? = null, // "Solo" or "Team"
    val eventMaxMembers: Int? = null,
    val eventDepartments: String? = null,
    val eventLink: String? = null,
    val eventCategory: String? = null
)

fun buildAnnouncementId(authorUid: String?, timestamp: Long): String {
    val normalizedUid = authorUid
        ?.trim()
        ?.ifBlank { null }
        ?.map { char ->
            if (char.isLetterOrDigit()) char else '-'
        }
        ?.joinToString(separator = "")
        ?.trim('-')
        ?.ifBlank { null }
        ?: "user"
    return "post-$normalizedUid-$timestamp"
}
