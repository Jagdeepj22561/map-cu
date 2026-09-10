package com.example.shared.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.PureChat
import com.example.shared.model.PureUser

@Composable
fun PureDeleteChatDialog(
    chatName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete chat") },
        text = { Text("Are you sure you want to delete the chat with $chatName?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PureChatItem(
    chat: PureChat,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            renderImage(
                chat.friendProfilePicUrl,
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
                ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.friendName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    PureStudentBadge()
                }

                if (chat.lastMessageTime > 0) {
                    Text(
                        text = "Just now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.size(2.dp))

            Text(
                text = chat.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = if (chat.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (chat.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        if (chat.unreadCount > 0) {
            Spacer(modifier = Modifier.size(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${chat.unreadCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PureNewChatBottomSheet(
    title: String = "New Chat",
    friends: List<PureUser>,
    onDismiss: () -> Unit,
    onFriendClick: (PureUser) -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )

            if (friends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No friends found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(friends) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFriendClick(friend) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            renderImage(
                                friend.profilePicUrl,
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                ContentScale.Crop
                            )
                            Spacer(Modifier.size(16.dp))
                            Text(
                                text = friend.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PureNearbyUsersBottomSheet(
    nearbyUsers: List<PureUser>,
    friends: List<PureUser> = emptyList(),
    pendingRequestEmails: Set<String> = emptySet(),
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSendRequest: (PureUser) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshButtonText: String = "Refresh",
    refreshStatusText: String? = null,
    isRefreshEnabled: Boolean = true,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    val friendIds = remember(friends) { friends.map { it.uid }.toSet() }
    val friendEmails = remember(friends) { friends.map { it.email.trim().lowercase() }.toSet() }
    val normalizedPendingEmails = remember(pendingRequestEmails) {
        pendingRequestEmails.map { it.trim().lowercase() }.toSet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nearby Friends",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (!refreshStatusText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.size(2.dp))
                        Text(
                            text = refreshStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onRefresh != null) {
                    TextButton(
                        onClick = onRefresh,
                        enabled = isRefreshEnabled && !isLoading
                    ) {
                        Text(refreshButtonText)
                    }
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                nearbyUsers.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No users found nearby", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(nearbyUsers) { user ->
                            val userEmail = user.email.trim().lowercase()
                            val isAlreadyFriend = user.uid in friendIds || userEmail in friendEmails
                            val isRequestSent = !isAlreadyFriend && userEmail in normalizedPendingEmails
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                renderImage(
                                    user.profilePicUrl,
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape),
                                    ContentScale.Crop
                                )
                                Spacer(Modifier.size(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        user.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onSendRequest(user) },
                                    enabled = !isAlreadyFriend && !isRequestSent,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        when {
                                            isAlreadyFriend -> "Already Added"
                                            isRequestSent -> "Request Sent"
                                            else -> "Add"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
