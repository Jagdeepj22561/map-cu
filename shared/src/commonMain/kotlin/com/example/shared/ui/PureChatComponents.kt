package com.example.shared.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    PureAlertDialog(
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
    formatTime: ((Long) -> String)? = null,
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
                    val timeText = remember(chat.lastMessageTime, formatTime) {
                        formatTime?.invoke(chat.lastMessageTime) ?: ""
                    }
                    if (timeText.isNotBlank()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

enum class PureNearbyFilter(val label: String) {
    ALL("All"),
    SAME_BRANCH("🔥 Same Branch"),
    FRESHMEN("🎓 Freshmen")
}

@Composable
fun PureStudentProfileDialog(
    user: PureUser,
    currentUser: PureUser? = null,
    isAlreadyFriend: Boolean,
    isRequestSent: Boolean,
    onDismiss: () -> Unit,
    onSendRequest: (PureUser) -> Unit,
    onMessageFriend: ((PureUser) -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    val isSameBranch = remember(user.course, currentUser?.course) {
        val uCourse = user.course.trim().lowercase()
        val mCourse = currentUser?.course?.trim()?.lowercase() ?: ""
        uCourse.isNotBlank() && mCourse.isNotBlank() && (uCourse == mCourse || uCourse.contains(mCourse) || mCourse.contains(uCourse))
    }
    val isBatchmate = remember(user.year, currentUser?.year) {
        val uYear = user.year.trim().lowercase()
        val mYear = currentUser?.year?.trim()?.lowercase() ?: ""
        uYear.isNotBlank() && mYear.isNotBlank() && uYear == mYear
    }
    val isSameCollege = remember(user.university, currentUser?.university) {
        val uCol = user.university.trim().lowercase()
        val mCol = currentUser?.university?.trim()?.lowercase() ?: ""
        uCol.isNotBlank() && mCol.isNotBlank() && (uCol == mCol || uCol.contains(mCol) || mCol.contains(uCol))
    }

    PureAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (isAlreadyFriend) {
                if (onMessageFriend != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onMessageFriend(user)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Message")
                    }
                } else {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            } else {
                Button(
                    onClick = { onSendRequest(user) },
                    enabled = !isRequestSent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isRequestSent) Icons.Default.Check else Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRequestSent) "Request Sent" else "Connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Text(
                text = "Student Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Avatar with ring
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                ) {
                    renderImage(user.profilePicUrl, Modifier.matchParentSize(), ContentScale.Crop)
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = user.name.ifBlank { "Campus Student" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (user.email.isNotBlank()) {
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Distance pill
                val distanceM = user.distanceMeters
                val distanceText = when {
                    distanceM != null && distanceM < 250 -> "📍 ${distanceM.toInt()}m away • Very Close"
                    distanceM != null && distanceM < 1000 -> "📍 ${distanceM.toInt()}m away • Walking Distance"
                    distanceM != null -> "📍 ${(distanceM / 1000.0).toString().take(4)} km away • Campus"
                    else -> "🎓 Enrolled Campus Peer"
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        distanceM != null && distanceM < 500 -> MaterialTheme.colorScheme.primaryContainer
                        distanceM != null -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = distanceText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            distanceM != null && distanceM < 500 -> MaterialTheme.colorScheme.onPrimaryContainer
                            distanceM != null -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Match Highlights (if any match)
                if (isSameBranch || isBatchmate || isSameCollege) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "✨ Match Highlights",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            if (isSameBranch) {
                                Text(
                                    text = "🔥 Same Branch: ${user.course}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            if (isBatchmate) {
                                Text(
                                    text = "🎓 Batchmate: ${user.year}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            if (isSameCollege) {
                                Text(
                                    text = "🏛️ Same University: ${user.university}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Academic details card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ACADEMICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        if (user.course.isNotBlank()) {
                            Text(
                                text = "📚 Course: ${user.course}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        val yearSem = listOfNotNull(
                            user.year.takeIf { it.isNotBlank() }?.let { "Year: $it" },
                            user.semester.takeIf { it.isNotBlank() }?.let { "Semester: $it" }
                        ).joinToString(" • ")
                        if (yearSem.isNotBlank()) {
                            Text(
                                text = "🎓 $yearSem",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (user.university.isNotBlank()) {
                            Text(
                                text = "🏛️ University: ${user.university}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Socials (if available)
                val hasSocials = user.instagramLink.isNotBlank() || user.snapchatLink.isNotBlank() || user.linkedinLink.isNotBlank()
                if (hasSocials) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "SOCIALS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (user.instagramLink.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.clickable {
                                            val raw = user.instagramLink.trim()
                                            val link = if (raw.startsWith("http")) raw else "https://instagram.com/${raw.removePrefix("@")}"
                                            onOpenUrl?.invoke(link)
                                        }
                                    ) {
                                        Text(
                                            text = "📸 IG: ${user.instagramLink.removePrefix("@").take(12)}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                if (user.snapchatLink.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.clickable {
                                            val raw = user.snapchatLink.trim()
                                            val link = if (raw.startsWith("http")) raw else "https://snapchat.com/add/$raw"
                                            onOpenUrl?.invoke(link)
                                        }
                                    ) {
                                        Text(
                                            text = "👻 Snap: ${user.snapchatLink.take(12)}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                if (user.linkedinLink.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.clickable {
                                            val raw = user.linkedinLink.trim()
                                            val link = if (raw.startsWith("http")) raw else "https://linkedin.com/in/$raw"
                                            onOpenUrl?.invoke(link)
                                        }
                                    ) {
                                        Text(
                                            text = "💼 LinkedIn",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PureNearbyUsersBottomSheet(
    nearbyUsers: List<PureUser>,
    friends: List<PureUser> = emptyList(),
    pendingRequestEmails: Set<String> = emptySet(),
    currentUser: PureUser? = null,
    limitStatusText: String? = null,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSendRequest: (PureUser) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshButtonText: String = "Refresh",
    refreshStatusText: String? = null,
    isRefreshEnabled: Boolean = true,
    onMessageFriend: ((PureUser) -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    val friendIds = remember(friends) { friends.map { it.uid }.toSet() }
    val friendEmails = remember(friends) { friends.map { it.email.trim().lowercase() }.toSet() }
    val normalizedPendingEmails = remember(pendingRequestEmails) {
        pendingRequestEmails.map { it.trim().lowercase() }.toSet()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PureNearbyFilter.ALL) }
    var previewUser by remember { mutableStateOf<PureUser?>(null) }

    val filteredUsers = remember(nearbyUsers, searchQuery, selectedFilter, currentUser) {
        nearbyUsers.filter { user ->
            val q = searchQuery.trim().lowercase()
            val matchesQuery = if (q.isBlank()) true else {
                user.name.lowercase().contains(q) ||
                user.email.lowercase().contains(q) ||
                user.course.lowercase().contains(q) ||
                user.university.lowercase().contains(q) ||
                user.year.lowercase().contains(q)
            }
            if (!matchesQuery) return@filter false

            when (selectedFilter) {
                PureNearbyFilter.ALL -> true
                PureNearbyFilter.SAME_BRANCH -> {
                    val mCourse = currentUser?.course?.trim()?.lowercase() ?: ""
                    val uCourse = user.course.trim().lowercase()
                    mCourse.isNotBlank() && uCourse.isNotBlank() && (uCourse == mCourse || uCourse.contains(mCourse) || mCourse.contains(uCourse))
                }
                PureNearbyFilter.FRESHMEN -> {
                    val y = user.year.trim().lowercase()
                    y.contains("1") || y.contains("fresh") || y == "first"
                }
            }
        }
    }

    if (previewUser != null) {
        val target = previewUser!!
        val userEmail = target.email.trim().lowercase()
        val isAlreadyFriend = target.uid in friendIds || userEmail in friendEmails
        val isRequestSent = !isAlreadyFriend && userEmail in normalizedPendingEmails

        PureStudentProfileDialog(
            user = target,
            currentUser = currentUser,
            isAlreadyFriend = isAlreadyFriend,
            isRequestSent = isRequestSent,
            onDismiss = { previewUser = null },
            onSendRequest = { onSendRequest(it) },
            onMessageFriend = onMessageFriend,
            onOpenUrl = onOpenUrl,
            renderImage = renderImage
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header: Radar Title & Limit Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Find Friend",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Find Friend",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = refreshStatusText ?: limitStatusText ?: "Discover freshmen & peers near you",
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

            // Limit / Safety Quota Indicator
            val activeLimitText = limitStatusText ?: "⚡ Campus Discovery Active"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activeLimitText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "${filteredUsers.size} found",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search by name, branch, year...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PureNearbyFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                filteredUsers.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedFilter != PureNearbyFilter.ALL) {
                                    "No students matched your filter"
                                } else {
                                    "No students found nearby on campus yet"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredUsers, key = { it.uid.ifBlank { it.email } }) { user ->
                            val userEmail = user.email.trim().lowercase()
                            val isAlreadyFriend = user.uid in friendIds || userEmail in friendEmails
                            val isRequestSent = !isAlreadyFriend && userEmail in normalizedPendingEmails

                            // Compute match highlights
                            val isSameBranch = remember(user.course, currentUser?.course) {
                                val uCourse = user.course.trim().lowercase()
                                val mCourse = currentUser?.course?.trim()?.lowercase() ?: ""
                                uCourse.isNotBlank() && mCourse.isNotBlank() && (uCourse == mCourse || uCourse.contains(mCourse) || mCourse.contains(uCourse))
                            }
                            val isBatchmate = remember(user.year, currentUser?.year) {
                                val uYear = user.year.trim().lowercase()
                                val mYear = currentUser?.year?.trim()?.lowercase() ?: ""
                                uYear.isNotBlank() && mYear.isNotBlank() && uYear == mYear
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 5.dp)
                                    .clickable { previewUser = user },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                    ) {
                                        renderImage(
                                            user.profilePicUrl,
                                            Modifier.matchParentSize(),
                                            ContentScale.Crop
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    // Details Column
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.name.ifBlank { "Student" },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )

                                        // Academic info
                                        val academicText = listOfNotNull(
                                            user.course.takeIf { it.isNotBlank() },
                                            user.year.takeIf { it.isNotBlank() }?.let { "Yr $it" }
                                        ).joinToString(" • ")
                                        if (academicText.isNotBlank()) {
                                            Text(
                                                text = academicText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(Modifier.height(4.dp))

                                        // Distance and Match Badges
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Distance Badge
                                            val d = user.distanceMeters
                                            val distLabel = when {
                                                d != null && d < 250 -> "${d.toInt()}m away"
                                                d != null && d < 1000 -> "${d.toInt()}m"
                                                d != null -> "${(d / 1000.0).toString().take(3)}km"
                                                else -> "Campus"
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when {
                                                    d != null && d < 500 -> MaterialTheme.colorScheme.primaryContainer
                                                    d != null -> MaterialTheme.colorScheme.secondaryContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ) {
                                                Text(
                                                    text = "📍 $distLabel",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            if (isSameBranch) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                                ) {
                                                    Text(
                                                        text = "🔥 Branch",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }

                                            if (isBatchmate) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "🎓 Batchmate",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    // Action Button
                                    Button(
                                        onClick = { onSendRequest(user) },
                                        enabled = !isAlreadyFriend && !isRequestSent,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            when {
                                                isAlreadyFriend -> "Friends"
                                                isRequestSent -> "Sent"
                                                else -> "Add"
                                            },
                                            style = MaterialTheme.typography.labelMedium
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
}
