package com.example.maps123.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.maps123.data.repository.AnnouncementRepository
import com.example.maps123.data.repository.ChatRepository
import com.example.maps123.utils.DateUtils
import com.example.maps123.utils.PostLinks
import com.example.shared.model.Announcement
import com.example.shared.ui.*
import com.example.maps123.data.repository.AuthRepository
import com.example.maps123.ui.components.AppAsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementDetailScreen(
    announcementId: String,
    repo: AnnouncementRepository,
    chatRepository: ChatRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var post by remember(announcementId) { mutableStateOf<Announcement?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember(announcementId) { mutableStateOf(true) }

    val currentUid = AuthRepository.currentUserId()

    var showReportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(announcementId) {
        loading = true
        errorMessage = null

        val loadedPost = runCatching { repo.getAnnouncement(announcementId) }
            .onFailure {
                errorMessage = it.message ?: "Failed to open post"
            }
            .getOrNull()

        if (loadedPost == null) {
            post = null
            loading = false
            if (errorMessage == null) {
                errorMessage = "Post not found or unavailable"
            }
            return@LaunchedEffect
        }

        post = loadedPost
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
                Toast.makeText(context, "Private chat added in Chats", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Failed to open chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    PureAnnouncementDetailScreen(
        announcement = post,
        isLoading = loading && post == null,
        errorMessage = errorMessage,
        currentUid = currentUid,
        onBack = onBack,
        onShareClick = {
            val shareText = PostLinks.buildShareMessage(post?.title, announcementId)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share post"))
        },
        onCopyIdClick = {
            post?.id?.let { id ->
                clipboard.setText(AnnotatedString(id))
                Toast.makeText(context, "Post ID copied", Toast.LENGTH_SHORT).show()
            }
        },
        onContactAuthor = { announcement -> contactAuthorPrivately(announcement) },
        onReport = { showReportDialog = true },
        onDelete = { showDeleteConfirm = true },
        formatTime = { timestamp -> DateUtils.getRelativeTime(timestamp) },
        renderImage = { url, modifier, scale ->
            AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
        }
    )

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
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteAnnouncement(post!!.id)
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            title = { Text("Delete post?") },
            text = { Text("This cannot be undone.") }
        )
    }
}
