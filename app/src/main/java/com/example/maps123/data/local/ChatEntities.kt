package com.example.maps123.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

import com.example.shared.model.PureChat
import com.example.shared.model.PureFriendRequest
import com.example.shared.model.PureMessage

@Entity(tableName = "chats",
    indices = [Index(value = ["chatId"], unique = true)])
data class ChatEntity(
    @PrimaryKey val chatId: String, // Usually combination of UID1_UID2 (sorted)
    val friendUid: String,
    val friendName: String,
    val friendProfilePicUrl: String? = null,
    val lastMessage: String,
    val lastMessageTime: Long,
    val lastSeenTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val isBlocked: Boolean = false
) {
    fun toPureChat() = PureChat(
        chatId = chatId,
        friendUid = friendUid,
        friendName = friendName,
        friendProfilePicUrl = friendProfilePicUrl,
        lastMessage = lastMessage,
        lastMessageTime = lastMessageTime,
        unreadCount = unreadCount
    )
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isSynced: Boolean = false,
    val imageUrl: String? = null,
    val type: String = "TEXT",
    @androidx.room.ColumnInfo(defaultValue = "0") val customTransport: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val customFailure: String? = null
) {
    fun toPureMessage() = PureMessage(
        messageId = messageId,
        chatId = chatId,
        senderId = senderId,
        content = content,
        timestamp = timestamp,
        isRead = isRead,
        imageUrl = imageUrl,
        type = type,
        deliveryLabel = if (customTransport) {
            customFailure ?: if (isSynced) "Accepted" else if (System.currentTimeMillis() - timestamp >= 72 * 60 * 60 * 1000L) "Not sent (expired)" else "Pending"
        } else null
    )
}
@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey
    var requestId: String = "",

    var senderId: String = "",
    var senderName: String = "",
    var senderEmail: String = "",
    var receiverId: String = "",
    var status: String = "", // PENDING, ACCEPTED, REJECTED

    var timestamp: Long = 0L
) {
    fun toPureFriendRequest() = PureFriendRequest(
        requestId = requestId,
        senderId = senderId,
        senderName = senderName,
        senderEmail = senderEmail,
        receiverId = receiverId,
        status = status,
        timestamp = timestamp
    )
}
