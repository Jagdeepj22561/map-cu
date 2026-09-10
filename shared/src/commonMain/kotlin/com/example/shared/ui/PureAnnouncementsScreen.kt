package com.example.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import com.example.shared.model.PureUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureAnnouncementsScreen(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    announcements: List<Announcement>,
    isLoading: Boolean,
    currentUser: PureUser?,
    onAnnouncementClick: (String) -> Unit,
    onCreatePostClick: () -> Unit,
    onContactAuthor: (Announcement) -> Unit,
    onReport: (Announcement) -> Unit,
    onShare: (Announcement) -> Unit,
    onDelete: (Announcement) -> Unit,
    onLike: (Announcement) -> Unit = {},
    onComment: (Announcement) -> Unit = {},
    onSave: (Announcement) -> Unit = {},
    isLikedByCurrentUser: (Announcement) -> Boolean = { false },
    isSaved: (Announcement) -> Boolean = { false },
    likeCount: (Announcement) -> Int = { it.likes.size },
    commentCount: (Announcement) -> Int = { it.comments.size },
    formatTime: (Long) -> String,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit,
    currentUid: String?
) {
    val currentType = AnnouncementType.values().getOrElse(selectedTab) { AnnouncementType.NEWS }

    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)

    val tabContainerColor = when (currentType) {
        AnnouncementType.NEWS -> MaterialTheme.colorScheme.surface
        AnnouncementType.EVENT -> MaterialTheme.colorScheme.primaryContainer
        AnnouncementType.LOST_AND_FOUND -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val listState = rememberLazyListState()

    Scaffold(
        containerColor = backgroundColor,
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            Box(
                modifier = Modifier.padding(bottom = 112.dp)
            ) {
                FloatingActionButton(
                    onClick = onCreatePostClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, "Create Post")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        val promptText = when (currentType) {
            AnnouncementType.NEWS -> "What's happening on campus?"
            AnnouncementType.LOST_AND_FOUND -> "Report a lost or found item"
            AnnouncementType.EVENT -> "Share an event with students"
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundColor)
        ) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("What's on your mind or searching for?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = tabContainerColor,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                listOf("For You", "Lost & Found", "Events").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 136.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            onClick = onCreatePostClick,
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(currentUser?.name?.firstOrNull()?.toString()?.uppercase() ?: "+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Create a post", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Text(promptText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QuickPostAction("Post", Icons.Default.Campaign, onCreatePostClick)
                                    QuickPostAction("Event", Icons.Default.Event, onCreatePostClick)
                                    QuickPostAction("Lost & Found", Icons.Default.VolunteerActivism, onCreatePostClick)
                                }
                            }
                        }
                    }

                    items(announcements) { announcement ->
                        PureAnnouncementCard(
                            announcement = announcement,
                            currentUid = currentUid,
                            onReport = { onReport(announcement) },
                            onShare = { onShare(announcement) },
                            onDelete = { onDelete(announcement) },
                            onLike = { onLike(announcement) },
                            onComment = { onComment(announcement) },
                            onSave = { onSave(announcement) },
                            isLikedByCurrentUser = isLikedByCurrentUser(announcement),
                            isSaved = isSaved(announcement),
                            likeCount = likeCount(announcement),
                            commentCount = commentCount(announcement),
                            onContactAuthor = { onContactAuthor(announcement) },
                            onClick = { onAnnouncementClick(announcement.id) },
                            formatTime = formatTime,
                            renderImage = renderImage
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPostAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(15.dp)) },
        border = null,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    )
}
