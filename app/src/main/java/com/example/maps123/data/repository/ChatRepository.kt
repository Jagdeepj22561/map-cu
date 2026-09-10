package com.example.maps123.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.maps123.data.local.*
import com.example.maps123.data.supabase.SupabaseProvider
import com.example.shared.GeoPoint
import com.example.shared.model.PureChat
import com.example.shared.model.PureUser
import com.example.shared.repository.IChatRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Supabase is the source of truth; Room remains the offline cache. */
class ChatRepository(private val context: Context) : IChatRepository {

    private val dao
        get() = AppDatabase.getInstance(context.applicationContext).chatDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var syncJob: Job? = null
    private var activeChatId: String? = null
    private var lastMessageTime = 0L
    private var lastReadUpdate = 0L

    override val allChats: Flow<List<PureChat>> =
        dao.getAllChats().map { it.map(ChatEntity::toPureChat) }

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

        return buildList {
            ids.forEach { friendId ->
                user(friendId)?.let(::add)
            }
        }.sortedBy { it.name.lowercase() }
    }

    override suspend fun getNearbyUsers(
        lat: Double,
        lng: Double
    ): List<PureUser> =
        getNearbyUsersImplementation().map(UserEntity::toPureUser)

    suspend fun getNearbyUsersImplementation(
        forceRefresh: Boolean = false
    ): List<UserEntity> =
        SupabaseProvider.client
            .from("profiles")
            .select {
                order("last_updated", Order.DESCENDING)
                limit(5)
            }
            .decodeList<ChatProfileRow>()
            .map(ChatProfileRow::toUser)
            .filter { it.uid != getCurrentUserUid() && !it.ghostMode }

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
        return user(senderUid)
    }

    suspend fun rejectFriendRequest(senderUid: String) {
        SupabaseProvider.client
            .from("friend_requests")
            .update({ set("status", "rejected") }) {
                filter {
                    eq("sender_id", senderUid)
                    eq("receiver_id", uid())
                    eq("status", "pending")
                }
            }
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
        UUID.nameUUIDFromBytes(
            listOf(myUid, friendUid).sorted().joinToString(":").toByteArray()
        ).toString()

    override suspend fun createChatForFriend(
        friendUid: String,
        friendName: String
    ): String {
        val mine = uid()
        val id = getChatId(mine, friendUid)

        SupabaseProvider.client.postgrest.rpc(
            function = "create_direct_chat",
            parameters = buildJsonObject {
                put("p_chat_id", id)
                put("p_friend_id", friendUid)
            }
        )

        val friend = user(friendUid)
        val old = dao.getChat(id)

        dao.insertChat(
            ChatEntity(
                id,
                friendUid,
                friend?.name ?: friendName,
                friend?.profilePicUrl,
                old?.lastMessage ?: "New chat started",
                old?.lastMessageTime ?: System.currentTimeMillis(),
                old?.lastSeenTimestamp ?: 0L,
                old?.unreadCount ?: 0,
                old?.isBlocked ?: false
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
        if (syncJob?.isActive == true) return

        syncJob = scope.launch {
            runCatching {
                SupabaseProvider.client.auth.awaitInitialization()
                uid()
            }.onFailure {
                Log.w("ChatRepository", "Chat sync could not initialize", it)
                return@launch
            }

            while (isActive) {
                runCatching { refreshFriendRequestsNow(true) }
                    .onFailure { Log.w("ChatRepository", "Friend request sync failed", it) }

                runCatching { refreshUserChatsNow(true) }
                    .onFailure { Log.w("ChatRepository", "Chat sync failed", it) }

                delay(8_000)
            }
        }
    }

    suspend fun refreshUserChatsNow(forceRefresh: Boolean = false) {
        val mine = uid()

        val memberships = SupabaseProvider.client
            .from("direct_chat_members")
            .select {
                filter { eq("user_id", mine) }
            }
            .decodeList<ChatMemberRow>()

        val seen = memberships.map { it.chatId }.toSet()

        memberships.forEach { syncChat(mine, it.chatId) }

        // IMPORTANT: do not delete Room chats merely because the server query
        // did not return them. Room is the offline cache, and older chats can
        // exist locally from before the Supabase chat migration. Deleting them
        // here was the reason existing conversations disappeared from the list.
        // A successful server sync will update them; otherwise the cached chat
        // remains visible and usable.

        restoreCachedChats(mine, seen)
    }

    private suspend fun restoreCachedChats(mine: String, serverChatIds: Set<String>) {
        val localMessages = dao.getAllMessages()
        if (localMessages.isEmpty()) return

        val friends = runCatching { getFriendsImplementation(true) }.getOrElse { emptyList() }
        if (friends.isEmpty()) return

        val messageChatIds = localMessages.map { it.chatId }.toSet()

        friends.forEach { friend ->
            val chatId = getChatId(mine, friend.uid)
            if (chatId in serverChatIds || chatId !in messageChatIds) return@forEach

            val latest = localMessages
                .asSequence()
                .filter { it.chatId == chatId }
                .maxByOrNull { it.timestamp }
                ?: return@forEach

            val old = dao.getChat(chatId)
            dao.insertChat(
                ChatEntity(
                    chatId,
                    friend.uid,
                    friend.name,
                    friend.profilePicUrl,
                    if (latest.imageUrl.isNullOrBlank()) latest.content else "Image",
                    latest.timestamp,
                    old?.lastSeenTimestamp ?: 0L,
                    old?.unreadCount ?: 0,
                    old?.isBlocked ?: false
                )
            )
        }
    }

    private suspend fun syncChat(mine: String, chatId: String) {
        val members = SupabaseProvider.client
            .from("direct_chat_members")
            .select {
                filter { eq("chat_id", chatId) }
            }
            .decodeList<ChatMemberRow>()

        val friendId = members.firstOrNull { it.userId != mine }?.userId ?: return
        val friend = user(friendId)

        val latest = SupabaseProvider.client
            .from("messages")
            .select {
                filter { eq("chat_id", chatId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<MessageRow>()
            .firstOrNull()

        val old = dao.getChat(chatId)
        dao.insertChat(
            ChatEntity(
                chatId,
                friendId,
                friend?.name ?: old?.friendName ?: "Unknown",
                friend?.profilePicUrl,
                latest?.preview() ?: old?.lastMessage ?: "New chat started",
                latest?.time() ?: old?.lastMessageTime ?: 0L,
                old?.lastSeenTimestamp ?: 0L,
                old?.unreadCount ?: 0,
                old?.isBlocked ?: false
            )
        )
    }

    fun setActiveChat(chatId: String?) { activeChatId = chatId }

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
        createChatForFriend(friendUid, "")

        val row = MessageRow(
            UUID.randomUUID().toString(),
            chatId,
            mine,
            content,
            imageUrl,
            if (imageUrl == null) "TEXT" else "IMAGE"
        )

        dao.insertMessage(row.toEntity())
        SupabaseProvider.client.from("messages").insert(row)
        dao.updateMessageSyncStatus(row.id, true)
        syncChat(mine, chatId)
    }

    suspend fun fetchNewMessages(chatId: String) {
        val rows = SupabaseProvider.client
            .from("messages")
            .select {
                filter { eq("chat_id", chatId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<MessageRow>()

        dao.insertMessages(rows.map(MessageRow::toEntity))
        if (activeChatId == chatId) markChatAsRead(chatId)
        syncChat(uid(), chatId)
    }

    fun getPendingRequestCount() = dao.getPendingRequestCount()
    fun getTotalUnreadCount() = dao.getTotalUnreadCount().map { it ?: 0 }

    fun clearAllListeners() {
        activeChatId = null
        syncJob?.cancel()
        syncJob = null
    }

    suspend fun rewriteLocalDataAndSync() {
        clearAllListeners()
        dao.deleteAllMessages()
        dao.deleteAllChats()
        dao.deleteAllRequests()
        startSync()
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
            SupabaseProvider.client.from("messages").update({
                set("read_at", "now()")
            }) {
                filter {
                    eq("id", message.messageId)
                    neq("sender_id", mine)
                }
            }
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

    suspend fun deleteMessagesForEveryone(chatId: String, messageIds: List<String>) {
        SupabaseProvider.client.from("messages").delete {
            filter {
                eq("chat_id", chatId)
                isIn("id", messageIds)
                eq("sender_id", uid())
            }
        }
        dao.deleteMessages(messageIds)
    }

    suspend fun editMessage(chatId: String, messageId: String, newContent: String) {
        SupabaseProvider.client.from("messages").update({
            set("content", newContent.trim())
        }) {
            filter {
                eq("id", messageId)
                eq("chat_id", chatId)
                eq("sender_id", uid())
            }
        }
        dao.updateMessageContent(messageId, newContent.trim())
    }

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
    @SerialName("created_at") val createdAt: String
) {
    fun time() = java.time.OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
}

@Serializable
private data class DirectChatRow(val id: String)

@Serializable
private data class ChatMemberRow(
    @SerialName("chat_id") val chatId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
private data class MessageRow(
    val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    val type: String = "TEXT",
    @SerialName("created_at") val createdAt: String = java.time.Instant.now().toString()
) {
    fun time() = runCatching {
        java.time.OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

    fun preview() = if (imageUrl.isNullOrBlank()) content else "Image"

    fun toEntity() = MessageEntity(
        id,
        chatId,
        senderId,
        content,
        time(),
        false,
        true,
        imageUrl,
        type
    )
}

@Serializable
private data class BlockRow(
    @SerialName("user_id") val userId: String,
    @SerialName("blocked_user_id") val blockedUserId: String
)

private fun UserEntity.toPureUser() = PureUser(
    uid,
    email,
    name,
    phoneNumber,
    year,
    semester,
    course,
    dob,
    profilePicUrl,
    lastUpdated,
    GeoPoint(latitude, longitude)
)
