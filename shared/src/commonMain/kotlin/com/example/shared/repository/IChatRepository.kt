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

expect class ChatRepository(): IChatRepository
