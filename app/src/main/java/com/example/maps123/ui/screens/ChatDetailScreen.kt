package com.example.maps123.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.maps123.ui.components.AppAsyncImage
import com.example.maps123.data.repository.ChatRepository
import com.example.maps123.data.repository.ChatRequestState
import com.example.maps123.data.repository.AuthRepository
import kotlinx.coroutines.launch
import com.example.maps123.utils.DateUtils
import com.example.maps123.utils.PostLinks

import com.example.shared.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: String?,
    repo: ChatRepository,
    friendUid: String?,
    friendName: String?,
    friendProfilePicUrl: String?,
    onProfileClick: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onBack: () -> Unit
) {
    if (chatId.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Invalid chat")
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val myUid = AuthRepository.currentUserId()
    val targetUid = friendUid
    if (myUid.isNullOrBlank() || targetUid.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Chat is still loading. Please return and try again.")
        }
        return
    }

    val messages by repo.getMessages(chatId).collectAsState(initial = emptyList())
    var isBlocked by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchChatQuery by remember { mutableStateOf("") }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImageCaption by remember { mutableStateOf("") }
    var pendingImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var requestState by remember { mutableStateOf<ChatRequestState?>(null) }
    var requestActionBusy by remember { mutableStateOf(false) }
    var isFriendUser by remember { mutableStateOf(false) }
    var requestStateLoading by remember { mutableStateOf(true) }

    val clipboardManager = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
        }
    }

    LaunchedEffect(chatId) {
        runCatching {
            repo.setActiveChat(chatId)
            repo.fetchNewMessages()
            isFriendUser = repo.isFriend(targetUid)
            requestState = repo.getChatRequestState(targetUid)
        }.onFailure {
            Toast.makeText(context, it.message ?: "Failed to load chat", Toast.LENGTH_SHORT).show()
        }
        requestStateLoading = false
    }

    // Keep the banner synchronized while the chat is open. Acceptance or
    // decline happens on the other device, so waiting for the 30-second global
    // friend sync would leave the composer in the wrong state.
    LaunchedEffect(chatId, targetUid) {
        while (true) {
            runCatching {
                val friend = repo.isFriend(targetUid)
                val state = repo.getChatRequestState(targetUid)
                isFriendUser = friend
                requestState = if (friend) null else state
            }
            kotlinx.coroutines.delay(10_000)
        }
    }

    LaunchedEffect(chatId, messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        runCatching {
            repo.markChatAsRead(chatId)
        }.onFailure {
            Toast.makeText(context, it.message ?: "Failed to update read status", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(chatId) {
        onDispose {
            repo.setActiveChat(null)
        }
    }

    PureChatDetailScreen(
        friendName = friendName,
        friendProfilePicUrl = friendProfilePicUrl,
        messages = messages.map { it.toPureMessage() },
        myUid = myUid,
        onBack = onBack,
        onProfileClick = { onProfileClick(targetUid) },
        onSendMessage = { content ->
            scope.launch {
                try {
                    repo.sendMessage(
                        chatId = chatId,
                        content = content,
                        friendUid = targetUid,
                        imageUrl = null
                    )
                    if (!isFriendUser) requestState = ChatRequestState("pending", incoming = false)
                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        },
        onPickImage = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onDeleteMessages = { ids, _ ->
            scope.launch {
                try {
                    repo.deleteMessagesLocally(ids.toList())
                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onCopyMessages = { ids ->
            val text = messages.filter { it.messageId in ids }.joinToString("\n") { it.content }
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        },
        onEditMessage = { _, _ -> },
        onBlockUser = {
            scope.launch {
                try {
                    if (isBlocked) repo.unblockUser(targetUid) else repo.blockUser(targetUid)
                    isBlocked = !isBlocked
                    Toast.makeText(context, if (isBlocked) "Blocked" else "Unblocked", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onClearChat = {
            scope.launch {
                try {
                    repo.clearChat(chatId)
                    Toast.makeText(context, "Cleared", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onDeleteChat = {
            scope.launch {
                try {
                    repo.deleteChat(chatId)
                    onBack()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onReportUser = {
            Toast.makeText(context, "Reported", Toast.LENGTH_SHORT).show()
        },
        isBlocked = isBlocked,
        searchQuery = searchChatQuery,
        onSearchQueryChange = { searchChatQuery = it },
        isSearchActive = isSearchActive,
        onToggleSearch = { isSearchActive = it },
        formatTime = { timestamp -> DateUtils.formatTime(timestamp) },
        formatDateHeader = { timestamp -> DateUtils.getDateHeader(timestamp) },
        onOpenPostLink = { link ->
            val postId = PostLinks.extractPostId(link)
            if (postId != null) {
                onOpenPost(postId)
            } else {
                Toast.makeText(context, "Invalid post link", Toast.LENGTH_SHORT).show()
            }
        },
        renderImage = { url, modifier, scale ->
            AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
        },
        requestBanner = {
            requestState?.let { state ->
                when {
                    state.incoming && state.status == "pending" -> Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Message request", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "This person wants to start a conversation. Accept to reply, or decline the request.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Row(Modifier.padding(top = 8.dp)) {
                                Button(
                                    enabled = !requestActionBusy,
                                    onClick = {
                                        requestActionBusy = true
                                        scope.launch {
                                            runCatching { repo.acceptFriendRequest(targetUid) }
                                                .onSuccess { isFriendUser = true; requestState = null; Toast.makeText(context, "Request accepted", Toast.LENGTH_SHORT).show() }
                                                .onFailure { Toast.makeText(context, it.message ?: "Unable to accept request", Toast.LENGTH_SHORT).show() }
                                            requestActionBusy = false
                                        }
                                    }
                                ) { Text("Accept") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    enabled = !requestActionBusy,
                                    onClick = {
                                        requestActionBusy = true
                                        scope.launch {
                                            runCatching { repo.rejectFriendRequest(targetUid) }
                                                .onSuccess { requestState = ChatRequestState("rejected", incoming = false); Toast.makeText(context, "Request declined", Toast.LENGTH_SHORT).show() }
                                                .onFailure { Toast.makeText(context, it.message ?: "Unable to decline request", Toast.LENGTH_SHORT).show() }
                                            requestActionBusy = false
                                        }
                                    }
                                ) { Text("Decline") }
                            }
                        }
                    }
                    !state.incoming && state.status == "pending" -> Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Message request sent. You can send one message until it is accepted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    !state.incoming && state.status == "rejected" -> Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Message request declined. You cannot send more messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        canSendMessages = !requestStateLoading && (isFriendUser || requestState == null),
        canEditMessage = { false },
        canDeleteForEveryone = false,
        onImageClick = { imageUrl ->
            fullScreenImageUrl = imageUrl
        }
    )

    if (pendingImageUri != null) {
        val uri = pendingImageUri!!
        val imageUrl = pendingImageUrl

        if (imageUrl == null && !isUploadingImage) {
            isUploadingImage = true
            LaunchedEffect(uri) {
                try {
                    val url = repo.uploadImage(uri)
                    pendingImageUrl = url
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Upload failed", Toast.LENGTH_SHORT).show()
                    pendingImageUri = null
                    pendingImageUrl = null
                }
                isUploadingImage = false
            }
        }

        if (isUploadingImage) {
            ImagePreviewDialog(
                imageUrl = "",
                caption = "",
                onCaptionChange = {},
                onSend = {},
                onDismiss = {
                    pendingImageUri = null
                    pendingImageUrl = null
                },
                renderImage = { _, modifier, scale ->
                    Box(
                        modifier = modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Uploading...")
                    }
                }
            )
        } else if (imageUrl != null) {
            ImagePreviewDialog(
                imageUrl = imageUrl,
                caption = pendingImageCaption,
                onCaptionChange = { pendingImageCaption = it },
                onSend = { caption ->
                    scope.launch {
                        try {
                            repo.sendMessage(
                                chatId = chatId,
                                content = caption,
                                friendUid = targetUid,
                                imageUrl = imageUrl
                            )
                            if (!isFriendUser) requestState = ChatRequestState("pending", incoming = false)
                            pendingImageUri = null
                            pendingImageUrl = null
                            pendingImageCaption = ""
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "Send failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = {
                    pendingImageUri = null
                    pendingImageUrl = null
                    pendingImageCaption = ""
                },
                renderImage = { url, modifier, scale ->
                    AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
                }
            )
        }
    }

    if (fullScreenImageUrl != null) {
        FullScreenImageDialog(
            imageUrl = fullScreenImageUrl!!,
            onDismiss = { fullScreenImageUrl = null },
            renderImage = { url, modifier, scale ->
                AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
            }
        )
    }
}
