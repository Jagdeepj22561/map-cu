package com.example.shared.repository

import cocoapods.FirebaseFirestore.FIRDocumentSnapshot
import cocoapods.FirebaseFirestore.FIRQuerySnapshot
import cocoapods.FirebaseFirestore.FIRDocumentChange
import cocoapods.FirebaseFirestore.FIRDocumentChangeType
import cocoapods.FirebaseFirestore.FIRSetOptions
import cocoapods.FirebaseFirestore.FIRFieldValue
import com.example.shared.GeoPoint
import com.example.shared.model.PureChat
import com.example.shared.model.PureFriendRequest
import com.example.shared.model.PureGroup
import com.example.shared.model.PureGroupMessage
import com.example.shared.model.PureMessage
import com.example.shared.model.PureUser
import com.example.shared.utils.GeoUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class IosGroupMember(
    val user: IosUserProfile,
    val role: String
)

private object IosChatStore {
    private const val chatsCacheKey = "ios.cache.chats"
    private const val groupsCacheKey = "ios.cache.groups"
    private const val friendsCacheKey = "ios.cache.friends"
    private const val requestsCacheKey = "ios.cache.friendRequests"
    private const val lastReadChatsKey = "ios.cache.lastReadChats"
    private const val lastReadGroupsKey = "ios.cache.lastReadGroups"
    private const val clearedChatsKey = "ios.cache.clearedChats"
    private const val clearedGroupsKey = "ios.cache.clearedGroups"
    private const val activeChatSyncIntervalMs = 10_000L
    private const val idleChatSyncIntervalMs = 30_000L
    private const val groupsRefreshDebounceMs = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null
    private var loadedCacheOwnerUid: String? = IosSessionStore.current()?.uid
    private var lastGroupsRefreshAt = 0L

    val chats = MutableStateFlow(loadCachedChats())
    val groups = MutableStateFlow(loadCachedGroups())
    val friends = MutableStateFlow(loadCachedFriends())
    val friendRequests = MutableStateFlow(loadCachedRequests())

    private val lastReadChats = loadLongMap(lastReadChatsKey).toMutableMap()
    private val lastReadGroups = loadLongMap(lastReadGroupsKey).toMutableMap()
    private val clearedChats = loadLongMap(clearedChatsKey).toMutableMap()
    private val clearedGroups = loadLongMap(clearedGroupsKey).toMutableMap()

    private val chatMessages = mutableMapOf<String, MutableStateFlow<List<PureMessage>>>()
    private val groupMessages = mutableMapOf<String, MutableStateFlow<List<PureGroupMessage>>>()

    private var activeChatId: String? = null
    private var activeGroupId: String? = null

    fun startSync() {
        ensureLocalCacheLoaded()
        if (syncJob != null) return
        syncJob = scope.launch {
            runCatching { syncOnce() }
            while (isActive) {
                delay(currentSyncDelayMs())
                runCatching { syncOnce() }
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun resetSessionState() {
        stopSync()
        loadedCacheOwnerUid = null
        chats.value = emptyList()
        groups.value = emptyList()
        friends.value = emptyList()
        friendRequests.value = emptyList()
        chatMessages.clear()
        groupMessages.clear()
        activeChatId = null
        activeGroupId = null
        persistChats()
        persistGroups()
        persistFriends()
        persistRequests()
        persistLongMap(lastReadChatsKey, emptyMap())
        persistLongMap(lastReadGroupsKey, emptyMap())
        persistLongMap(clearedChatsKey, emptyMap())
        persistLongMap(clearedGroupsKey, emptyMap())
    }

    fun setActiveChat(chatId: String?) {
        activeChatId = chatId
    }

    fun setActiveGroup(groupId: String?) {
        activeGroupId = groupId
    }

    fun messagesFlow(chatId: String): StateFlow<List<PureMessage>> {
        return mutableMessagesFlow(chatId)
    }

    fun groupMessagesFlow(groupId: String): StateFlow<List<PureGroupMessage>> {
        return mutableGroupMessagesFlow(groupId)
    }

    private fun mutableMessagesFlow(chatId: String): MutableStateFlow<List<PureMessage>> {
        return chatMessages.getOrPut(chatId) {
            MutableStateFlow(loadCachedMessages(chatId))
        }
    }

    private fun mutableGroupMessagesFlow(groupId: String): MutableStateFlow<List<PureGroupMessage>> {
        return groupMessages.getOrPut(groupId) {
            MutableStateFlow(loadCachedGroupMessages(groupId))
        }
    }

    suspend fun syncOnce() {
        ensureLocalCacheLoaded()
        val uid = currentUid() ?: return
        syncFriendsAndRequests(uid)
        syncChats(uid)
        syncGroups(uid)
    }

    suspend fun refreshGroupsNow() {
        ensureLocalCacheLoaded()
        val uid = currentUid() ?: return
        val now = currentTimeMillis()
        if (now - lastGroupsRefreshAt < groupsRefreshDebounceMs) return
        syncGroups(uid)
        lastGroupsRefreshAt = now
    }

    suspend fun getFriends(): List<PureUser> {
        ensureLocalCacheLoaded()
        if (friends.value.isEmpty()) {
            syncFriendsAndRequests(currentUid() ?: return emptyList())
        }
        return friends.value
    }

    suspend fun getNearbyUsers(lat: Double, lng: Double): List<PureUser> {
        val uid = currentUid() ?: return emptyList()
        val snapshot = suspendCancellableCoroutine<FIRQuerySnapshot?> { continuation ->
            IosFirestoreRefs.users.getDocumentsWithCompletion { snapshot, error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(snapshot)
            }
        }
        return snapshot?.documents?.mapNotNull { doc ->
            (doc as? FIRDocumentSnapshot)?.data()?.asStringMap()?.toIosUserProfile(doc.documentID)
        }?.filter { profile ->
            profile.uid != uid &&
                !profile.ghostMode &&
                profile.location != null &&
                GeoUtils.distanceMeters(GeoPoint(lat, lng), profile.location) <= 10_000.0
        }?.map { it.toPureUser() } ?: emptyList()
    }

    suspend fun sendFriendRequest(email: String) {
        val session = IosSessionStore.current() ?: throw Exception("Not logged in")
        val snapshot = suspendCancellableCoroutine<FIRQuerySnapshot?> { continuation ->
            IosFirestoreRefs.users.queryWhereField("email", isEqualTo = email.trim())
                .queryLimitedTo(1u)
                .getDocumentsWithCompletion { snapshot, error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(snapshot)
                }
        }
        val targetDoc = snapshot?.documents?.firstOrNull() as? FIRDocumentSnapshot
            ?: throw Exception("Friend request failed: User with email '$email' not found.")
        val targetUid = targetDoc.documentID
        
        if (targetUid == session.uid) {
            throw Exception("You cannot send a friend request to yourself.")
        }
        if (friends.value.any { it.uid == targetUid }) {
            throw Exception("You are already friends with this user.")
        }

        val existingRequest = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.userFriendRequests(targetUid).documentWithPath(session.uid)
                .getDocumentWithCompletion { snapshot, error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(snapshot)
                }
        }
        if (existingRequest?.exists() == true) {
            throw Exception("A friend request has already been sent to this user.")
        }

        val incomingRequest = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.userFriendRequests(session.uid).documentWithPath(targetUid)
                .getDocumentWithCompletion { snapshot, error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(snapshot)
                }
        }
        if (incomingRequest?.exists() == true) {
            throw Exception("This user already sent you a friend request. Please accept it from requests.")
        }

        val me = IosUserStore.currentUser.value ?: IosUserStore.getUser(session.uid)
        val payload = mapOf(
            "senderUid" to session.uid,
            "senderName" to (me?.name ?: session.email.substringBefore("@")),
            "senderEmail" to session.email,
            "status" to "PENDING",
            "timestamp" to currentTimeMillis()
        )
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userFriendRequests(targetUid).documentWithPath(session.uid)
                .setData(payload as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
    }

    suspend fun acceptFriendRequest(senderUid: String) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        val now = currentTimeMillis()
        val newFriend = IosUserStore.cachedUser(senderUid) ?: IosUserStore.getUser(senderUid)
        
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userFriends(uid).documentWithPath(senderUid)
                .setData(mapOf("uid" to senderUid, "createdAt" to now) as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userFriends(senderUid).documentWithPath(uid)
                .setData(mapOf("uid" to uid, "createdAt" to now) as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userFriendRequests(uid).documentWithPath(senderUid)
                .deleteDocumentWithCompletion { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        friendRequests.value = friendRequests.value.filterNot {
            it.senderId == senderUid || it.requestId == senderUid
        }
        persistRequests()
        if (newFriend != null && friends.value.none { it.uid == senderUid }) {
            friends.value = friends.value + newFriend.toPureUser()
            persistFriends()
        }
        syncFriendsAndRequests(uid)
    }

    suspend fun rejectFriendRequest(senderUid: String) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userFriendRequests(uid).documentWithPath(senderUid)
                .deleteDocumentWithCompletion { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        friendRequests.value = friendRequests.value.filterNot {
            it.senderId == senderUid || it.requestId == senderUid
        }
        persistRequests()
        syncFriendsAndRequests(uid)
    }

    suspend fun unfriendUser(friendUid: String) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userFriends(uid).documentWithPath(friendUid)
                .deleteDocumentWithCompletion { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userFriends(friendUid).documentWithPath(uid)
                .deleteDocumentWithCompletion { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        syncFriendsAndRequests(uid)
    }

    suspend fun createChatForFriend(friendUid: String, friendName: String): String {
        require(friendUid.isNotBlank()) { "Friend UID cannot be empty" }
        val uid = currentUid() ?: throw Exception("Not logged in")
        val chatId = getChatId(uid, friendUid)
        val existing = chats.value.firstOrNull { it.chatId == chatId }
        val friend = IosUserStore.getUser(friendUid)
        val now = currentTimeMillis()
        val chatSummary = existing ?: PureChat(
            chatId = chatId,
            friendUid = friendUid,
            friendName = friend?.name?.takeIf { it.isNotBlank() } ?: friendName,
            friendProfilePicUrl = friend?.profilePicUrl?.takeIf { it.isNotBlank() },
            lastMessage = "New chat started",
            lastMessageTime = now,
            unreadCount = 0
        )
        chats.value = (chats.value.filterNot { it.chatId == chatId } + chatSummary)
            .sortedByDescending { it.lastMessageTime }
        persistChats()

        val chatSnap = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.userChats(uid).documentWithPath(chatId)
                .getDocumentWithCompletion { snapshot, error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(snapshot)
                }
        }
        
        if (chatSnap?.exists() != true) {
            val payload = mapOf(
                "chatId" to chatId,
                "friendUid" to friendUid,
                "lastMessage" to "New chat started",
                "lastMessageTime" to now,
                "lastSenderUid" to uid
            )
            suspendCancellableCoroutine<Unit> { continuation ->
                IosFirestoreRefs.userChats(uid).documentWithPath(chatId)
                    .setData(payload as Map<Any?, *>) { error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(Unit)
                    }
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                IosFirestoreRefs.userChats(friendUid).documentWithPath(chatId)
                    .setData((payload + ("friendUid" to uid)) as Map<Any?, *>) { error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(Unit)
                    }
            }
        }
        return chatId
    }

    suspend fun createGroup(name: String, memberUids: List<String>, iconUrl: String?) {
        createAdvancedGroup(name, memberUids, iconUrl, "", "PRIVATE")
    }

    suspend fun createAdvancedGroup(
        name: String,
        memberUids: List<String>,
        iconUrl: String?,
        customPublicId: String,
        visibility: String
    ) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        require(name.isNotBlank()) { "Group name is required" }
        val groupId = "group_${currentTimeMillis()}"
        val now = currentTimeMillis()
        val normalizedPublicId = normalizeIosGroupCode(customPublicId.ifBlank { name })
        require(normalizedPublicId.isNotBlank()) { "Group ID is required" }
        ensureIosGroupCodeAvailable(normalizedPublicId)
        val allMembers = (memberUids + uid).toSet()
        val membersMap = allMembers.associateWith { memberUid ->
            mapOf(
                "role" to if (memberUid == uid) "owner" else "member",
                "joinedAt" to now
            )
        }
        val groupPayload = buildMap {
            put("groupId", groupId)
            put("name", name.trim())
            put("ownerUid", uid)
            put("createdAt", now)
            put("lastMessage", "Group created")
            put("lastMessageTime", now)
            put("lastSenderUid", uid)
            put("members", membersMap)
            put("publicGroupId", normalizedPublicId)
            put("visibility", visibility)
            put("isDefaultGroup", false)
            put("inviteCode", buildIosInviteCode())
            put("memberCount", allMembers.size)
            if (!iconUrl.isNullOrBlank()) put("groupPicUrl", iconUrl)
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).setData(groupPayload as Map<Any?, *>) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }

        allMembers.forEach { memberUid ->
            suspendCancellableCoroutine<Unit> { continuation ->
                IosFirestoreRefs.userGroups(memberUid).documentWithPath(groupId)
                    .setData(mapOf("groupId" to groupId, "addedAt" to now) as Map<Any?, *>) { error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(Unit)
                    }
            }
        }
        syncGroups(uid)
    }

    suspend fun searchGroupByCode(code: String): PureGroup? {
        ensureDefaultGroups()
        val normalized = normalizeIosGroupCode(code)
        if (normalized.isBlank()) return null
        val publicResult = suspendCancellableCoroutine<FIRQuerySnapshot?> { continuation ->
            IosFirestoreRefs.groups.queryWhereField("publicGroupId", isEqualTo = normalized)
                .queryLimitedTo(1u)
                .getDocumentsWithCompletion { snapshot, error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(snapshot)
                }
        }
        val inviteResult = if (publicResult?.documents?.isNotEmpty() == true) {
            publicResult
        } else {
            suspendCancellableCoroutine<FIRQuerySnapshot?> { continuation ->
                IosFirestoreRefs.groups.queryWhereField("inviteCode", isEqualTo = normalized)
                    .queryLimitedTo(1u)
                    .getDocumentsWithCompletion { snapshot, error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(snapshot)
                    }
            }
        }
        val doc = inviteResult?.documents?.firstOrNull() as? FIRDocumentSnapshot ?: return null
        return doc.data()?.asStringMap()?.toPureGroup(doc.documentID)
    }

    suspend fun joinGroupByCode(code: String): PureGroup {
        val group = searchGroupByCode(code) ?: throw Exception("Group not found")
        val uid = currentUid() ?: throw Exception("Not logged in")
        val normalized = normalizeIosGroupCode(code)
        val alreadyJoined = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.userGroups(uid).documentWithPath(group.groupId)
                .getDocumentWithCompletion { snapshot, error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(snapshot)
                }
        }?.exists() == true
        if (alreadyJoined) return group
        if (group.visibility == "PRIVATE" && group.inviteCode != normalized) {
            throw Exception("Private groups require an invite link or code")
        }
        val now = currentTimeMillis()
        val latestGroup = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.group(group.groupId).getDocumentWithCompletion { snapshot, error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(snapshot)
            }
        }
        val latestData = latestGroup?.data()?.asStringMap().orEmpty()
        val members = latestData["members"].asStringMap()
        if (members.containsKey(uid)) {
            suspendCancellableCoroutine<Unit> { continuation ->
                IosFirestoreRefs.userGroups(uid).documentWithPath(group.groupId)
                    .setData(mapOf("groupId" to group.groupId, "addedAt" to now) as Map<Any?, *>) { error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(Unit)
                    }
            }
            syncGroups(uid)
            return groups.value.firstOrNull { it.groupId == group.groupId } ?: group
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(group.groupId).updateData(
                mapOf(
                    "members.$uid" to mapOf("role" to "member", "joinedAt" to now),
                    "memberCount" to (members.size + 1)
                ) as Map<Any?, *>
            ) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userGroups(uid).documentWithPath(group.groupId)
                .setData(mapOf("groupId" to group.groupId, "addedAt" to now) as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        syncGroups(uid)
        return getGroup(group.groupId) ?: group
    }

    suspend fun renameGroup(groupId: String, newName: String) {
        requireAdmin(groupId)
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "Group name cannot be empty" }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).updateData(mapOf("name" to trimmed) as Map<Any?, *>) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        syncGroups(currentUid() ?: return)
    }

    suspend fun pinLatestGroupMessage(groupId: String) {
        requireAdmin(groupId)
        val latest = groupMessagesFlow(groupId).value.lastOrNull()
            ?: throw Exception("No message available to pin")
        val preview = latest.content.takeIf { it.isNotBlank() }
            ?: if (!latest.imageUrl.isNullOrBlank()) "Image" else "Latest message"
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).updateData(
                mapOf(
                    "pinnedMessageId" to latest.messageId,
                    "pinnedMessagePreview" to preview.take(140)
                ) as Map<Any?, *>
            ) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        syncGroups(currentUid() ?: return)
    }

    suspend fun getFeaturedGroups(): List<PureGroup> {
        ensureDefaultGroups()
        return iosDefaultGroupSeeds.mapNotNull { seed -> searchGroupByCode(seed.publicId) }
    }

    suspend fun updateGroupPublicId(groupId: String, newId: String) {
        requireAdmin(groupId)
        val normalized = normalizeIosGroupCode(newId)
        require(normalized.isNotBlank()) { "Group ID is required" }
        val group = getGroup(groupId) ?: throw Exception("Group not found")
        if (group.isDefaultGroup) throw Exception("Default groups cannot be edited")
        val existing = searchGroupByCode(normalized)
        if (existing != null && existing.groupId != groupId) throw Exception("Group ID already taken")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).updateData(mapOf("publicGroupId" to normalized) as Map<Any?, *>) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        syncGroups(currentUid() ?: return)
    }

    suspend fun sendMessage(chatId: String, content: String, friendUid: String, imageUrl: String? = null) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        val sender = IosUserStore.currentUser.value ?: IosUserStore.getUser(uid)
        val messageId = "msg_${currentTimeMillis()}"
        val now = currentTimeMillis()
        val message = PureMessage(
            messageId = messageId,
            chatId = chatId,
            senderId = uid,
            content = content,
            timestamp = now,
            isRead = false,
            imageUrl = imageUrl,
            type = if (imageUrl.isNullOrBlank()) "TEXT" else "IMAGE"
        )
        val localMessages = messagesFlow(chatId).value + message
        updateChatMessages(chatId, localMessages)
        updateChatPreview(
            chatId = chatId,
            friendUid = friendUid,
            friendName = chats.value.firstOrNull { it.chatId == chatId }?.friendName ?: "Chat",
            friendProfilePicUrl = chats.value.firstOrNull { it.chatId == chatId }?.friendProfilePicUrl,
            lastMessage = if (imageUrl != null) "Image" else content,
            lastMessageTime = now,
            unreadCount = chats.value.firstOrNull { it.chatId == chatId }?.unreadCount ?: 0
        )

        val payload = buildMap {
            put("senderId", uid)
            put("content", content)
            put("timestamp", now)
            put("type", if (imageUrl != null) "IMAGE" else "TEXT")
            put("seen", false)
            if (!imageUrl.isNullOrBlank()) put("imageUrl", imageUrl)
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.chatMessages(chatId).documentWithPath(messageId)
                .setData(payload as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        
        val lastMessageSummary = if (imageUrl != null) "Image" else content
        val chatPayload = mapOf(
            "lastMessage" to lastMessageSummary,
            "lastMessageTime" to now,
            "lastSenderUid" to uid
        )
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userChats(uid).documentWithPath(chatId)
                .updateData(chatPayload as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userChats(friendUid).documentWithPath(chatId)
                .updateData(chatPayload as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.notifications.documentWithPath(messageId)
                .setData(
                    mapOf(
                        "type" to "DIRECT_MESSAGE",
                        "chatId" to chatId,
                        "senderId" to uid,
                        "senderName" to (sender?.name ?: "Unknown"),
                        "receiverId" to friendUid,
                        "content" to content,
                        "imageUrl" to imageUrl,
                        "timestamp" to now
                    ) as Map<Any?, *>
                ) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
    }

    suspend fun sendGroupMessage(groupId: String, content: String, imageUrl: String? = null) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        val sender = IosUserStore.currentUser.value ?: IosUserStore.getUser(uid)
        val messageId = "gmsg_${currentTimeMillis()}"
        val now = currentTimeMillis()
        val payload = buildMap {
            put("senderId", uid)
            put("senderName", sender?.name ?: "Unknown")
            put("content", content)
            put("timestamp", now)
            put("type", if (imageUrl != null) "IMAGE" else "TEXT")
            if (!imageUrl.isNullOrBlank()) put("imageUrl", imageUrl)
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.groupMessages(groupId).documentWithPath(messageId)
                .setData(payload as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).updateData(mapOf(
                "lastMessage" to (if (imageUrl != null) "Image" else content),
                "lastMessageTime" to now,
                "lastSenderUid" to uid
            ) as Map<Any?, *>) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        
        val local = groupMessagesFlow(groupId).value + PureGroupMessage(
            messageId = messageId,
            groupId = groupId,
            senderId = uid,
            senderName = sender?.name ?: "Unknown",
            content = content,
            timestamp = now
        )
        updateGroupMessages(groupId, local)
    }

    suspend fun markChatRead(chatId: String) {
        val uid = currentUid() ?: return
        lastReadChats[chatId] = currentTimeMillis()
        persistLongMap(lastReadChatsKey, lastReadChats)
        val messages = messagesFlow(chatId).value
        messages.filter { it.senderId != uid && !it.isRead }.forEach { message ->
            runCatching {
                suspendCancellableCoroutine<Unit> { continuation ->
                    IosFirestoreRefs.chatMessages(chatId).documentWithPath(message.messageId)
                        .updateData(mapOf("seen" to true) as Map<Any?, *>) { error ->
                            if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                            else continuation.resume(Unit)
                        }
                }
            }
        }
        updateChatMessages(
            chatId,
            messages.map { message ->
                if (message.senderId != uid) message.copy(isRead = true) else message
            }
        )
        chats.value = chats.value.map { chat ->
            if (chat.chatId == chatId) chat.copy(unreadCount = 0) else chat
        }
        persistChats()
    }

    suspend fun markGroupRead(groupId: String) {
        val uid = currentUid() ?: return
        lastReadGroups[groupId] = currentTimeMillis()
        persistLongMap(lastReadGroupsKey, lastReadGroups)
        val messages = groupMessagesFlow(groupId).value
        messages.filter { it.senderId != uid }.forEach { message ->
            runCatching {
                suspendCancellableCoroutine<Unit> { continuation ->
                    IosFirestoreRefs.groupMessages(groupId).documentWithPath(message.messageId)
                        .updateData(mapOf("seenBy.$uid" to true) as Map<Any?, *>) { error ->
                            if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                            else continuation.resume(Unit)
                        }
                }
            }
        }
        groups.value = groups.value.map { group ->
            if (group.groupId == groupId) group.copy(unreadCount = 0) else group
        }
        persistGroups()
    }

    suspend fun clearChat(chatId: String) {
        clearedChats[chatId] = currentTimeMillis()
        persistLongMap(clearedChatsKey, clearedChats)
        updateChatMessages(chatId, emptyList())
    }

    suspend fun clearGroupChat(groupId: String) {
        clearedGroups[groupId] = currentTimeMillis()
        persistLongMap(clearedGroupsKey, clearedGroups)
        updateGroupMessages(groupId, emptyList())
    }

    suspend fun deleteChat(chatId: String) {
        val uid = currentUid() ?: return
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userChats(uid).documentWithPath(chatId)
                .deleteDocumentWithCompletion { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        chats.value = chats.value.filterNot { it.chatId == chatId }
        persistChats()
        updateChatMessages(chatId, emptyList())
    }

    suspend fun deleteMessagesLocally(messageIds: List<String>) {
        chatMessages.keys.toList().forEach { chatId ->
            updateChatMessages(
                chatId,
                messagesFlow(chatId).value.filterNot { it.messageId in messageIds }
            )
        }
    }

    suspend fun deleteMessagesForEveryone(chatId: String, messageIds: List<String>) {
        messageIds.forEach { id ->
            suspendCancellableCoroutine<Unit> { continuation ->
                IosFirestoreRefs.chatMessages(chatId).documentWithPath(id)
                    .deleteDocumentWithCompletion { error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(Unit)
                    }
            }
        }
        updateChatMessages(
            chatId,
            messagesFlow(chatId).value.filterNot { it.messageId in messageIds }
        )
    }

    suspend fun editMessage(chatId: String, messageId: String, newContent: String) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        val target = messagesFlow(chatId).value.firstOrNull { it.messageId == messageId }
            ?: throw Exception("Message not found")
        if (target.senderId != uid) throw Exception("You can only edit your own messages")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.chatMessages(chatId).documentWithPath(messageId)
                .updateData(
                    mapOf(
                        "content" to newContent,
                        "editedAt" to currentTimeMillis()
                    ) as Map<Any?, *>
                ) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        updateChatMessages(
            chatId,
            messagesFlow(chatId).value.map { message ->
                if (message.messageId == messageId) message.copy(content = newContent) else message
            }
        )
    }

    suspend fun deleteGroupMessagesLocally(messageIds: List<String>) {
        groupMessages.keys.toList().forEach { groupId ->
            updateGroupMessages(
                groupId,
                groupMessagesFlow(groupId).value.filterNot { it.messageId in messageIds }
            )
        }
    }

    suspend fun deleteGroupMessagesForEveryone(groupId: String, messageIds: List<String>) {
        messageIds.forEach { id ->
            suspendCancellableCoroutine<Unit> { continuation ->
                IosFirestoreRefs.groupMessages(groupId).documentWithPath(id)
                    .deleteDocumentWithCompletion { error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(Unit)
                    }
            }
        }
        updateGroupMessages(
            groupId,
            groupMessagesFlow(groupId).value.filterNot { it.messageId in messageIds }
        )
    }

    suspend fun editGroupMessage(groupId: String, messageId: String, newContent: String) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        val target = groupMessagesFlow(groupId).value.firstOrNull { it.messageId == messageId }
            ?: throw Exception("Message not found")
        if (target.senderId != uid) throw Exception("You can only edit your own messages")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.groupMessages(groupId).documentWithPath(messageId)
                .updateData(
                    mapOf(
                        "content" to newContent,
                        "editedAt" to currentTimeMillis()
                    ) as Map<Any?, *>
                ) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        updateGroupMessages(
            groupId,
            groupMessagesFlow(groupId).value.map { message ->
                if (message.messageId == messageId) message.copy(content = newContent) else message
            }
        )
    }

    suspend fun blockUser(friendUid: String) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userBlocks(uid).documentWithPath(friendUid)
                .setData(mapOf("uid" to friendUid, "createdAt" to currentTimeMillis()) as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
    }

    suspend fun unblockUser(friendUid: String) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userBlocks(uid).documentWithPath(friendUid)
                .deleteDocumentWithCompletion { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
    }

    suspend fun isUserBlocked(friendUid: String): Boolean {
        val uid = currentUid() ?: return false
        val iBlocked = runCatching {
            val doc = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
                IosFirestoreRefs.userBlocks(uid).documentWithPath(friendUid)
                    .getDocumentWithCompletion { snapshot, error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(snapshot)
                    }
            }
            doc?.exists() == true
        }.getOrDefault(false)
        if (iBlocked) return true
        
        return runCatching {
            val doc = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
                IosFirestoreRefs.userBlocks(friendUid).documentWithPath(uid)
                    .getDocumentWithCompletion { snapshot, error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(snapshot)
                    }
            }
            doc?.exists() == true
        }.getOrDefault(false)
    }

    suspend fun getGroup(groupId: String): PureGroup? {
        groups.value.firstOrNull { it.groupId == groupId }?.let { return it }
        val doc = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.group(groupId).getDocumentWithCompletion { snapshot, error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(snapshot)
            }
        }
        val groupMap = doc?.data()?.asStringMap() ?: return null
        return groupMap.toPureGroup(groupId)
    }

    suspend fun getGroupMembers(groupId: String): List<IosGroupMember> {
        val doc = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.group(groupId).getDocumentWithCompletion { snapshot, error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(snapshot)
            }
        }
        val members = doc?.data()?.asStringMap()?.get("members")?.asStringMap() ?: return emptyList()
        return members.entries.mapNotNull { (uid, rawRole) ->
            val role = rawRole.asStringMap()?.get("role").stringValue().ifBlank { "member" }
            val user = IosUserStore.getUser(uid) ?: return@mapNotNull null
            IosGroupMember(user = user, role = role)
        }
    }

    suspend fun getMyRole(groupId: String): String? {
        val uid = currentUid() ?: return null
        val doc = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.group(groupId).getDocumentWithCompletion { snapshot, error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(snapshot)
            }
        }
        val members = doc?.data()?.asStringMap()?.get("members")?.asStringMap() ?: return null
        return members[uid]?.asStringMap()?.get("role").stringValue().takeIf { it.isNotBlank() }
    }

    suspend fun promoteToAdmin(groupId: String, uid: String) {
        requireAdmin(groupId)
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).updateData(mapOf("members.$uid.role" to "admin") as Map<Any?, *>) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        syncGroups(currentUid() ?: return)
    }

    suspend fun demoteAdmin(groupId: String, uid: String) {
        if (getMyRole(groupId) != "owner") throw Exception("Only owner")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).updateData(mapOf("members.$uid.role" to "member") as Map<Any?, *>) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        syncGroups(currentUid() ?: return)
    }

    suspend fun transferOwnership(groupId: String, newOwner: String) {
        val me = currentUid() ?: throw Exception("Not logged in")
        if (getMyRole(groupId) != "owner") throw Exception("Only owner")
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).updateData(mapOf(
                "ownerUid" to newOwner,
                "members.$newOwner.role" to "owner",
                "members.$me.role" to "admin"
            ) as Map<Any?, *>) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        syncGroups(me)
    }

    suspend fun exitGroup(groupId: String) {
        val uid = currentUid() ?: throw Exception("Not logged in")
        if (getMyRole(groupId) == "owner") {
            throw Exception("Transfer ownership first")
        }
        val latestGroup = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.group(groupId).getDocumentWithCompletion { snapshot, error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(snapshot)
            }
        }
        val latestData = latestGroup?.data()?.asStringMap().orEmpty()
        val members = latestData["members"].asStringMap()
        val nextCount = if (members.containsKey(uid)) {
            (members.size - 1).coerceAtLeast(0)
        } else {
            members.size
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.group(groupId).updateData(
                mapOf(
                    "members.$uid" to FIRFieldValue.fieldValueForDelete(),
                    "memberCount" to nextCount
                ) as Map<Any?, *>
            ) { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.userGroups(uid).documentWithPath(groupId).deleteDocumentWithCompletion { error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(Unit)
            }
        }
        groups.value = groups.value.filterNot { it.groupId == groupId }
        persistGroups()
        updateGroupMessages(groupId, emptyList())
    }

    private suspend fun ensureIosGroupCodeAvailable(publicId: String) {
        val existing = suspendCancellableCoroutine<FIRQuerySnapshot?> { continuation ->
            IosFirestoreRefs.groups.queryWhereField("publicGroupId", isEqualTo = publicId)
                .queryLimitedTo(1u)
                .getDocumentsWithCompletion { snapshot, error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(snapshot)
                }
        }
        if (existing?.documents?.isNotEmpty() == true) throw Exception("Group ID already taken")
    }

    private suspend fun ensureDefaultGroups() {
        iosDefaultGroupSeeds.forEach { seed ->
            val existing = searchGroupByCode(seed.publicId)
            if (existing != null) return@forEach
            val groupId = "default_${seed.publicId.lowercase()}"
            val now = currentTimeMillis()
            val payload = mapOf(
                "groupId" to groupId,
                "name" to seed.name,
                "ownerUid" to "system",
                "createdAt" to now,
                "lastMessage" to "Welcome to ${seed.name}",
                "lastMessageTime" to now,
                "members" to emptyMap<String, Any>(),
                "publicGroupId" to seed.publicId,
                "visibility" to "PUBLIC",
                "isDefaultGroup" to true,
                "inviteCode" to buildIosInviteCode(),
                "memberCount" to 0
            )
            suspendCancellableCoroutine<Unit> { continuation ->
                IosFirestoreRefs.group(groupId).setData(payload as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
            }
        }
    }

    private suspend fun loadUnreadGroupIds(
        groupId: String,
        uid: String,
        messages: List<PureGroupMessage>
    ): List<String> {
        return messages.filter { message ->
            if (message.senderId == uid) return@filter false
            val seenBy = runCatching {
                val doc = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
                    IosFirestoreRefs.groupMessages(groupId).documentWithPath(message.messageId)
                        .getDocumentWithCompletion { snapshot, error ->
                            if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                            else continuation.resume(snapshot)
                        }
                }
                doc?.data()?.asStringMap()?.get("seenBy")?.asStringMap() ?: emptyMap()
            }.getOrDefault(emptyMap())
            seenBy[uid] == null
        }.map { it.messageId }
    }

    private suspend fun requireAdmin(groupId: String) {
        val role = getMyRole(groupId)
        if (role != "owner" && role != "admin") {
            throw Exception("Admin permission required")
        }
    }

    private fun updateChatMessages(chatId: String, messages: List<PureMessage>) {
        (messagesFlow(chatId) as? MutableStateFlow)?.value = messages
        IosLocalDatabaseStore.saveJson(
            "ios.cache.chatMessages.$chatId",
            messages.map { message -> message.toMap() }
        )
    }

    private fun updateGroupMessages(groupId: String, messages: List<PureGroupMessage>) {
        (groupMessagesFlow(groupId) as? MutableStateFlow)?.value = messages
        IosLocalDatabaseStore.saveJson(
            "ios.cache.groupMessages.$groupId",
            messages.map { message -> message.toMap() }
        )
    }

    private fun updateChatPreview(
        chatId: String,
        friendUid: String,
        friendName: String,
        friendProfilePicUrl: String?,
        lastMessage: String,
        lastMessageTime: Long,
        unreadCount: Int
    ) {
        val chatSummary = PureChat(
            chatId = chatId,
            friendUid = friendUid,
            friendName = friendName,
            friendProfilePicUrl = friendProfilePicUrl,
            lastMessage = lastMessage,
            lastMessageTime = lastMessageTime,
            unreadCount = unreadCount
        )
        chats.value = (chats.value.filterNot { it.chatId == chatId } + chatSummary)
            .sortedByDescending { it.lastMessageTime }
        persistChats()
    }

    private fun persistChats() {
        IosLocalDatabaseStore.saveJson(chatsCacheKey, chats.value.map { it.toMap() })
    }

    private fun persistGroups() {
        IosLocalDatabaseStore.saveJson(groupsCacheKey, groups.value.map { it.toMap() })
    }

    private fun persistFriends() {
        IosLocalDatabaseStore.saveJson(friendsCacheKey, friends.value.map { it.toMap() })
    }

    private fun persistRequests() {
        IosLocalDatabaseStore.saveJson(requestsCacheKey, friendRequests.value.map { it.toMap() })
    }

    private fun currentUid(): String? = IosSessionStore.current()?.uid

    private fun getChatId(myUid: String, friendUid: String): String {
        return if (myUid < friendUid) "${myUid}_$friendUid" else "${friendUid}_$myUid"
    }

    private fun loadCachedChats(): List<PureChat> {
        return IosLocalDatabaseStore.readJson(chatsCacheKey).asList()
            ?.mapNotNull { it.asStringMap()?.toPureChat() }
            ?.sortedByDescending { it.lastMessageTime }
            ?: emptyList()
    }

    private fun loadCachedGroups(): List<PureGroup> {
        return IosLocalDatabaseStore.readJson(groupsCacheKey).asList()
            ?.mapNotNull { it.asStringMap()?.toPureGroup() }
            ?.sortedByDescending { it.lastMessageTime }
            ?: emptyList()
    }

    private fun loadCachedFriends(): List<PureUser> {
        return IosLocalDatabaseStore.readJson(friendsCacheKey).asList()
            ?.mapNotNull { it.asStringMap()?.toPureUser() }
            ?: emptyList()
    }

    private fun loadCachedRequests(): List<PureFriendRequest> {
        return IosLocalDatabaseStore.readJson(requestsCacheKey).asList()
            ?.mapNotNull { it.asStringMap()?.toPureFriendRequest() }
            ?: emptyList()
    }

    private fun loadCachedMessages(chatId: String): List<PureMessage> {
        return IosLocalDatabaseStore.readJson("ios.cache.chatMessages.$chatId").asList()
            ?.mapNotNull { it.asStringMap()?.toPureMessage(chatId) }
            ?.sortedBy { it.timestamp }
            ?: emptyList()
    }

    private fun loadCachedGroupMessages(groupId: String): List<PureGroupMessage> {
        return IosLocalDatabaseStore.readJson("ios.cache.groupMessages.$groupId").asList()
            ?.mapNotNull { it.asStringMap()?.toPureGroupMessage(groupId) }
            ?.sortedBy { it.timestamp }
            ?: emptyList()
    }

    private fun loadLongMap(key: String): Map<String, Long> {
        return IosLocalDatabaseStore.readJson(key).asStringMap()
            ?.mapValuesNotNull { entry -> entry.value.longValue().takeIf { it > 0L } }
            ?: emptyMap()
    }

    private fun persistLongMap(key: String, map: Map<String, Long>) {
        IosLocalDatabaseStore.saveJson(key, map)
    }

    private fun ensureLocalCacheLoaded() {
        val currentOwner = currentUid()
        if (loadedCacheOwnerUid == currentOwner) return
        loadedCacheOwnerUid = currentOwner
        lastGroupsRefreshAt = 0L
        chats.value = loadCachedChats()
        groups.value = loadCachedGroups()
        friends.value = loadCachedFriends()
        friendRequests.value = loadCachedRequests()
        lastReadChats.clear()
        lastReadChats.putAll(loadLongMap(lastReadChatsKey))
        lastReadGroups.clear()
        lastReadGroups.putAll(loadLongMap(lastReadGroupsKey))
        clearedChats.clear()
        clearedChats.putAll(loadLongMap(clearedChatsKey))
        clearedGroups.clear()
        clearedGroups.putAll(loadLongMap(clearedGroupsKey))
        chatMessages.clear()
        groupMessages.clear()
    }

    private fun currentSyncDelayMs(): Long {
        return if (activeChatId != null || activeGroupId != null) {
            activeChatSyncIntervalMs
        } else {
            idleChatSyncIntervalMs
        }
    }
}

actual class ChatRepository : IChatRepository {
    override val allChats: Flow<List<PureChat>> = IosChatStore.chats
    override val allGroups: Flow<List<PureGroup>> = IosChatStore.groups

    val friends: StateFlow<List<PureUser>> = IosChatStore.friends
    val friendRequests: StateFlow<List<PureFriendRequest>> = IosChatStore.friendRequests

    override suspend fun getFriends(): List<PureUser> = IosChatStore.getFriends()

    override suspend fun getNearbyUsers(lat: Double, lng: Double): List<PureUser> =
        IosChatStore.getNearbyUsers(lat, lng)

    override suspend fun sendFriendRequest(email: String) {
        IosChatStore.sendFriendRequest(email)
    }

    override suspend fun createChatForFriend(friendUid: String, friendName: String): String =
        IosChatStore.createChatForFriend(friendUid, friendName)

    override suspend fun createGroup(name: String, memberUids: List<String>, iconUrl: String?) {
        IosChatStore.createGroup(name, memberUids, iconUrl)
    }

    override suspend fun toggleGhostMode(isGhostMode: Boolean) {
        IosChatStore.toggleGhostMode(isGhostMode)
    }

    override fun listenToUserChats() {
        IosChatStore.startSync()
    }

    override fun listenToUserGroups() {
        IosChatStore.startSync()
    }

    override fun startSync() {
        IosChatStore.startSync()
    }

    override suspend fun getChatId(myUid: String, friendUid: String): String {
        return if (myUid < friendUid) "${myUid}_$friendUid" else "${friendUid}_$myUid"
    }

    override fun getCurrentUserUid(): String? = IosSessionStore.current()?.uid

    suspend fun refreshGroupsNow() = IosChatStore.refreshGroupsNow()

    fun messagesFlow(chatId: String): StateFlow<List<PureMessage>> = IosChatStore.messagesFlow(chatId)

    fun groupMessagesFlow(groupId: String): StateFlow<List<PureGroupMessage>> = IosChatStore.groupMessagesFlow(groupId)

    fun setActiveChat(chatId: String?) = IosChatStore.setActiveChat(chatId)

    fun setActiveGroup(groupId: String?) = IosChatStore.setActiveGroup(groupId)

    suspend fun sendMessage(chatId: String, content: String, friendUid: String, imageUrl: String? = null) {
        IosChatStore.sendMessage(chatId, content, friendUid, imageUrl)
    }

    suspend fun sendGroupMessage(groupId: String, content: String, imageUrl: String? = null) {
        IosChatStore.sendGroupMessage(groupId, content, imageUrl)
    }

    suspend fun markChatRead(chatId: String) = IosChatStore.markChatRead(chatId)

    suspend fun markGroupRead(groupId: String) = IosChatStore.markGroupRead(groupId)

    suspend fun acceptFriendRequest(senderUid: String) = IosChatStore.acceptFriendRequest(senderUid)

    suspend fun rejectFriendRequest(senderUid: String) = IosChatStore.rejectFriendRequest(senderUid)

    suspend fun unfriendUser(friendUid: String) = IosChatStore.unfriendUser(friendUid)

    suspend fun clearChat(chatId: String) = IosChatStore.clearChat(chatId)

    suspend fun clearGroupChat(groupId: String) = IosChatStore.clearGroupChat(groupId)

    suspend fun deleteChat(chatId: String) = IosChatStore.deleteChat(chatId)

    suspend fun deleteMessagesLocally(messageIds: List<String>) = IosChatStore.deleteMessagesLocally(messageIds)

    suspend fun deleteMessagesForEveryone(chatId: String, messageIds: List<String>) {
        IosChatStore.deleteMessagesForEveryone(chatId, messageIds)
    }

    suspend fun deleteGroupMessagesLocally(messageIds: List<String>) {
        IosChatStore.deleteGroupMessagesLocally(messageIds)
    }

    suspend fun deleteGroupMessagesForEveryone(groupId: String, messageIds: List<String>) {
        IosChatStore.deleteGroupMessagesForEveryone(groupId, messageIds)
    }

    suspend fun blockUser(friendUid: String) = IosChatStore.blockUser(friendUid)

    suspend fun unblockUser(friendUid: String) = IosChatStore.unblockUser(friendUid)

    suspend fun isUserBlocked(friendUid: String): Boolean = IosChatStore.isUserBlocked(friendUid)

    suspend fun getGroup(groupId: String): PureGroup? = IosChatStore.getGroup(groupId)

    suspend fun getGroupMembers(groupId: String): List<IosGroupMember> = IosChatStore.getGroupMembers(groupId)

    suspend fun getMyRole(groupId: String): String? = IosChatStore.getMyRole(groupId)

    suspend fun promoteToAdmin(groupId: String, uid: String) = IosChatStore.promoteToAdmin(groupId, uid)

    suspend fun demoteAdmin(groupId: String, uid: String) = IosChatStore.demoteAdmin(groupId, uid)

    suspend fun transferOwnership(groupId: String, newOwner: String) {
        IosChatStore.transferOwnership(groupId, newOwner)
    }

    suspend fun exitGroup(groupId: String) = IosChatStore.exitGroup(groupId)

    suspend fun joinGroupByCode(code: String): PureGroup = IosChatStore.joinGroupByCode(code)

    suspend fun renameGroup(groupId: String, newName: String) = IosChatStore.renameGroup(groupId, newName)

    suspend fun pinLatestGroupMessage(groupId: String) = IosChatStore.pinLatestGroupMessage(groupId)

    fun clearAllListeners() = IosChatStore.stopSync()

    fun resetSessionState() = IosChatStore.resetSessionState()
}

private fun PureUser.toMap(): Map<String, Any?> = mapOf(
    "uid" to uid,
    "email" to email,
    "name" to name,
    "phoneNumber" to phoneNumber,
    "year" to year,
    "semester" to semester,
    "course" to course,
    "dob" to dob,
    "profilePicUrl" to profilePicUrl,
    "lastUpdated" to lastUpdated,
    "location" to location?.let { mapOf("latitude" to it.lat, "longitude" to it.lng) }
)

private fun PureFriendRequest.toMap(): Map<String, Any?> = mapOf(
    "requestId" to requestId,
    "senderId" to senderId,
    "senderName" to senderName,
    "senderEmail" to senderEmail,
    "receiverId" to receiverId,
    "status" to status,
    "timestamp" to timestamp
)

private fun PureChat.toMap(): Map<String, Any?> = mapOf(
    "chatId" to chatId,
    "friendUid" to friendUid,
    "friendName" to friendName,
    "friendProfilePicUrl" to friendProfilePicUrl,
    "lastMessage" to lastMessage,
    "lastMessageTime" to lastMessageTime,
    "unreadCount" to unreadCount
)

private fun PureGroup.toMap(): Map<String, Any?> = mapOf(
    "groupId" to groupId,
    "name" to name,
    "ownerUid" to ownerUid,
    "lastMessage" to lastMessage,
    "lastMessageTime" to lastMessageTime,
    "unreadCount" to unreadCount,
    "createdAt" to createdAt,
    "groupIconUrl" to groupIconUrl,
    "publicGroupId" to publicGroupId,
    "visibility" to visibility,
    "isDefaultGroup" to isDefaultGroup,
    "inviteCode" to inviteCode,
    "memberCount" to memberCount,
    "pinnedMessageId" to pinnedMessageId,
    "pinnedMessagePreview" to pinnedMessagePreview
)

private fun PureMessage.toMap(): Map<String, Any?> = mapOf(
    "messageId" to messageId,
    "chatId" to chatId,
    "senderId" to senderId,
    "content" to content,
    "timestamp" to timestamp,
    "isRead" to isRead
)

private fun PureGroupMessage.toMap(): Map<String, Any?> = mapOf(
    "messageId" to messageId,
    "groupId" to groupId,
    "senderId" to senderId,
    "senderName" to senderName,
    "content" to content,
    "timestamp" to timestamp,
    "imageUrl" to imageUrl,
    "type" to type
)

private fun Map<String, Any?>.toPureUser(): PureUser {
    val locationMap = this["location"].asStringMap()
    val latitude = locationMap?.get("latitude").doubleValue()
    val longitude = locationMap?.get("longitude").doubleValue()
    return PureUser(
        uid = this["uid"].stringValue(),
        email = this["email"].stringValue(),
        name = this["name"].stringValue(),
        phoneNumber = this["phoneNumber"].stringValue(),
        year = this["year"].stringValue(),
        semester = this["semester"].stringValue(),
        course = this["course"].stringValue(),
        dob = this["dob"].stringValue(),
        profilePicUrl = this["profilePicUrl"].stringValue(),
        lastUpdated = this["lastUpdated"].longValue(),
        location = if ((latitude ?: 0.0) != 0.0 || (longitude ?: 0.0) != 0.0) {
            GeoPoint(latitude ?: 0.0, longitude ?: 0.0)
        } else {
            null
        }
    )
}

private fun Map<String, Any?>.toPureFriendRequest(): PureFriendRequest = PureFriendRequest(
    requestId = this["requestId"].stringValue().ifBlank {
        this["senderId"].stringValue().ifBlank { this["senderUid"].stringValue() }
    },
    senderId = this["senderId"].stringValue().ifBlank { this["senderUid"].stringValue() },
    senderName = this["senderName"].stringValue(),
    senderEmail = this["senderEmail"].stringValue(),
    receiverId = this["receiverId"].stringValue().ifBlank {
        IosSessionStore.current()?.uid.orEmpty()
    },
    status = this["status"].stringValue().ifBlank { "PENDING" },
    timestamp = this["timestamp"].longValue()
)

private fun Map<String, Any?>.toPureChat(): PureChat = PureChat(
    chatId = this["chatId"].stringValue(),
    friendUid = this["friendUid"].stringValue(),
    friendName = this["friendName"].stringValue(),
    friendProfilePicUrl = this["friendProfilePicUrl"].stringValue().takeIf { it.isNotBlank() },
    lastMessage = this["lastMessage"].stringValue(),
    lastMessageTime = this["lastMessageTime"].longValue(),
    unreadCount = this["unreadCount"].longValue().toInt()
)

private fun Map<String, Any?>.toPureGroup(): PureGroup = PureGroup(
    groupId = this["groupId"].stringValue(),
    name = this["name"].stringValue(),
    ownerUid = this["ownerUid"].stringValue(),
    lastMessage = this["lastMessage"].stringValue(),
    lastMessageTime = this["lastMessageTime"].longValue(),
    unreadCount = this["unreadCount"].longValue().toInt(),
    createdAt = this["createdAt"].longValue(),
    groupIconUrl = this["groupPicUrl"].stringValue().takeIf { it.isNotBlank() }
        ?: this["groupIconUrl"].stringValue().takeIf { it.isNotBlank() },
    publicGroupId = this["publicGroupId"].stringValue().takeIf { it.isNotBlank() },
    visibility = this["visibility"].stringValue().ifBlank { "PRIVATE" },
    isDefaultGroup = this["isDefaultGroup"].booleanValue(false),
    inviteCode = this["inviteCode"].stringValue().takeIf { it.isNotBlank() },
    memberCount = this["members"].asStringMap()?.size
        ?: this["memberCount"].longValue().toInt().coerceAtLeast(0),
    pinnedMessageId = this["pinnedMessageId"].stringValue().takeIf { it.isNotBlank() },
    pinnedMessagePreview = this["pinnedMessagePreview"].stringValue().takeIf { it.isNotBlank() }
)

private fun Map<String, Any?>.toPureGroup(groupId: String): PureGroup =
    toPureGroup().copy(groupId = groupId.ifBlank { this["groupId"].stringValue() })

private fun Map<String, Any?>.toPureMessage(chatId: String, overrideId: String? = null): PureMessage {
    return PureMessage(
        messageId = overrideId ?: this["messageId"].stringValue(),
        chatId = chatId,
        senderId = this["senderId"].stringValue(),
        content = this["content"].stringValue(),
        timestamp = this["timestamp"].longValue(),
        isRead = this["seen"].booleanValue(this["isRead"].booleanValue(false)),
        imageUrl = this["imageUrl"].stringValue().takeIf { it.isNotBlank() },
        type = this["type"].stringValue().ifBlank { if (this["imageUrl"].stringValue().isNotBlank()) "IMAGE" else "TEXT" }
    )
}

private fun Map<String, Any?>.toPureGroupMessage(groupId: String, overrideId: String? = null): PureGroupMessage {
    return PureGroupMessage(
        messageId = overrideId ?: this["messageId"].stringValue(),
        groupId = groupId,
        senderId = this["senderId"].stringValue(),
        senderName = this["senderName"].stringValue(),
        content = this["content"].stringValue(),
        timestamp = this["timestamp"].longValue(),
        imageUrl = this["imageUrl"].stringValue().takeIf { it.isNotBlank() },
        type = this["type"].stringValue().ifBlank { "TEXT" }
    )
}

private data class IosDefaultGroupSeed(val name: String, val publicId: String)

private val iosDefaultGroupSeeds = listOf(
    IosDefaultGroupSeed("CU Official", "CUOFFICIAL"),
    IosDefaultGroupSeed("CU Placements", "CUPLACE"),
    IosDefaultGroupSeed("CU Events", "CUEVENTS")
)

private fun normalizeIosGroupCode(raw: String): String =
    raw.uppercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(18)

private fun buildIosInviteCode(): String =
    currentTimeMillis().toString(16).takeLast(8).uppercase()
