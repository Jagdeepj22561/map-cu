package com.example.maps123.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.maps123.data.local.*
import com.example.maps123.data.supabase.SupabaseProvider
import com.example.shared.GeoPoint
import com.example.shared.data.EventNotificationClient
import com.example.shared.model.PureChat
import com.example.shared.model.PureUser
import com.example.shared.repository.IChatRepository
import com.example.shared.repository.directChatId
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

data class ChatRequestState(val status: String, val incoming: Boolean)

/** Supabase stores chat metadata; the custom server transports messages and Room stores history. */
class ChatRepository(private val context: Context) : IChatRepository {
    private val customChat = CustomChatClient(context.applicationContext)

    private val dao
        get() = AppDatabase.getInstance(context.applicationContext).chatDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var syncJob: Job? = null
    private var lastMessageTime = 0L
    private var lastReadUpdate = 0L

    override val allChats: Flow<List<PureChat>>
        get() = dao.getAllChats().map { it.map(ChatEntity::toPureChat) }

    override fun getCurrentUserUid(): String? =
        SupabaseProvider.client.auth.currentUserOrNull()?.id

    private suspend fun uid(): String {
        SupabaseProvider.client.auth.awaitInitialization()

        return SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: error("Not logged in")
    }

    fun getMessages(chatId: String): Flow<List<MessageEntity>> =
        dao.getMessages(chatId)

    override suspend fun getFriends(): List<PureUser> =
        getFriendsImplementation().map(UserEntity::toPureUser)

    suspend fun isFriend(otherUid: String): Boolean =
        getFriendsImplementation().any { it.uid == otherUid }

    suspend fun getFriendsImplementation(
        forceRefresh: Boolean = false
    ): List<UserEntity> {
        val ids = SupabaseProvider.client
            .from("friendships")
            .select {
                filter { eq("user_id", uid()) }
            }
            .decodeList<FriendshipRow>()
            .map { it.friendId }

        if (ids.isEmpty()) return emptyList()

        return SupabaseProvider.client
            .from("profiles")
            .select { filter { isIn("id", ids.distinct()) } }
            .decodeList<ChatProfileRow>()
            .map(ChatProfileRow::toUser)
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun getNearbyUsers(
        lat: Double,
        lng: Double
    ): List<PureUser> =
        getNearbyUsersImplementation(userLat = lat, userLng = lng).map { user ->
            val dist = if (lat != 0.0 && lng != 0.0 && user.latitude != 0.0 && user.longitude != 0.0) {
                com.example.shared.utils.GeoUtils.distanceMeters(GeoPoint(lat, lng), GeoPoint(user.latitude, user.longitude))
            } else {
                null
            }
            user.toPureUser(distanceMeters = dist)
        }

    suspend fun getNearbyUsersImplementation(
        forceRefresh: Boolean = false,
        userLat: Double = 0.0,
        userLng: Double = 0.0
    ): List<UserEntity> {
        val currentUid = getCurrentUserUid()
        val users = SupabaseProvider.client
            .from("profiles")
            .select {
                order("last_updated", Order.DESCENDING)
                limit(50)
            }
            .decodeList<ChatProfileRow>()
            .map(ChatProfileRow::toUser)
            .filter { it.uid != currentUid && !it.ghostMode }

        return if (userLat != 0.0 && userLng != 0.0) {
            users.sortedWith(
                compareBy<UserEntity> { u ->
                    if (u.latitude != 0.0 && u.longitude != 0.0) {
                        com.example.shared.utils.GeoUtils.distanceMeters(GeoPoint(userLat, userLng), GeoPoint(u.latitude, u.longitude))
                    } else {
                        Double.MAX_VALUE
                    }
                }.thenByDescending { it.lastUpdated }
            )
        } else {
            users
        }
    }

    fun getNearbyUsersLastRefreshTime() = 0L
    fun getNearbyUsersRefreshRemainingMs() = 0L

    override suspend fun sendFriendRequest(email: String) {
        val mine = uid()
        val target = SupabaseProvider.client
            .from("profiles")
            .select {
                filter { eq("email", email.trim()) }
                limit(1)
            }
            .decodeList<ChatProfileRow>()
            .firstOrNull()
            ?: error("Friend request failed: User with email '$email' not found.")

        require(target.id != mine) { "You cannot send a friend request to yourself." }

        // Check for existing pending friend requests in both directions
        val outgoingRequest = SupabaseProvider.client
            .from("friend_requests")
            .select {
                filter {
                    eq("sender_id", mine)
                    eq("receiver_id", target.id)
                    eq("status", "pending")
                }
                limit(1)
            }
            .decodeList<FriendRequestRow>()
            .isNotEmpty()

        val incomingRequest = SupabaseProvider.client
            .from("friend_requests")
            .select {
                filter {
                    eq("sender_id", target.id)
                    eq("receiver_id", mine)
                    eq("status", "pending")
                }
                limit(1)
            }
            .decodeList<FriendRequestRow>()
            .isNotEmpty()

        if (outgoingRequest) {
            error("Friend request already sent.")
        }

        if (incomingRequest) {
            error("This user has already sent you a friend request.")
        }

        val exists = SupabaseProvider.client
            .from("friendships")
            .select {
                filter {
                    eq("user_id", mine)
                    eq("friend_id", target.id)
                }
            }
            .decodeList<FriendshipRow>()
            .isNotEmpty()

        if (exists) error("You are already friends with this user.")

        SupabaseProvider.client
            .from("friend_requests")
            .insert(FriendRequestInsert(mine, target.id))

        val createdRequest = SupabaseProvider.client
            .from("friend_requests")
            .select {
                filter {
                    eq("sender_id", mine)
                    eq("receiver_id", target.id)
                    eq("status", "pending")
                }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<FriendRequestRow>()
            .firstOrNull()
        if (createdRequest != null) {
            EventNotificationClient.dispatch("friend_request_created", createdRequest.id)
        }
    }

    suspend fun refreshFriendRequestsNow(forceRefresh: Boolean = false) {
        val mine = uid()
        val rows = SupabaseProvider.client
            .from("friend_requests")
            .select {
                filter {
                    eq("receiver_id", mine)
                    eq("status", "pending")
                }
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<FriendRequestRow>()

        val requests = rows.mapNotNull { r ->
            user(r.senderId)?.let {
                FriendRequestEntity(
                    r.id,
                    it.uid,
                    it.name,
                    it.email,
                    mine,
                    "PENDING",
                    r.time()
                )
            }
        }

        requests.forEach { dao.insertRequest(it) }
        val keep = requests.map { it.requestId }.toSet()
        dao.getAllRequestsList()
            .filterNot { it.requestId in keep }
            .forEach { dao.deleteRequest(it.requestId) }
    }

    suspend fun acceptFriendRequest(senderUid: String): UserEntity? {
        val mine = uid()
        val request = SupabaseProvider.client
            .from("friend_requests")
            .select {
                filter {
                    eq("sender_id", senderUid)
                    eq("receiver_id", mine)
                    eq("status", "pending")
                }
                limit(1)
            }
            .decodeList<FriendRequestRow>()
            .firstOrNull() ?: return null

        SupabaseProvider.client
            .from("friend_requests")
            .update({ set("status", "accepted") }) {
                filter { eq("id", request.id) }
            }

        SupabaseProvider.client
            .from("friendships")
            .upsert(
                listOf(
                    FriendshipRow(mine, senderUid),
                    FriendshipRow(senderUid, mine)
                )
            )

        dao.deleteRequest(request.id)
        EventNotificationClient.dispatch("friend_request_accepted", request.id)
        return user(senderUid)
    }

    suspend fun rejectFriendRequest(senderUid: String) {
        val mine = uid()
        val request = SupabaseProvider.client
            .from("friend_requests")
            .select {
                filter {
                    eq("sender_id", senderUid)
                    eq("receiver_id", mine)
                    eq("status", "pending")
                }
                limit(1)
            }
            .decodeList<FriendRequestRow>()
            .firstOrNull()
        if (request == null) {
            refreshFriendRequestsNow(true)
            return
        }
        SupabaseProvider.client
            .from("friend_requests")
            .update({ set("status", "rejected") }) {
                filter { eq("id", request.id) }
            }
        EventNotificationClient.dispatch("friend_request_rejected", request.id)
        refreshFriendRequestsNow(true)
    }

    suspend fun unfriendUser(friendUid: String) {
        val mine = uid()
        SupabaseProvider.client.from("friendships").delete {
            filter {
                eq("user_id", mine)
                eq("friend_id", friendUid)
            }
        }
        SupabaseProvider.client.from("friendships").delete {
            filter {
                eq("user_id", friendUid)
                eq("friend_id", mine)
            }
        }
    }

    override suspend fun getChatId(myUid: String, friendUid: String): String =
        directChatId(myUid, friendUid)

    override suspend fun createChatForFriend(
        friendUid: String,
        friendName: String
    ): String {
        val mine = uid()
        val id = getChatId(mine, friendUid)

        val friend = user(friendUid)
        val existingChat = dao.getChat(id)

        dao.insertChat(
            ChatEntity(
                id,
                friendUid,
                friend?.name ?: friendName,
                friend?.profilePicUrl,
                existingChat?.lastMessage ?: "New chat started",
                existingChat?.lastMessageTime ?: System.currentTimeMillis(),
                existingChat?.lastSeenTimestamp ?: 0L,
                existingChat?.unreadCount ?: 0,
                existingChat?.isBlocked ?: false
            )
        )

        return id
    }

    override suspend fun toggleGhostMode(isGhostMode: Boolean) =
        updateGhostMode(isGhostMode)

    suspend fun updateGhostMode(enabled: Boolean) {
        SupabaseProvider.client.from("profiles").update({
            set("ghost_mode", enabled)
            set("last_updated", System.currentTimeMillis())
        }) {
            filter { eq("id", uid()) }
        }
    }

    override fun listenToUserChats() = startSync()
    fun listenToFriendRequests() = startSync()

    override fun startSync() {
        customChat.start(scope)
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (isActive) {
                runCatching { refreshFriendRequestsNow(true) }
                    .onFailure { Log.w("ChatRepository", "Friend request sync failed", it) }
                runCatching { refreshUserChatsNow(true) }
                    .onFailure { Log.w("ChatRepository", "Chat metadata sync failed", it) }
                delay(30_000)
            }
        }
    }

    fun stopSync() {
        customChat.stop()
        syncJob?.cancel()
        syncJob = null
    }

    suspend fun refreshUserChatsNow(forceRefresh: Boolean = false) {
        val mine = uid()
        val friends = getFriendsImplementation(forceRefresh)
        friends.forEach { friend ->
            val chatId = getChatId(mine, friend.uid)
            dao.getChat(chatId)?.let { existingChat ->
                dao.insertChat(existingChat.copy(friendName = friend.name, friendProfilePicUrl = friend.profilePicUrl))
            }
        }
    }

    fun setActiveChat(chatId: String?) { customChat.activeChatId = chatId }

    suspend fun sendMessage(
        chatId: String,
        content: String,
        friendUid: String,
        imageUrl: String? = null
    ) {
        val now = System.currentTimeMillis()
        check(now - lastMessageTime >= 400L) { "You're sending too fast" }
        lastMessageTime = now

        val mine = uid()
        require(chatId == getChatId(mine, friendUid)) { "Invalid conversation" }
        require(content.isNotBlank() || imageUrl != null) { "Message cannot be empty" }
        require(content.toByteArray().size <= 8000) { "Message is too long" }
        val existingChat = dao.getChat(chatId)
        check(existingChat?.isBlocked != true) { "Unblock this user to send messages" }
        val message = MessageEntity(UUID.randomUUID().toString(), chatId, mine, content, now,
            imageUrl = imageUrl, type = if (imageUrl == null) "TEXT" else "IMAGE", customTransport = true)
        val db = AppDatabase.getInstance(context.applicationContext)
        db.withTransaction {
            dao.insertMessage(message)
            dao.insertChat((existingChat ?: ChatEntity(chatId, friendUid, "Friend", lastMessage = "", lastMessageTime = 0)).copy(
                lastMessage = if (imageUrl == null) content else "Image", lastMessageTime = now))
        }
        customChat.start(scope)
    }

    fun fetchNewMessages() {
        customChat.start(scope)
    }

    fun getPendingRequestCount() = dao.getPendingRequestCount()
    fun getTotalUnreadCount() = dao.getTotalUnreadCount().map { it ?: 0 }

    suspend fun getOutgoingPendingRequestEmails(): Set<String> {
        val mine = uid()
        val outgoing = SupabaseProvider.client
            .from("friend_requests")
            .select {
                filter {
                    eq("sender_id", mine)
                    eq("status", "pending")
                }
            }
            .decodeList<FriendRequestRow>()

        val receiverIds = outgoing.map { it.receiverId }
        if (receiverIds.isEmpty()) return emptySet()

        return SupabaseProvider.client
            .from("profiles")
            .select { filter { isIn("id", receiverIds) } }
            .decodeList<ChatProfileRow>()
            .map { it.email.trim().lowercase() }
            .toSet()
    }

    /** Returns the request visible from this conversation, if any. */
    suspend fun getChatRequestState(otherUid: String): ChatRequestState? {
        val mine = uid()
        val rows = SupabaseProvider.client
            .from("friend_requests")
            .select {
                filter {
                    or {
                        and { eq("sender_id", mine); eq("receiver_id", otherUid) }
                        and { eq("sender_id", otherUid); eq("receiver_id", mine) }
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(20)
            }
            .decodeList<FriendRequestRow>()

        val incoming = rows.firstOrNull { it.senderId == otherUid && it.receiverId == mine }
        if (incoming != null && incoming.status in setOf("pending", "rejected")) {
            return ChatRequestState(incoming.status, incoming = true)
        }

        val outgoing = rows.firstOrNull { it.senderId == mine && it.receiverId == otherUid }
        if (outgoing != null && outgoing.status in setOf("pending", "rejected")) {
            return ChatRequestState(outgoing.status, incoming = false)
        }
        return null
    }

    fun clearAllListeners() {
        customChat.stop()
        customChat.activeChatId = null
        syncJob?.cancel()
        syncJob = null
    }

    suspend fun uploadImage(uri: Uri): String {
        val file = com.example.maps123.utils.ImageUtils.uriToFile(context, uri)
        return com.example.maps123.utils.ImageUtils.uploadImage(
            com.example.maps123.utils.ImageUtils.compressProfileImage(context, file)
        )
    }

    suspend fun markChatAsRead(chatId: String) {
        val now = System.currentTimeMillis()
        if (now - lastReadUpdate < 2_000L) return
        lastReadUpdate = now

        val mine = uid()
        dao.getUnreadIncomingMessages(chatId, mine).forEach { message ->
            dao.updateMessageReadStatus(message.messageId, true)
        }
        dao.markChatAsRead(chatId, now)
    }

    suspend fun clearChat(chatId: String) = dao.clearChatMessages(chatId)
    suspend fun deleteChat(chatId: String) = dao.deleteChat(chatId)

    suspend fun blockUser(friendUid: String) {
        SupabaseProvider.client.from("blocks").upsert(BlockRow(uid(), friendUid))
        dao.setBlocked(friendUid, true)
    }

    suspend fun unblockUser(friendUid: String) {
        SupabaseProvider.client.from("blocks").delete {
            filter {
                eq("user_id", uid())
                eq("blocked_user_id", friendUid)
            }
        }
        dao.setBlocked(friendUid, false)
    }

    suspend fun isUserBlocked(friendUid: String) =
        SupabaseProvider.client.from("blocks").select {
            filter {
                eq("user_id", uid())
                eq("blocked_user_id", friendUid)
            }
        }.decodeList<BlockRow>().isNotEmpty()

    suspend fun deleteMessagesLocally(messageIds: List<String>) =
        dao.deleteMessages(messageIds)

    private suspend fun user(id: String): UserEntity? =
        SupabaseProvider.client
            .from("profiles")
            .select {
                filter { eq("id", id) }
                limit(1)
            }
            .decodeList<ChatProfileRow>()
            .firstOrNull()
            ?.toUser()

    suspend fun lookupUser(uid: String): UserEntity? = user(uid)
}

@Serializable
private data class ChatProfileRow(
    val id: String,
    val email: String,
    val name: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    val year: String = "",
    val semester: String = "",
    val course: String = "",
    val university: String = "",
    val dob: String = "",
    val gender: String = "",
    @SerialName("profile_pic_url") val profilePicUrl: String = "",
    @SerialName("instagram_link") val instagramLink: String = "",
    @SerialName("snapchat_link") val snapchatLink: String = "",
    @SerialName("linkedin_link") val linkedinLink: String = "",
    @SerialName("last_updated") val lastUpdated: Long = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("ghost_mode") val ghostMode: Boolean = false
) {
    fun toUser() = UserEntity(
        uid = id,
        email = email,
        name = name,
        phoneNumber = phoneNumber,
        year = year,
        semester = semester,
        course = course,
        university = university,
        dob = dob,
        gender = gender,
        profilePicUrl = profilePicUrl,
        instagramLink = instagramLink,
        snapchatLink = snapchatLink,
        linkedinLink = linkedinLink,
        lastUpdated = lastUpdated,
        latitude = latitude,
        longitude = longitude,
        ghostMode = ghostMode
    )
}

@Serializable
private data class FriendshipRow(
    @SerialName("user_id") val userId: String,
    @SerialName("friend_id") val friendId: String
)

@Serializable
private data class FriendRequestInsert(
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String
)

@Serializable
private data class FriendRequestRow(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String
) {
    fun time() = java.time.OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
}

@Serializable
private data class BlockRow(
    @SerialName("user_id") val userId: String,
    @SerialName("blocked_user_id") val blockedUserId: String
)

private fun UserEntity.toPureUser(distanceMeters: Double? = null) = PureUser(
    uid = uid,
    email = email,
    name = name,
    phoneNumber = phoneNumber,
    year = year,
    semester = semester,
    course = course,
    dob = dob,
    profilePicUrl = profilePicUrl,
    lastUpdated = lastUpdated,
    location = if (latitude != 0.0 && longitude != 0.0) GeoPoint(latitude, longitude) else null,
    university = university,
    instagramLink = instagramLink,
    snapchatLink = snapchatLink,
    linkedinLink = linkedinLink,
    distanceMeters = distanceMeters,
    ghostMode = ghostMode
)
