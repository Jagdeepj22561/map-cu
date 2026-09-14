package com.example.shared.model

data class PureChat(
    val chatId: String,
    val friendUid: String,
    val friendName: String,
    val friendProfilePicUrl: String? = null,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0
)

data class PureMessage(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val imageUrl: String? = null,
    val type: String = "TEXT",
    val deliveryLabel: String? = null
)

data class PureFriendRequest(
    val requestId: String,
    val senderId: String,
    val senderName: String,
    val senderEmail: String,
    val receiverId: String,
    val status: String,
    val timestamp: Long = 0L
)
