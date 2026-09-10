package com.example.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class PureAppScreen { MAP, CHAT, ANNOUNCEMENTS, GROUPS, FRIENDS, SETTINGS, PROFILE, FRIEND_PROFILE, ANNOUNCEMENT_DETAIL }

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
            if (showBars) Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PureDockItem("Map", Icons.Default.Place, currentScreen == PureAppScreen.MAP) { onScreenSelected(PureAppScreen.MAP) }
                            PureDockItem("Chat", Icons.AutoMirrored.Filled.Chat, currentScreen == PureAppScreen.CHAT, unreadMessageCount) { onScreenSelected(PureAppScreen.CHAT) }
                            PureDockItem("News", Icons.Default.Notifications, currentScreen == PureAppScreen.ANNOUNCEMENTS, newAnnouncementCount) { onScreenSelected(PureAppScreen.ANNOUNCEMENTS) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        content(padding)
    }
}

@Composable
fun PureDockItem(label: String, icon: ImageVector, selected: Boolean, badgeCount: Int = 0, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column(
        modifier = Modifier.width(80.dp).clickable(onClick = onClick).padding(vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = container, modifier = Modifier.size(width = 56.dp, height = 34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                BadgedBox(badge = { if (badgeCount > 0) Badge { Text(if (badgeCount > 99) "99+" else "$badgeCount") } }) {
                    Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
