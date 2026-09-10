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

enum class PureAppScreen { MAP, CHAT, ANNOUNCEMENTS, GROUPS, FRIENDS, SETTINGS, PROFILE, FRIEND_PROFILE, ANNOUNCEMENT_DETAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureHomeScreen(currentScreen: PureAppScreen, onScreenSelected: (PureAppScreen) -> Unit, searchQuery: String, onSearchQueryChange: (String) -> Unit, isSearching: Boolean, onToggleSearch: (Boolean) -> Unit, unreadMessageCount: Int, newAnnouncementCount: Int, onSettingsClick: () -> Unit, hideBars: Boolean = false, content: @Composable (PaddingValues) -> Unit) {
    val showTopBar = !hideBars && currentScreen in listOf(PureAppScreen.MAP, PureAppScreen.CHAT, PureAppScreen.ANNOUNCEMENTS, PureAppScreen.GROUPS, PureAppScreen.FRIENDS)
    val showBottomBar = !hideBars && currentScreen in listOf(PureAppScreen.MAP, PureAppScreen.CHAT, PureAppScreen.ANNOUNCEMENTS, PureAppScreen.GROUPS, PureAppScreen.FRIENDS)
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { if (showTopBar) Surface(shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), tonalElevation = 4.dp, shadowElevation = 4.dp) { TopAppBar(title = { if (isSearching) TextField(searchQuery, onSearchQueryChange, modifier = Modifier.fillMaxWidth().height(56.dp), placeholder = { Text("Search...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(28.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)) else Text("CU Connect", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }, actions = { if (isSearching) IconButton(onClick = { onToggleSearch(false) }) { Icon(Icons.Default.Close, "Close search") } else { IconButton(onClick = { onToggleSearch(true) }) { Icon(Icons.Default.Search, "Search") }; IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") } } }) } }, bottomBar = { if (showBottomBar) Surface(color = if (currentScreen == PureAppScreen.MAP) Color.Transparent else MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) { Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 4.dp, border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { PureDockItem("Map", Icons.Default.Place, currentScreen == PureAppScreen.MAP) { onScreenSelected(PureAppScreen.MAP) }; PureDockItem("Chat", Icons.AutoMirrored.Filled.Chat, currentScreen == PureAppScreen.CHAT, unreadMessageCount) { onScreenSelected(PureAppScreen.CHAT) }; PureDockItem("News", Icons.Default.Notifications, currentScreen == PureAppScreen.ANNOUNCEMENTS, newAnnouncementCount) { onScreenSelected(PureAppScreen.ANNOUNCEMENTS) }; PureDockItem("Groups", Icons.Default.Groups, currentScreen == PureAppScreen.GROUPS) { onScreenSelected(PureAppScreen.GROUPS) }; PureDockItem("Friends", Icons.Default.PersonSearch, currentScreen == PureAppScreen.FRIENDS) { onScreenSelected(PureAppScreen.FRIENDS) } } } } } }) { padding -> if (currentScreen == PureAppScreen.GROUPS) PureGroupsScreen() else if (currentScreen == PureAppScreen.FRIENDS) PureFriendsScreen() else content(padding) }
}

@Composable
fun PureDockItem(label: String, icon: ImageVector, selected: Boolean, badgeCount: Int = 0, onClick: () -> Unit) { val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant; val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent; Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp).clickable(onClick = onClick)) { Surface(shape = RoundedCornerShape(14.dp), color = containerColor, modifier = Modifier.size(width = 56.dp, height = 30.dp)) { Box(contentAlignment = Alignment.Center) { BadgedBox(badge = { if (badgeCount > 0) Badge { Text("$badgeCount") } }) { Icon(icon, contentDescription = label, tint = color) } } }; Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium) } }
