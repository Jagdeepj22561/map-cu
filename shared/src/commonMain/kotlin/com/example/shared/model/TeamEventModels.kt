package com.example.shared.model

data class TeamEvent(
    val announcementId: String,
    val title: String,
    val description: String,
    val maxMembers: Int? = null,
    val currentMembers: Int = 0
)

data class TeamRequirement(
    val id: String,
    val announcementId: String,
    val authorId: String,
    val content: String,
    val membersNeeded: Int,
    val createdAt: Long
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
