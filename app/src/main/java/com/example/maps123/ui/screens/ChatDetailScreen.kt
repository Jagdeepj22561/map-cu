package com.example.maps123.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.maps123.ui.components.AppAsyncImage
import com.example.maps123.data.repository.ChatRepository
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
    val clipboardManager = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val url = repo.uploadImage(uri)
                    repo.sendMessage(
                        chatId = chatId,
                        content = "",
                        friendUid = targetUid,
                        imageUrl = url
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Upload failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    LaunchedEffect(chatId) {
        runCatching {
            repo.setActiveChat(chatId)
            repo.fetchNewMessages(chatId)
        }.onFailure {
            Toast.makeText(context, it.message ?: "Failed to load chat", Toast.LENGTH_SHORT).show()
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
                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        },
        onPickImage = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onDeleteMessages = { ids, isForEveryone ->
            scope.launch {
                try {
                    if (isForEveryone) {
                        repo.deleteMessagesForEveryone(chatId, ids.toList())
                    } else {
                        repo.deleteMessagesLocally(ids.toList())
                    }
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
        onEditMessage = { messageId, newContent ->
            scope.launch {
                try {
                    repo.editMessage(chatId, messageId, newContent)
                    Toast.makeText(context, "Message updated", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                }
            }
        },
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
        }
    )
 }
