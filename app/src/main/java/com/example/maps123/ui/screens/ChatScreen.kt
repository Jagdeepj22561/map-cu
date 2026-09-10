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

private fun formatFriendRequestError(message: String?): String {
    val text = message?.trim().orEmpty()
    return when {
        text.contains("scope left the composition", ignoreCase = true) -> ""
        text.contains("job was cancelled", ignoreCase = true) -> ""
        text.contains("already sent you a friend request", ignoreCase = true) -> text
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
    searchQuery: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var requestError by remember { mutableStateOf<String?>(null) }

    var showNewChatDialog by remember { mutableStateOf(false) }
    var showNearbySheet by remember { mutableStateOf(false) }
    var nearbyUsers by remember { mutableStateOf<List<UserEntity>>(emptyList()) }
    var nearbyLoading by remember { mutableStateOf(false) }
    var nearbyError by remember { mutableStateOf<String?>(null) }
    var nearbyLimitDialogMessage by remember { mutableStateOf<String?>(null) }
    var pendingRequestEmails by remember { mutableStateOf<Set<String>>(emptySet()) }
    var nearbyLastRefreshedAt by remember { mutableStateOf(chatRepository.getNearbyUsersLastRefreshTime()) }
    var nearbyTimeTicker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nearbyRefreshRemainingMs by remember { mutableLongStateOf(chatRepository.getNearbyUsersRefreshRemainingMs()) }

    val pureFriends = remember(friends) { friends.map { it.toPureUser() } }

    suspend fun loadNearbyUsers(forceRefresh: Boolean) {
        nearbyLoading = true
        nearbyError = null
        try {
            nearbyUsers = chatRepository.getNearbyUsersImplementation(forceRefresh = forceRefresh)
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
            nearbyUsers = nearbyUsers.map { it.toPureUser() },
            friends = pureFriends,
            pendingRequestEmails = pendingRequestEmails,
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
                        Toast.makeText(context, "Friend request sent", Toast.LENGTH_SHORT).show()
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
