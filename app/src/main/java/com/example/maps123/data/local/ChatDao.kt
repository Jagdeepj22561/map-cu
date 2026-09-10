package com.example.maps123.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Chat List
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats")
    suspend fun getAllChatsList(): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChat(chatId: String): ChatEntity?

    @Query("UPDATE chats SET unreadCount = 0, lastSeenTimestamp = :lastSeenTimestamp WHERE chatId = :chatId")
    suspend fun markChatAsRead(chatId: String, lastSeenTimestamp: Long)

    @Query("SELECT SUM(unreadCount) FROM chats")
    fun getTotalUnreadCount(): Flow<Int?>

    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE chatId = :chatId")
    suspend fun incrementUnreadCount(chatId: String)

    @Query("UPDATE chats SET unreadCount = :count WHERE chatId = :chatId")
    suspend fun setUnreadCount(chatId: String, count: Int)

    @Query("UPDATE chats SET isBlocked = :blocked WHERE friendUid = :friendUid")
    suspend fun setBlocked(friendUid: String, blocked: Boolean)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChatMessages(chatId: String)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    // Messages
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(chatId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isSynced = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSyncedMessage(chatId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND senderId != :currentUid AND isRead = 0 ORDER BY timestamp ASC")
    suspend fun getUnreadIncomingMessages(chatId: String, currentUid: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET isSynced = :isSynced WHERE messageId = :messageId")
    suspend fun updateMessageSyncStatus(messageId: String, isSynced: Boolean)

    @Query("UPDATE messages SET isRead = :isRead WHERE messageId = :messageId")
    suspend fun updateMessageReadStatus(messageId: String, isRead: Boolean)

    @Query("UPDATE messages SET content = :content WHERE messageId = :messageId")
    suspend fun updateMessageContent(messageId: String, content: String)

    @Query("DELETE FROM messages WHERE messageId IN (:messageIds)")
    suspend fun deleteMessages(messageIds: List<String>)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    // Friend Requests
    @Query("SELECT * FROM friend_requests WHERE status = 'PENDING'")
    fun getPendingRequests(): Flow<List<FriendRequestEntity>>

    @Query("SELECT COUNT(*) FROM friend_requests WHERE status = 'PENDING'")
    fun getPendingRequestCount(): Flow<Int>

    @Query("SELECT * FROM friend_requests")
    suspend fun getAllRequestsList(): List<FriendRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: FriendRequestEntity)
    
    @Query("DELETE FROM friend_requests WHERE requestId = :senderUid")
    suspend fun deleteRequest(senderUid: String)

    @Query("DELETE FROM friend_requests")
    suspend fun deleteAllRequests()

    @Query("""
    UPDATE chats 
    SET lastMessage = :lastMessage, 
        lastMessageTime = :lastMessageTime 
    WHERE chatId = :chatId
""")
    suspend fun updateLastMessage(
        chatId: String,
        lastMessage: String,
        lastMessageTime: Long
    )

    @Query("SELECT * FROM messages WHERE chatId = :chatId")
    suspend fun getMessagesOnce(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages")
    suspend fun getAllMessages(): List<MessageEntity>
}
