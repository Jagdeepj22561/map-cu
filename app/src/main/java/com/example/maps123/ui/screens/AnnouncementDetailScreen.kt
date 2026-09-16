package com.example.maps123.ui.screens

import com.example.shared.ui.PureAlertDialog as AlertDialog

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.maps123.data.repository.AnnouncementRepository
import com.example.maps123.data.repository.AuthRepository
import com.example.maps123.data.repository.ChatRepository
import com.example.maps123.ui.components.AppAsyncImage
import com.example.maps123.utils.DateUtils
import com.example.maps123.utils.PostLinks
import com.example.maps123.utils.toPureUser
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import com.example.shared.model.Comment
import com.example.shared.ui.PureAnnouncementDetailScreen
import com.example.shared.ui.PureReportDialog
import com.example.shared.ui.PureSharePostDialog
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AnnouncementDetailScreen(
    announcementId: String,
    repo: AnnouncementRepository,
    chatRepository: ChatRepository,
    initialTab: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var post by remember(announcementId) { mutableStateOf<Announcement?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember(announcementId) { mutableStateOf(true) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var showShareDialog by remember { mutableStateOf(false) }
    var saved by remember(announcementId) { mutableStateOf(false) }

    val currentUidState = produceState<String?>(initialValue = AuthRepository.currentUserId()) {
        value = AuthRepository.awaitCurrentUserId()
    }
    val currentUid = currentUidState.value
    val observedPost by remember(repo, announcementId) {
        repo.getAnnouncementFlow(announcementId)
    }.collectAsState(initial = null)

    // Keep this screen connected to Room. Remote polling and optimistic
    // mutations both update the same cached row, so likes/comments cannot get
    // stuck in the snapshot that happened to exist when the screen opened.
    LaunchedEffect(observedPost) {
        observedPost?.let {
            post = it
            loading = false
            errorMessage = null
        }
    }

    LaunchedEffect(announcementId) {
        loading = true
        errorMessage = null
        val loadedPost = runCatching { repo.refreshAnnouncement(announcementId) }
            .onFailure { errorMessage = it.message ?: "Failed to open post" }
            .getOrNull()
        if (loadedPost == null) {
            post = null
            loading = false
            if (errorMessage == null) errorMessage = "Post not found or unavailable"
            return@LaunchedEffect
        }
        post = loadedPost
        saved = context.getSharedPreferences("saved_announcements", 0).getBoolean(announcementId, false)
        loading = false
    }

    fun contactAuthorPrivately(announcement: Announcement) {
        val authorUid = announcement.authorUid
        if (authorUid.isNullOrBlank()) {
            Toast.makeText(context, "Author details unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        if (authorUid == currentUid) {
            Toast.makeText(context, "This is your post", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            try {
                chatRepository.createChatForFriend(authorUid, announcement.author.ifBlank { "User" })
                Toast.makeText(context, "Chat opened — one message is allowed until accepted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Failed to open chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var friends by remember { mutableStateOf<List<com.example.shared.model.PureUser>>(emptyList()) }
    LaunchedEffect(Unit) {
        friends = runCatching { chatRepository.getFriendsImplementation() }
            .getOrDefault(emptyList())
            .map { it.toPureUser() }
    }

    if (post != null && post!!.type == AnnouncementType.EVENT) {
        com.example.shared.ui.PureEventDetailScreen(
            announcement = post!!,
            currentUid = currentUid,
            friends = friends,
            initialTab = initialTab,
            onBack = onBack,
            onShareClick = { showShareDialog = true },
            onCopyIdClick = {
                post?.id?.let { id ->
                    clipboard.setText(AnnotatedString(id))
                    Toast.makeText(context, "Post ID copied", Toast.LENGTH_SHORT).show()
                }
            },
            onContactOrganizer = { post?.let { contactAuthorPrivately(it) } },
            onOpenChatWithUser = { friendUid, friendName ->
                scope.launch {
                    runCatching {
                        chatRepository.createChatForFriend(friendUid, friendName)
                        Toast.makeText(context, "Private chat opened with $friendName", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            formatTime = { timestamp -> DateUtils.getRelativeTime(timestamp) },
            renderImage = { url, modifier, scale ->
                AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
            }
        )
    } else {
        PureAnnouncementDetailScreen(
            announcement = post,
            isLoading = loading && post == null,
            errorMessage = errorMessage,
            currentUid = currentUid,
            onBack = onBack,
            onShareClick = { showShareDialog = true },
            onCopyIdClick = {
                post?.id?.let { id ->
                    clipboard.setText(AnnotatedString(id))
                    Toast.makeText(context, "Post ID copied", Toast.LENGTH_SHORT).show()
                }
            },
            onContactAuthor = { announcement -> contactAuthorPrivately(announcement) },
            onReport = { showReportDialog = true },
            onDelete = { showDeleteConfirm = true },
            onLike = {
                val current = post ?: return@PureAnnouncementDetailScreen
                val uid = currentUid ?: run {
                    Toast.makeText(context, "Login required", Toast.LENGTH_SHORT).show()
                    return@PureAnnouncementDetailScreen
                }
                val wasLiked = current.likes.containsKey(uid)
                val shouldLike = !wasLiked
                post = current.copy(
                    likes = current.likes.toMutableMap().apply {
                        if (shouldLike) put(uid, true) else remove(uid)
                    }
                )
                scope.launch {
                    runCatching { repo.setLike(current.id, shouldLike) }
                        .onFailure {
                            post = current
                            Toast.makeText(context, it.message ?: "Could not update like", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onComment = { showCommentDialog = true },
            onSave = {
                val id = post?.id ?: return@PureAnnouncementDetailScreen
                saved = !saved
                context.getSharedPreferences("saved_announcements", 0)
                    .edit()
                    .putBoolean(id, saved)
                    .apply()
            },
            isLikedByCurrentUser = post?.likes?.containsKey(currentUid) == true,
            isSaved = saved,
            likeCount = post?.likes?.size ?: 0,
            commentCount = post?.comments?.size ?: 0,
            formatTime = { timestamp -> DateUtils.getRelativeTime(timestamp) },
            renderImage = { url, modifier, scale ->
                AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
            }
        )
    }

    if (showCommentDialog && post != null) {
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("Comments") },
            text = {
                Column {
                    if (post!!.comments.isEmpty()) {
                        Text("No comments yet. Be the first to comment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(post!!.comments.values.toList()) { comment ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text(comment.userName.ifBlank { "Member" }, style = MaterialTheme.typography.labelLarge)
                                    Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Add a comment") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = commentText.isNotBlank() && currentUid != null,
                    onClick = {
                        val current = post ?: return@Button
                        val uid = currentUid ?: return@Button
                        val text = commentText.trim()
                        val comment = Comment(
                            id = UUID.randomUUID().toString(),
                            userId = uid,
                            userName = AuthRepository.currentUserEmail()?.substringBefore("@").orEmpty().ifBlank { "You" },
                            userProfilePic = null,
                            text = text,
                            timestamp = System.currentTimeMillis()
                        )
                        scope.launch {
                            runCatching { repo.addComment(current.id, text, comment) }
                                .onSuccess {
                                    post = current.copy(comments = current.comments + (comment.id to comment))
                                    commentText = ""
                                    Toast.makeText(context, "Comment added", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, it.message ?: "Could not add comment", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                ) { Text("Comment") }
            },
            dismissButton = {
                TextButton(onClick = { showCommentDialog = false }) { Text("Close") }
            }
        )
    }

    if (showShareDialog && post != null) {
        val shareText = PostLinks.buildShareMessage(post!!.title, announcementId)
        PureSharePostDialog(
            link = shareText,
            onDismiss = { showShareDialog = false },
            onCopy = {
                clipboard.setText(AnnotatedString(shareText))
                Toast.makeText(context, "Share link copied", Toast.LENGTH_SHORT).show()
            },
            onShareViaApp = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }, "Share post"))
                showShareDialog = false
            }
        )
    }

    if (showReportDialog && post != null && currentUid != null) {
        PureReportDialog(
            onDismiss = { showReportDialog = false },
            onReport = { reason ->
                scope.launch {
                    try {
                        repo.reportAnnouncement(post!!.id, currentUid, reason)
                        Toast.makeText(context, "Report sent", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    showReportDialog = false
                }
            }
        )
    }

    if (showDeleteConfirm && post != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            repo.deleteAnnouncement(post!!.id)
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            title = { Text("Delete post?") },
            text = { Text("This cannot be undone.") }
        )
    }
}
