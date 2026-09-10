package com.example.shared.repository

import com.example.shared.model.PureChat
import com.example.shared.model.PureUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class ChatRepository : IChatRepository {
    private val db = FirebaseFirestore.getInstance()
    private val _allChats = MutableStateFlow<List<PureChat>>(emptyList())
    private var chatsListener: ListenerRegistration? = null
    private val friendDetailCache = mutableSetOf<String>()

    override val allChats: Flow<List<PureChat>> = _allChats.asStateFlow()

    override suspend fun getFriends(): List<PureUser> = emptyList()

    override suspend fun getNearbyUsers(lat: Double, lng: Double): List<PureUser> = emptyList()

    override suspend fun sendFriendRequest(email: String) {}

    override suspend fun createChatForFriend(friendUid: String, friendName: String): String {
        return "${getCurrentUserUid()}_$friendUid"
    }

    override suspend fun toggleGhostMode(isGhostMode: Boolean) {}

    override fun listenToUserChats() {
        val uid = getCurrentUserUid() ?: return
        if (chatsListener != null) return // Prevent duplicate listeners

        chatsListener = db.collection("users").document(uid).collection("chats")
            .addSnapshotListener { snapshot, _ ->
                val chats = snapshot?.documents?.map { doc ->
                    val data = doc.data.orEmpty()
                    val friendUid = data["friendUid"]?.toString()
                        ?: doc.id.split("_").firstOrNull { it != uid }
                        ?: ""
                    PureChat(
                        chatId = doc.id,
                        friendUid = friendUid,
                        friendName = "User $friendUid",
                        lastMessage = data["lastMessage"]?.toString() ?: "",
                        lastMessageTime = data["lastMessageTime"].toLongCompat(),
                        unreadCount = 0
                    )
                }?.sortedByDescending { it.lastMessageTime } ?: emptyList()
                
                _allChats.value = chats
                
                // Only fetch details if we haven't already or if name is still default
                chats.forEach { chat ->
                    if (!friendDetailCache.contains(chat.friendUid)) {
                        fetchFriendDetails(chat.friendUid)
                    }
                }
            }
    }

    private fun fetchFriendDetails(friendUid: String) {
        if (friendUid.isBlank()) return
        friendDetailCache.add(friendUid)
        db.collection("users").document(friendUid).get().addOnSuccessListener { snapshot ->
            val name = snapshot.getString("name") ?: "User $friendUid"
            val pic = snapshot.getString("profilePicUrl")
            _allChats.value = _allChats.value.map { chat ->
                if (chat.friendUid == friendUid) {
                    chat.copy(friendName = name, friendProfilePicUrl = pic)
                } else chat
            }
        }.addOnFailureListener {
            friendDetailCache.remove(friendUid)
        }
    }

    override fun startSync() {
        listenToUserChats()
    }

    fun stopSync() {
        chatsListener?.remove()
        chatsListener = null
        friendDetailCache.clear()
    }

    override suspend fun getChatId(myUid: String, friendUid: String): String =
        if (myUid < friendUid) "${myUid}_$friendUid" else "${friendUid}_$myUid"

    // The Android application owns the Supabase session; this legacy shared
    // implementation is no longer used for authenticated chat operations.
    override fun getCurrentUserUid(): String? = null
}

private fun Any?.toLongCompat(): Long = when (this) {
    is Long -> this
    is Int -> toLong()
    is Double -> toLong()
    is Float -> toLong()
    is Number -> toLong()
    is String -> toLongOrNull() ?: 0L
    else -> 0L
}
