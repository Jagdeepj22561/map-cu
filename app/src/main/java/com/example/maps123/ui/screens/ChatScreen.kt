package com.example.maps123.ui.screens

import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.maps123.data.local.UserEntity
import com.example.maps123.data.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.shared.ui.*
import com.example.shared.model.PureChat
import com.example.maps123.ui.components.AppAsyncImage
import com.example.maps123.utils.toPureUser

import com.google.android.gms.maps.model.LatLng
import com.example.shared.GeoPoint
import com.example.shared.utils.GeoUtils
import com.example.shared.model.PureUser

private fun formatFriendRequestError(message: String?): String {
    val text = message?.trim().orEmpty()
    return when {
        text.contains("scope left the composition", ignoreCase = true) -> ""
        text.contains("job was cancelled", ignoreCase = true) -> ""
        text.contains("already sent you a friend request", ignoreCase = true) -> text
        text.contains("bidirectional pending", ignoreCase = true) -> "This user has already sent you a friend request."
        text.contains("already been sent", ignoreCase = true) -> "Friend request already sent."
        text.contains("already friends", ignoreCase = true) -> "You are already friends with this user."
        text.contains("yourself", ignoreCase = true) -> "You cannot send a friend request to yourself."
        text.contains("not found", ignoreCase = true) -> "No user found with that email."
        text.contains("permission", ignoreCase = true) -> "Permission denied while sending friend request."
        text.isBlank() -> "Failed to send friend request."
        else -> text
    }
}

private fun Throwable.isFirestorePermissionDenied(): Boolean =
    message?.contains("permission", ignoreCase = true) == true ||
        message?.contains("row-level security", ignoreCase = true) == true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatRepository: ChatRepository,
    friends: List<UserEntity>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onChatClick: (String, String, String, String?) -> Unit,
    onAddFriendClick: () -> Unit,
    searchQuery: String,
    onMessagePrivately: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    currentUser: UserEntity? = null,
    userLocation: LatLng? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var requestError by remember { mutableStateOf<String?>(null) }

    // Always refresh existing conversations when the Chat screen enters.
    // This fixes the case where a chat exists on Supabase but Room has not
    // populated the list yet, so the user no longer has to press New Chat.
    LaunchedEffect(chatRepository, selectedTab) {
        if (selectedTab == 0) {
            runCatching {
                chatRepository.refreshUserChatsNow(forceRefresh = true)
            }.onFailure {
                // Background sync will retry; don't interrupt the chat UI.
            }
        }
    }

    var showNewChatDialog by remember { mutableStateOf(false) }
    var showNearbySheet by remember { mutableStateOf(false) }
    var nearbyUsers by remember { mutableStateOf<List<UserEntity>>(emptyList()) }
    var nearbyLoading by remember { mutableStateOf(false) }
    var nearbyError by remember { mutableStateOf<String?>(null) }
    var nearbyLimitDialogMessage by remember { mutableStateOf<String?>(null) }
    var pendingRequestEmails by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        runCatching { pendingRequestEmails = chatRepository.getOutgoingPendingRequestEmails() }
    }
    var nearbyLastRefreshedAt by remember { mutableStateOf(chatRepository.getNearbyUsersLastRefreshTime()) }
    var nearbyTimeTicker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nearbyRefreshRemainingMs by remember { mutableLongStateOf(chatRepository.getNearbyUsersRefreshRemainingMs()) }

    val pureFriends = remember(friends) { friends.map { it.toPureUser() } }

    val currentLat = userLocation?.latitude ?: currentUser?.latitude ?: 0.0
    val currentLng = userLocation?.longitude ?: currentUser?.longitude ?: 0.0

    val pureNearbyUsers = remember(nearbyUsers, currentLat, currentLng) {
        nearbyUsers.map { user ->
            val dist = if (currentLat != 0.0 && currentLng != 0.0 && user.latitude != 0.0 && user.longitude != 0.0) {
                GeoUtils.distanceMeters(
                    GeoPoint(currentLat, currentLng),
                    GeoPoint(user.latitude, user.longitude)
                )
            } else {
                null
            }
            user.toPureUser(distanceMeters = dist)
        }.sortedWith(
            compareBy<PureUser> { it.distanceMeters ?: Double.MAX_VALUE }
                .thenByDescending { it.lastUpdated }
        )
    }

    suspend fun loadNearbyUsers(forceRefresh: Boolean) {
        nearbyLoading = true
        nearbyError = null
        try {
            nearbyUsers = chatRepository.getNearbyUsersImplementation(
                forceRefresh = forceRefresh,
                userLat = currentLat,
                userLng = currentLng
            )
            nearbyLastRefreshedAt = chatRepository.getNearbyUsersLastRefreshTime()
            nearbyRefreshRemainingMs = chatRepository.getNearbyUsersRefreshRemainingMs()
        } catch (e: Exception) {
            if (e.isFirestorePermissionDenied()) {
                nearbyLimitDialogMessage = "Nearby users refresh is currently blocked by your server rules. Try again after the server-side limit resets."
                nearbyError = null
            } else {
                nearbyError = e.localizedMessage ?: "Failed to load nearby users"
            }
            nearbyUsers = emptyList()
        } finally {
            nearbyLoading = false
        }
    }

    LaunchedEffect(showNearbySheet, nearbyLastRefreshedAt) {
        nearbyTimeTicker = System.currentTimeMillis()
        while (showNearbySheet) {
            nearbyRefreshRemainingMs = chatRepository.getNearbyUsersRefreshRemainingMs()
            delay(60_000L)
            nearbyTimeTicker = System.currentTimeMillis()
        }
    }

    val refreshButtonText = remember(nearbyLoading, nearbyLastRefreshedAt, nearbyTimeTicker) {
        if (nearbyLoading) "Refreshing..." else "Refresh"
    }
    val refreshStatusText = remember(nearbyLoading, nearbyLastRefreshedAt, nearbyTimeTicker) {
        when {
            nearbyLoading && nearbyLastRefreshedAt <= 0L -> "Loading nearby users..."
            nearbyLoading -> "Refreshing nearby users..."
            nearbyRefreshRemainingMs > 0L -> "Next refresh in ${formatRemainingDuration(nearbyRefreshRemainingMs)}"
            nearbyLastRefreshedAt <= 0L -> "Not refreshed yet"
            else -> formatNearbyRefreshLabel(nearbyLastRefreshedAt, nearbyTimeTicker)
        }
    }

    if (showNewChatDialog) {
        PureNewChatBottomSheet(
            friends = pureFriends,
            onDismiss = { showNewChatDialog = false },
            onFriendClick = { friend ->
                scope.launch {
                    runCatching { chatRepository.createChatForFriend(friend.uid, friend.name) }
                        .onSuccess { chatId ->
                            onChatClick(chatId, friend.uid, friend.name, friend.profilePicUrl)
                            showNewChatDialog = false
                        }
                        .onFailure { error -> Toast.makeText(context, error.message ?: "Could not create chat", Toast.LENGTH_SHORT).show() }
                }
            },
            renderImage = { url, modifier, scale -> AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale) }
        )
    }

    if (showNearbySheet) {
        PureNearbyUsersBottomSheet(
            nearbyUsers = pureNearbyUsers,
            friends = pureFriends,
            pendingRequestEmails = pendingRequestEmails,
            currentUser = currentUser?.toPureUser(),
            limitStatusText = "⚡ Find Friend Active • Discover Peers",
            isLoading = nearbyLoading,
            errorMessage = nearbyError,
            onDismiss = { showNearbySheet = false },
            onSendRequest = { user ->
                scope.launch {
                    try {
                        if (friends.any { it.uid == user.uid || it.email.trim().equals(user.email.trim(), ignoreCase = true) }) {
                            Toast.makeText(context, "Already added", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        requestError = null
                        chatRepository.sendFriendRequest(user.email)
                        pendingRequestEmails = pendingRequestEmails + user.email.trim().lowercase()
                        Toast.makeText(context, "Friend request sent to ${user.name}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        requestError = formatFriendRequestError(e.message)
                        Toast.makeText(context, requestError, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onRefresh = {
                if (nearbyLoading) return@PureNearbyUsersBottomSheet
                val remaining = chatRepository.getNearbyUsersRefreshRemainingMs()
                nearbyRefreshRemainingMs = remaining
                if (remaining > 0L) {
                    nearbyLimitDialogMessage = "Nearby users can be refreshed again in ${formatRemainingDuration(remaining)}."
                    return@PureNearbyUsersBottomSheet
                }
                scope.launch { loadNearbyUsers(forceRefresh = true) }
            },
            refreshButtonText = refreshButtonText,
            refreshStatusText = refreshStatusText,
            isRefreshEnabled = !nearbyLoading,
            onMessageFriend = { user ->
                showNearbySheet = false
                scope.launch {
                    val myUid = currentUser?.uid.orEmpty()
                    val chatId = chatRepository.getChatId(myUid, user.uid)
                    onMessagePrivately(chatId, user.uid, user.name, user.profilePicUrl)
                }
            },
            onOpenUrl = { url ->
                runCatching {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                }
            },
            renderImage = { url, modifier, scale -> AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale) }
        )
    }

    if (nearbyLimitDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { nearbyLimitDialogMessage = null },
            confirmButton = { TextButton(onClick = { nearbyLimitDialogMessage = null }) { Text("OK") } },
            title = { Text("Nearby Refresh Locked") },
            text = { Text(nearbyLimitDialogMessage.orEmpty()) }
        )
    }

    if (!requestError.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { requestError = null },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(requestError ?: ""))
                    Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                    requestError = null
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { requestError = null }) { Text("Close") } },
            title = { Text("Friend Request Error") },
            text = { Text(requestError ?: "") }
        )
    }

    var chatToDelete by remember { mutableStateOf<PureChat?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog && chatToDelete != null) {
        PureDeleteChatDialog(
            chatName = chatToDelete?.friendName ?: "this user",
            onDismiss = { showDeleteDialog = false; chatToDelete = null },
            onConfirm = {
                val target = chatToDelete ?: return@PureDeleteChatDialog
                showDeleteDialog = false
                chatToDelete = null
                scope.launch {
                    try {
                        chatRepository.deleteChat(target.chatId)
                        Toast.makeText(context, "Chat deleted", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    PureChatScreen(
        repository = chatRepository,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        onChatClick = { chat ->
            scope.launch {
                val uid = chatRepository.getCurrentUserUid() ?: return@launch
                val chatId = chatRepository.getChatId(uid, chat.friendUid)
                onChatClick(chatId, chat.friendUid, chat.friendName, chat.friendProfilePicUrl)
            }
        },
        onAddFriendClick = onAddFriendClick,
        onNearbyClick = {
            showNearbySheet = true
            if (nearbyUsers.isEmpty() && !nearbyLoading) scope.launch { loadNearbyUsers(forceRefresh = false) }
        },
        onNewChatClick = { showNewChatDialog = true },
        onChatLongClick = { chat -> chatToDelete = chat; showDeleteDialog = true },
        searchQuery = searchQuery,
        onMessagePrivately = { senderUid, senderName ->
            scope.launch {
                runCatching {
                    if (!chatRepository.isFriend(senderUid)) {
                        Toast.makeText(context, "Add this user as a friend first to message them", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val chatId = chatRepository.createChatForFriend(senderUid, senderName)
                    val profile = chatRepository.lookupUser(senderUid)
                    onMessagePrivately(
                        chatId,
                        senderUid,
                        profile?.name ?: senderName,
                        profile?.profilePicUrl
                    )
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "Could not start private chat",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        formatTime = { timestamp -> com.example.maps123.utils.DateUtils.formatChatTime(timestamp) },
        renderImage = { url, modifier, scale -> AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale) }
    )
}

private fun formatNearbyRefreshLabel(lastRefreshedAt: Long, now: Long): String {
    val diffMs = (now - lastRefreshedAt).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    return when {
        diffMs < 60_000L -> "Updated just now"
        minutes < 60L -> "Updated ${minutes}m ago"
        hours < 24L -> "Updated ${hours}h ago"
        else -> "Updated ${(diffMs / 86_400_000L)}d ago"
    }
}

private fun formatRemainingDuration(remainingMs: Long): String {
    val totalMinutes = (remainingMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}
