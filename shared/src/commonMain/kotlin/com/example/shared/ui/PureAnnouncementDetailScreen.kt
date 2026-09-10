package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.shared.model.Announcement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureAnnouncementDetailScreen(
    announcement: Announcement?,
    isLoading: Boolean,
    errorMessage: String?,
    currentUid: String?,
    onBack: () -> Unit,
    onShareClick: () -> Unit,
    onCopyIdClick: () -> Unit,
    onContactAuthor: (Announcement) -> Unit,
    onReport: (Announcement) -> Unit,
    onDelete: (Announcement) -> Unit,
    onLike: (() -> Unit)? = null,
    onComment: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    isLikedByCurrentUser: Boolean = false,
    isSaved: Boolean = false,
    likeCount: Int = announcement?.likes?.size ?: 0,
    commentCount: Int = announcement?.comments?.size ?: 0,
    formatTime: (Long) -> String,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (announcement != null) {
                        IconButton(onClick = onShareClick) {
                            Icon(Icons.Default.Share, "Share")
                        }
                        IconButton(onClick = onCopyIdClick) {
                            Icon(Icons.Default.ContentCopy, "Copy ID")
                        }
                    }
                }
            )
        }
    ) { pad ->
        when {
            isLoading && announcement == null -> Box(
                Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            errorMessage != null -> Box(
                Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Text("Error: $errorMessage")
            }

            announcement == null -> Box(
                Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Text("Post not found")
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PureAnnouncementCard(
                    announcement = announcement,
                    currentUid = currentUid,
                    onReport = { onReport(announcement) },
                    onShare = onShareClick,
                    onDelete = { onDelete(announcement) },
                    onLike = onLike,
                    onComment = onComment,
                    onSave = onSave,
                    isLikedByCurrentUser = isLikedByCurrentUser,
                    isSaved = isSaved,
                    likeCount = likeCount,
                    commentCount = commentCount,
                    onContactAuthor = { onContactAuthor(announcement) },
                    formatTime = formatTime,
                    renderImage = renderImage
                )
                TeamEventSection(announcement)
            }
        }
    }
}
