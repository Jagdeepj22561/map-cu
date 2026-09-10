package com.example.shared.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class PureAppScreen { MAP, CHAT, ANNOUNCEMENTS, SETTINGS, PROFILE, FRIEND_PROFILE, ANNOUNCEMENT_DETAIL }

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
    val showTopBar = !hideBars && currentScreen in listOf(PureAppScreen.MAP, PureAppScreen.CHAT, PureAppScreen.ANNOUNCEMENTS)
    val showBottomBar = !hideBars && currentScreen in listOf(PureAppScreen.MAP, PureAppScreen.CHAT, PureAppScreen.ANNOUNCEMENTS)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    TopAppBar(
                        title = {
                            Crossfade(targetState = isSearching) { searching ->
                                if (searching) {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = onSearchQueryChange,
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        placeholder = { Text("Search...") },
                                        leadingIcon = { Icon(Icons.Default.Search, null) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { onSearchQueryChange("") }) {
                                                    Icon(Icons.Default.Close, null)
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(28.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                    )
                                } else {
                                    Text("CU Connect", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        },
                        actions = {
                            if (isSearching) {
                                IconButton(onClick = { onToggleSearch(false) }) {
                                    Icon(Icons.Default.Close, "Close search")
                                }
                            } else {
                                IconButton(onClick = { onToggleSearch(true) }) {
                                    Icon(Icons.Default.Search, "Search")
                                }
                                IconButton(onClick = onSettingsClick) {
                                    Icon(Icons.Default.Settings, "Settings")
                                }
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = if (currentScreen == PureAppScreen.MAP) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 54.dp, end = 54.dp, bottom = 18.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp,
                            shadowElevation = 4.dp,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PureDockItem(
                                    label = "Map",
                                    icon = Icons.Default.Place,
                                    selected = currentScreen == PureAppScreen.MAP,
                                    onClick = { onScreenSelected(PureAppScreen.MAP) }
                                )
                                PureDockItem(
                                    label = "Chat",
                                    icon = Icons.AutoMirrored.Filled.Chat,
                                    selected = currentScreen == PureAppScreen.CHAT,
                                    badgeCount = unreadMessageCount,
                                    onClick = { onScreenSelected(PureAppScreen.CHAT) }
                                )
                                PureDockItem(
                                    label = "News",
                                    icon = Icons.Default.Notifications,
                                    selected = currentScreen == PureAppScreen.ANNOUNCEMENTS,
                                    badgeCount = newAnnouncementCount,
                                    onClick = { onScreenSelected(PureAppScreen.ANNOUNCEMENTS) }
                                )
                            }
                        }
                    }
                }
            }
        },
        content = content
    )
}

@Composable
fun PureDockItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = containerColor,
            modifier = Modifier.size(width = 56.dp, height = 30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                BadgedBox(
                    badge = {
                        if (badgeCount > 0) {
                            Badge { Text("$badgeCount") }
                        }
                    }
                ) {
                    Icon(icon, contentDescription = label, tint = color)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}
