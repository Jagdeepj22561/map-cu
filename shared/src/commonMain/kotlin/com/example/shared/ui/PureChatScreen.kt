package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.PureChat
import com.example.shared.repository.IChatRepository

@Composable
fun PureChatScreen(
    repository: IChatRepository,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    onChatClick: (PureChat) -> Unit,
    onAddFriendClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onChatLongClick: (PureChat) -> Unit,
    searchQuery: String = "",
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    val chats by repository.allChats.collectAsState(emptyList())
    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats else chats.filter { it.friendName.contains(searchQuery, true) || it.lastMessage.contains(searchQuery, true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                label = { Text("Messages") },
                leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                label = { Text("Groups") },
                leadingIcon = { Icon(Icons.Default.Groups, null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (selectedTab == 1) {
            PureGroupsScreen()
            return@Column
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Messages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Stay connected with your campus", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNewChatClick) { Icon(Icons.Default.Edit, "New chat") }
                }
                if (filteredChats.isEmpty()) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Surface(Modifier.size(72.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("No conversations yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Start a conversation with a campus friend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onNearbyClick) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("Show Nearby Friends") }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(filteredChats, key = { it.chatId }) { chat ->
                            PureChatItem(chat = chat, onClick = { onChatClick(chat) }, onLongClick = { onChatLongClick(chat) }, renderImage = renderImage)
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 18.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(onClick = onNearbyClick) { Icon(Icons.Default.LocationOn, "Show Nearby Friends") }
                FloatingActionButton(onClick = onNewChatClick) { Icon(Icons.Default.Edit, "New chat") }
            }
        }
    }
}
