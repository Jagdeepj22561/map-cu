package com.example.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class PureAppScreen { MAP, CHAT, ANNOUNCEMENTS, EVENTS, GROUPS, FRIENDS, SETTINGS, PROFILE, FRIEND_PROFILE, ANNOUNCEMENT_DETAIL, SEARCH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureHomeScreen(
    currentScreen: PureAppScreen,
    onScreenSelected: (PureAppScreen) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    unreadMessageCount: Int,
    newAnnouncementCount: Int,
    onSettingsClick: () -> Unit,
    hideBars: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val primaryScreens = setOf(PureAppScreen.MAP, PureAppScreen.CHAT, PureAppScreen.ANNOUNCEMENTS)
    val showBars = !hideBars && currentScreen in primaryScreens

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showBars) Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                TopAppBar(
                    title = {
                        if (isSearching) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search campus, people, chats…") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.extraLarge
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("CU Connect", fontWeight = FontWeight.Bold)
                                Text(
                                    when (currentScreen) {
                                        PureAppScreen.MAP -> "Campus map"
                                        PureAppScreen.CHAT -> "Your conversations"
                                        PureAppScreen.ANNOUNCEMENTS -> "Campus community"
                                        PureAppScreen.EVENTS -> "Discover events & hackathons"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSearching) {
                            IconButton(onClick = { onToggleSearch(false) }) { Icon(Icons.Default.Close, "Close search") }
                        } else {
                            IconButton(onClick = { onToggleSearch(true) }) { Icon(Icons.Default.Search, "Search") }
                            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showBars) Box(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    shadowElevation = 10.dp,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PureDockItem("Map", Icons.Default.Place, currentScreen == PureAppScreen.MAP) { onScreenSelected(PureAppScreen.MAP) }
                        PureDockItem("Chat", Icons.AutoMirrored.Filled.Chat, currentScreen == PureAppScreen.CHAT, unreadMessageCount) { onScreenSelected(PureAppScreen.CHAT) }
                        PureDockItem("News", Icons.Default.Notifications, currentScreen == PureAppScreen.ANNOUNCEMENTS, newAnnouncementCount) { onScreenSelected(PureAppScreen.ANNOUNCEMENTS) }
                        PureDockItem("Search", Icons.Default.Search, currentScreen == PureAppScreen.SEARCH) { onScreenSelected(PureAppScreen.SEARCH) }
                    }
                }
            }
        }
    ) { padding ->
        content(padding)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureDockItem(label: String, icon: ImageVector, selected: Boolean, badgeCount: Int = 0, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    if (selected) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(44.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                BadgedBox(badge = { if (badgeCount > 0) Badge { Text(if (badgeCount > 99) "99+" else "$badgeCount") } }) {
                    Icon(icon, label, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp)
        ) {
            BadgedBox(badge = { if (badgeCount > 0) Badge { Text(if (badgeCount > 99) "99+" else "$badgeCount") } }) {
                Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
    }
}
