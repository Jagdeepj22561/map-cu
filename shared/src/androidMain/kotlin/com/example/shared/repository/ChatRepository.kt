package com.example.shared.repository

import com.example.shared.model.PureChat
import com.example.shared.model.PureUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android chat repository.
 *
 * Firebase Firestore was intentionally removed from the shared data layer.
 * Chat data is being migrated to Supabase; keeping the repository dependency-free
 * from Firebase allows the shared KMP module to compile without Firestore.
 */
actual class ChatRepository : IChatRepository {
    private val _allChats = MutableStateFlow<List<PureChat>>(emptyList())

    override val allChats: Flow<List<PureChat>> = _allChats.asStateFlow()

    override suspend fun getFriends(): List<PureUser> = emptyList()

    override suspend fun getNearbyUsers(lat: Double, lng: Double): List<PureUser> = emptyList()

    override suspend fun sendFriendRequest(email: String) {
        // Friend discovery/request flow is handled by the Supabase repositories.
    }

    override suspend fun createChatForFriend(friendUid: String, friendName: String): String {
        require(friendUid.isNotBlank()) { "Friend UID cannot be empty" }
        return "${getCurrentUserUid() ?: "user"}_$friendUid"
    }

    override suspend fun toggleGhostMode(isGhostMode: Boolean) {
        // Handled by the Supabase profile repository.
    }

    override fun listenToUserChats() {
        // Supabase Realtime will own chat synchronization.
    }

    override fun startSync() {
        listenToUserChats()
    }

    fun stopSync() {
        // No Firebase listener to remove.
    }

    override suspend fun getChatId(myUid: String, friendUid: String): String =
        if (myUid < friendUid) "${myUid}_$friendUid" else "${friendUid}_$myUid"

    override fun getCurrentUserUid(): String? = null
}
