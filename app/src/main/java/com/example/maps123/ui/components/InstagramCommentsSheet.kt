package com.example.maps123.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.Comment

/** Instagram-style full comment conversation, backed by Supabase / Room. */
@Composable
fun InstagramCommentsSheet(
    comments: List<Comment>,
    currentUid: String? = null,
    currentUserName: String,
    currentUserPhoto: String?,
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    onPost: () -> Unit,
    onReply: ((Comment) -> Unit)? = null,
    onDeleteComment: ((Comment) -> Unit)? = null,
    onReportComment: ((Comment) -> Unit)? = null,
    onCopyComment: ((Comment) -> Unit)? = null,
    replyingToComment: Comment? = null,
    onCancelReply: (() -> Unit)? = null,
    renderAvatar: @Composable (String?, Modifier) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp, max = 680.dp)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Comments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))

        if (comments.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("No comments yet", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Start the conversation.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    val isAuthor = currentUid != null && comment.userId.isNotBlank() && comment.userId == currentUid
                    InstagramCommentRow(
                        comment = comment,
                        isAuthor = isAuthor,
                        onReply = { onReply?.invoke(comment) },
                        onDelete = { onDeleteComment?.invoke(comment) },
                        onReport = { onReportComment?.invoke(comment) },
                        onCopy = { onCopyComment?.invoke(comment) },
                        renderAvatar = renderAvatar
                    )
                }
            }
        }

        // Replying banner
        if (replyingToComment != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Replying to @${replyingToComment.userName.ifBlank { "User" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { onCancelReply?.invoke() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel reply",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            renderAvatar(
                currentUserPhoto,
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f)
            ) {
                TextField(
                    value = commentText,
                    onValueChange = onCommentTextChange,
                    placeholder = {
                        Text(
                            if (replyingToComment != null) "Reply to @${replyingToComment.userName.ifBlank { "User" }}…"
                            else "Add a comment…"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
            if (commentText.trim().isNotEmpty()) {
                IconButton(onClick = onPost) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "Post comment",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun InstagramCommentRow(
    comment: Comment,
    isAuthor: Boolean = false,
    onReply: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReport: () -> Unit = {},
    onCopy: () -> Unit = {},
    renderAvatar: @Composable (String?, Modifier) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        renderAvatar(
            comment.userProfilePic,
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName.ifBlank { "User" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = commentRelativeTime(comment.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Reply",
                modifier = Modifier
                    .padding(top = 5.dp)
                    .clickable { onReply() },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    "Comment options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (isAuthor) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete comment",
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Copy text") },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Report",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "Report comment",
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onReport()
                        }
                    )
                }
            }
        }
    }
}

fun commentRelativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "now"
    val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 60_000)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1_440 -> "${minutes / 60}h"
        else -> "${minutes / 1_440}d"
    }
}
