package com.example.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class TeamEvent(
    val announcementId: String,
    val title: String,
    val description: String,
    val maxMembers: Int? = null,
    val currentMembers: Int = 0
)

@Serializable
data class TeamRequirement(
    val id: String,
    val announcementId: String,
    val authorId: String,
    val content: String,
    val membersNeeded: Int,
    val createdAt: String
)

@Serializable
data class TeamEventInfo(
    val id: String,
    val announcementId: String,
    val ownerId: String,
    val name: String,
    val maxMembers: Int? = null,
    val members: List<TeamMemberInfo> = emptyList(),
    val createdAt: String = ""
) {
    val isFull: Boolean get() = maxMembers != null && members.size >= maxMembers
    val availableSpots: Int get() = maxMembers?.let { (it - members.size).coerceAtLeast(0) } ?: 999
    val capacityRatio: Float get() = if (maxMembers == null || maxMembers <= 0) 0f else (members.size.toFloat() / maxMembers.toFloat()).coerceIn(0f, 1f)
}

@Serializable
data class TeamMemberInfo(
    val userId: String,
    val name: String,
    val profilePicUrl: String? = null,
    val role: String = "member"
)

data class FriendSuggestion(
    val userId: String,
    val name: String,
    val email: String,
    val profilePicUrl: String? = null,
    val course: String = "",
    val year: String = "",
    val semester: String = "",
    val score: Int = 0,
    val reasons: List<String> = emptyList()
)

@Serializable
enum class EventCategory(val displayLabel: String) {
    HACKATHON("Hackathon"),
    WORKSHOP("Workshop"),
    CULTURAL("Cultural"),
    SPORTS("Sports"),
    TECH_TALK("Tech Talk"),
    SEMINAR("Seminar"),
    QUIZ("Quiz"),
    OTHER("Other");

    companion object {
        fun fromLabel(label: String?): EventCategory? =
            values().firstOrNull { it.displayLabel.equals(label, ignoreCase = true) }
        fun fromName(name: String?): EventCategory? =
            values().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

@Serializable
enum class EventStatus(val displayLabel: String) {
    UPCOMING("Upcoming"),
    LIVE("Live"),
    ENDED("Ended");

    companion object {
        fun fromEventTime(eventTime: String?): EventStatus {
            if (eventTime.isNullOrBlank()) return UPCOMING
            val trimmed = eventTime.trim().lowercase()
            return when {
                trimmed == "live" || trimmed.contains("live now") || trimmed.contains("ongoing") -> LIVE
                trimmed == "ended" || trimmed.contains("closed") || trimmed.contains("completed") -> ENDED
                trimmed == "upcoming" -> UPCOMING
                else -> UPCOMING
            }
        }
    }
}

@Serializable
data class JoinRequest(
    val id: String,
    val teamId: String,
    val requesterId: String,
    val requesterName: String,
    val requesterPicUrl: String? = null,
    val message: String = "",
    val status: String = "pending",
    val reviewedBy: String? = null,
    val createdAt: String = ""
)

@Serializable
data class EventTimelineItem(
    val id: String = "",
    val announcementId: String = "",
    val title: String = "",
    val description: String = "",
    val scheduledAt: String? = null,
    val sortOrder: Int = 0
)

@Serializable
data class EventListItem(
    val announcementId: String,
    val title: String,
    val imageUrl: String? = null,
    val eventVenue: String? = null,
    val eventTime: String? = null,
    val eventMode: String? = null,
    val eventMaxMembers: Int? = null,
    val eventCategory: String? = null,
    val status: EventStatus = EventStatus.UPCOMING,
    val participantCount: Int = 0,
    val teamCount: Int = 0
)
