package com.example.maps123.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages WHERE customTransport = 1 AND customFailure IS NULL AND isSynced = 0 AND senderId = :uid AND timestamp > :since ORDER BY timestamp LIMIT 50")
    suspend fun getCustomOutbox(uid: String, since: Long): List<MessageEntity>

    @Query("UPDATE messages SET customFailure = :reason WHERE messageId = :id AND customTransport = 1 AND isSynced = 0")
    suspend fun markCustomFailure(id: String, reason: String)
    // Chat List
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChat(chatId: String): ChatEntity?

    @Query("UPDATE chats SET unreadCount = 0, lastSeenTimestamp = :lastSeenTimestamp WHERE chatId = :chatId")
    suspend fun markChatAsRead(chatId: String, lastSeenTimestamp: Long)

    @Query("SELECT SUM(unreadCount) FROM chats")
    fun getTotalUnreadCount(): Flow<Int?>

    @Query("UPDATE chats SET isBlocked = :blocked WHERE friendUid = :friendUid")
    suspend fun setBlocked(friendUid: String, blocked: Boolean)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChatMessages(chatId: String)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: String)

    // Messages
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND senderId != :currentUid AND isRead = 0 ORDER BY timestamp ASC")
    suspend fun getUnreadIncomingMessages(chatId: String, currentUid: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("UPDATE messages SET isSynced = :isSynced WHERE messageId = :messageId")
    suspend fun updateMessageSyncStatus(messageId: String, isSynced: Boolean): Int

    @Query("UPDATE messages SET isRead = :isRead WHERE messageId = :messageId")
    suspend fun updateMessageReadStatus(messageId: String, isRead: Boolean)

    @Query("DELETE FROM messages WHERE messageId IN (:messageIds)")
    suspend fun deleteMessages(messageIds: List<String>)

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

}
