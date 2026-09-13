package com.example.shared.repository

import com.example.shared.model.PureChat
import com.example.shared.model.PureUser
import kotlinx.coroutines.flow.Flow

interface IChatRepository {
    val allChats: Flow<List<PureChat>>

    suspend fun getFriends(): List<PureUser>
    suspend fun getNearbyUsers(lat: Double, lng: Double): List<PureUser>
    suspend fun sendFriendRequest(email: String)
    suspend fun createChatForFriend(friendUid: String, friendName: String): String
    suspend fun toggleGhostMode(isGhostMode: Boolean)
    
    fun listenToUserChats()
    fun startSync()
    
    suspend fun getChatId(myUid: String, friendUid: String): String
    fun getCurrentUserUid(): String?
}

expect class ChatRepository(): IChatRepository {
    override val allChats: Flow<List<PureChat>>

    override suspend fun getFriends(): List<PureUser>
    override suspend fun getNearbyUsers(lat: Double, lng: Double): List<PureUser>
    override suspend fun sendFriendRequest(email: String)
    override suspend fun createChatForFriend(friendUid: String, friendName: String): String
    override suspend fun toggleGhostMode(isGhostMode: Boolean)
    
    override fun listenToUserChats()
    override fun startSync()
    
    override suspend fun getChatId(myUid: String, friendUid: String): String
    override fun getCurrentUserUid(): String?
}
